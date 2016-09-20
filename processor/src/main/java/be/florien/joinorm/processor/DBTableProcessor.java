package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import be.florien.joinorm.annotation.JoCustomJoin;
import be.florien.joinorm.annotation.JoId;
import be.florien.joinorm.annotation.JoIgnore;
import be.florien.joinorm.annotation.JoJoin;
import be.florien.joinorm.annotation.JoTable;
import be.florien.joinorm.architecture.DBTable;

@SupportedAnnotationTypes("be.florien.joinorm.annotation.JoTable")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class DBTableProcessor extends AbstractProcessor {

    private static final String PACKAGE_NAME = "be.florien.joinorm.generated";
    private int debugCount = 0;
    private String currentTableName;
    private Element currentTableElement;
    private Element currentIdElement;
    private ClassName currentClassName;
    private JoTable currentTableAnnotation;
    private TypeSpec.Builder classBuilder;
    private MethodSpec.Builder currentJoinBuilder;

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (Element tableElement : roundEnvironment.getElementsAnnotatedWith(JoTable.class)) {
            currentTableElement = tableElement;
            currentTableAnnotation = currentTableElement.getAnnotation(JoTable.class);

            if (tableElement.getKind() != ElementKind.CLASS) {
                continue;
            }

            getIdElement(roundEnvironment);

            currentTableName = tableElement.getSimpleName() + "Table";
            currentClassName = ClassName.get(PACKAGE_NAME, currentTableName);

            initBuilder();
            getConstructor();
            getIdMethods();

            for (Element field : currentTableElement.getEnclosedElements()) {
                getFieldRelated(field);
            }

            classBuilder.addMethod(currentJoinBuilder.addStatement("return \"\"").build());

            try {
                JavaFile.builder(PACKAGE_NAME, classBuilder.build())
                        .build().writeTo(processingEnv.getFiler());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private void initBuilder() {
        ClassName dbTableClassName = ClassName.get(DBTable.class);
        ClassName modelObjectClassName = ClassName.get((TypeElement) currentTableElement);
        ParameterizedTypeName parametrisedDBTableClassName = ParameterizedTypeName.get(dbTableClassName, modelObjectClassName);
        ParameterizedTypeName wildcardDBTableClassName = ParameterizedTypeName.get(dbTableClassName, WildcardTypeName.subtypeOf(TypeName.OBJECT));
        ParameterSpec wildcardDBTableParameter = ParameterSpec.builder(wildcardDBTableClassName, "innerTable").build();

        classBuilder = TypeSpec.classBuilder(currentTableName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(parametrisedDBTableClassName);

        currentJoinBuilder = MethodSpec.methodBuilder("getJoinToInnerTable")
                .addParameter(wildcardDBTableParameter)
                .returns(TypeName.get(String.class))
                .addModifiers(Modifier.PROTECTED);
    }

    private void getConstructor() {
        String dbName = currentTableAnnotation.tableName().equals(JoTable.STRING_IGNORE) ? currentTableName : currentTableAnnotation.tableName();

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super($S, $L.class)", dbName, currentTableElement.getSimpleName())
                .build());
    }

    private void getIdElement(RoundEnvironment roundEnvironment) {
        currentIdElement = null;
        for (Element maybeIdElement : roundEnvironment.getElementsAnnotatedWith(JoId.class)) {
            if (maybeIdElement.getEnclosingElement().equals(currentTableElement)) {
                currentIdElement = maybeIdElement;
            }
        }
    }

    private void getIdMethods() {
        List<MethodSpec> idMethods = new ArrayList<>();
        idMethods.add(MethodSpec.methodBuilder("selectId")
                .returns(currentClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("selectId($S)", currentIdElement == null ? "oui" : currentIdElement.getSimpleName())
                .addStatement("return this")
                .build());

        idMethods.add(MethodSpec.methodBuilder("getId")
                .returns(String.class)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return $S", currentIdElement == null ? "oui" : currentIdElement.getSimpleName())
                .build());


        classBuilder.addMethods(idMethods);
    }

    private void getFieldRelated(Element fieldElement) {
        List<MethodSpec> fieldsMethods = new ArrayList<>();
        if (fieldElement.getKind().equals(ElementKind.FIELD) && fieldElement.getAnnotation(JoIgnore.class) == null) {
            if (fieldElement != currentIdElement) {
                if (currentTableAnnotation.isGeneratingSelect()) {
                    MethodSpec method = getSelectMethod(fieldElement);
                    fieldsMethods.add(method);
                }
                if (currentTableAnnotation.isGeneratingWrite()) {
                    MethodSpec method = getWriteMethod(fieldElement);
                    if (method != null) {
                        fieldsMethods.add(method);
                    }
                }
            }
            getColumnNameField(fieldElement);
            try {
                if (fieldElement.asType().getKind() == TypeKind.DECLARED) {
                    getDeclaredKindFieldMethods(fieldElement);
                }
            } catch (NullPointerException ex) {
                String message = "";
                for (StackTraceElement oui : ex.getStackTrace()) {
                    message += oui.toString() + "\n";
                }
                classBuilder.addMethod(getDebugMethod(message));
            }
        }
        classBuilder.addMethods(fieldsMethods);
    }

    private void getDeclaredKindFieldMethods(Element fieldElement) {
        DeclaredType fieldDeclaredType = (DeclaredType) fieldElement.asType();
        TypeElement typeElement = (TypeElement) fieldDeclaredType.asElement();
        JoJoin joinAnnotation = fieldElement.getAnnotation(JoJoin.class);

        if (joinAnnotation != null) {
            DeclaredType customClassType = (DeclaredType) getTableClass(joinAnnotation);
            TypeName customClassTypeName = ClassName.get(customClassType);
            if (!customClassTypeName.equals(ClassName.get(DBTable.class))) {
                TypeElement customClassElement = (TypeElement) customClassType.asElement();

                for (Element customClassEnclosedElement : customClassElement.getEnclosedElements()) {
                    JoCustomJoin customJoinAnnotation = customClassEnclosedElement.getAnnotation(JoCustomJoin.class);
                    if (customClassEnclosedElement.getKind() == ElementKind.METHOD && customJoinAnnotation != null) {
                        currentJoinBuilder.beginControlFlow("if (innerTable instanceof $T)", customClassType)
                                .addStatement("return (($L) $L).$L($L)", customClassElement.getSimpleName(), "innerTable", customClassEnclosedElement.getSimpleName(), customJoinAnnotation.getParams())
                                .endControlFlow();
                    }
                }
            }
        } else if (typeElement.getAnnotation(JoTable.class) != null) { // todo verify superclass
            Name fieldName = fieldDeclaredType.asElement().getSimpleName();
            ClassName fieldTableClassName = ClassName.bestGuess(fieldName + "Table");
            currentJoinBuilder
                    .beginControlFlow("if (innerTable instanceof $T)", fieldTableClassName)
                    .addStatement("return getJoinOnRef(innerTable, $S, false)", fieldName + "_id")
                    .endControlFlow();
        }
    }

    private void getColumnNameField(Element selectable) {
        String dataFieldName = String.valueOf(selectable.getSimpleName());
        String columnFieldName = camelToSnake(dataFieldName);
        classBuilder.addField(FieldSpec
                .builder(TypeName.get(String.class), "COLUMN_" + columnFieldName.toUpperCase(), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", dataFieldName)
                .build());
    }

    private MethodSpec getSelectMethod(Element selectable) {
        String typeName;
        String statementFormat = "select$L($S)";
        TypeMirror typeMirror = selectable.asType();
        String parameterName = snakeToCamel(selectable.getSimpleName().toString());
        String selectMethodName = "select" + parameterName.substring(0, 1).toUpperCase() + parameterName.substring(1);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(selectMethodName);

        switch (typeMirror.getKind()) {
            case BOOLEAN:
                typeName = "Boolean";
                break;
            case BYTE:
                typeName = "Byte";
                break;
            case SHORT:
                typeName = "Short";
                break;
            case INT:
                typeName = "Int";
                break;
            case LONG:
                typeName = "Long";
                break;
            case CHAR:
                typeName = "Char";
                break;
            case FLOAT:
                typeName = "Float";
                break;
            case DOUBLE:
                typeName = "Double";
                break;
            case ARRAY:
                typeName = "Array";
                break;
            case DECLARED:
                DeclaredType declaredType = (DeclaredType) typeMirror;
                TypeElement typeElement = (TypeElement) declaredType.asElement();
                TypeElement superTypeElement = typeElement;
                JoJoin annotation = selectable.getAnnotation(JoJoin.class);

                while (superTypeElement.getAnnotation(JoTable.class) == null) {
                    TypeMirror superclass = superTypeElement.getSuperclass();
                    if (superclass instanceof DeclaredType) {
                        superTypeElement = (TypeElement) ((DeclaredType) superclass).asElement();
                    } else {
                        break;
                    }
                }

                if (superTypeElement.getAnnotation(JoTable.class) != null || (annotation != null && annotation.getTableClass() != DBTable.class)) {
                    statementFormat = "select$L($L)";
                    TypeName className;
                    if (annotation != null && !ClassName.get(getTableClass(annotation)).equals(ClassName.get(DBTable.class))) {
                        className = ClassName.get(getTableClass(annotation));
                    } else {
                        className = ClassName.get(PACKAGE_NAME, typeElement.getSimpleName() + "Table");
                    }
                    typeName = "Table";
                    builder.addParameter(ParameterSpec.builder(className, parameterName).build());
                } else {
                    typeName = "" + typeElement.getSimpleName();
                }
                break;
            default:
                typeName = "lolType";

        }

        return builder.returns(currentClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement(statementFormat, typeName, parameterName)
                .addStatement("return this")
                .build();
    }

    private TypeMirror getTableClass(JoJoin annotation) {
        try {
            annotation.getTableClass();
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
        return (TypeMirror) ClassName.get(DBTable.class).box();
    }

    private MethodSpec getWriteMethod(Element selectable) {
        String typeName;
        TypeMirror typeMirror = selectable.asType();
        ParameterSpec value = ParameterSpec.builder(TypeName.get(typeMirror), "value").build();
        switch (typeMirror.getKind()) {

            case BOOLEAN:
                typeName = "Boolean";
                break;
            case BYTE:
                typeName = "Byte";
                break;
            case SHORT:
                typeName = "Short";
                break;
            case INT:
                typeName = "Int";
                break;
            case LONG:
                typeName = "Long";
                break;
            case CHAR:
                typeName = "Char";
                break;
            case FLOAT:
                typeName = "Float";
                break;
            case DOUBLE:
                typeName = "Double";
                break;
            case ARRAY:
                typeName = "Array";
                break;
            case DECLARED:
                DeclaredType declaredType = (DeclaredType) typeMirror;
                TypeElement typeElement = (TypeElement) declaredType.asElement();
                if (typeElement.getAnnotation(JoTable.class) != null) {
                    return null; //todo
                } else {
                    typeName = "" + typeElement.getSimpleName();
                }
                break;
            default:
                typeName = "lolType";

        }

        String selectableSimpleName = selectable.getSimpleName().toString();
        selectableSimpleName = selectableSimpleName.substring(0, 1).toUpperCase() + selectableSimpleName.substring(1);

        return MethodSpec.methodBuilder("write" + selectableSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(value)
                .addStatement("write$L($S, $L)", typeName, selectable.getSimpleName(), "value")
                .build();
    }

    private String snakeToCamel(String dataFieldName) {
        String columnFieldName = dataFieldName;

        for (int i = dataFieldName.length() - 1; i >= 0; i--) {
            if (dataFieldName.charAt(i) == '_') {
                columnFieldName = dataFieldName.substring(0, i) + columnFieldName.substring(i + 1, i + 2).toUpperCase() + columnFieldName.substring(i + 2, columnFieldName.length());
            }
        }

        return columnFieldName;
    }

    private String camelToSnake(String dataFieldName) {
        String columnFieldName = dataFieldName;

        for (int i = dataFieldName.length() - 1; i >= 0; i--) {
            if (Character.isUpperCase(dataFieldName.charAt(i))) {
                columnFieldName = dataFieldName.substring(0, i) + '_' + columnFieldName.substring(i, columnFieldName.length());
            }
        }

        return columnFieldName;
    }

    private MethodSpec getDebugMethod(String toDisplay) {
        return MethodSpec.methodBuilder("debug" + debugCount++)
                .returns(TypeName.get(String.class))
                .addStatement("return $S", toDisplay)
                .build();
    }
}
