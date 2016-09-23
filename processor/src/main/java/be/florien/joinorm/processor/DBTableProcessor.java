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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import be.florien.joinorm.annotation.JoCustomJoin;
import be.florien.joinorm.annotation.JoId;
import be.florien.joinorm.annotation.JoIgnore;
import be.florien.joinorm.annotation.JoJoin;
import be.florien.joinorm.annotation.JoTable;
import be.florien.joinorm.architecture.DBTable;

@SupportedAnnotationTypes("be.florien.joinorm.annotation.JoTable")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class DBTableProcessor extends AbstractProcessor {

    private int debugCount = 0;
    private boolean isStartOfJoin;
    private String currentTableName;
    private Element currentModelElement;
    private Element currentIdElement;
    private String currentTablePackageName;
    private ClassName currentTableClassName;
    private JoTable currentModelAnnotation;
    private TypeSpec.Builder currentClassBuilder;
    private MethodSpec.Builder currentJoinBuilder;
    private Messager messager;

    //TODO list of Table


    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        messager = processingEnvironment.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (Element tableElement : roundEnvironment.getElementsAnnotatedWith(JoTable.class)) {
            if (tableElement.getKind() != ElementKind.CLASS) {
                continue;
            }

            currentModelElement = tableElement;
            currentTablePackageName = ClassName.get((TypeElement) currentModelElement).packageName() + ".table";
            currentModelAnnotation = currentModelElement.getAnnotation(JoTable.class);
            currentTableName = tableElement.getSimpleName() + "Table";
            currentTableClassName = ClassName.get(currentTablePackageName, currentTableName);

            initBuilder();
            getIdElement(roundEnvironment);
            addConstructor();
            addIdMethods();

            for (Element field : currentModelElement.getEnclosedElements()) {
                addFieldRelatedMethods(field);
            }

            if (!isStartOfJoin) {
                currentJoinBuilder.endControlFlow();
            }

            currentClassBuilder.addMethod(currentJoinBuilder.addStatement("return \"\"").build());

            try {
                JavaFile.builder(currentTablePackageName, currentClassBuilder.build())
                        .build()
                        .writeTo(processingEnv.getFiler());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private void initBuilder() {
        ClassName dbTableClassName = ClassName.get(DBTable.class);
        ClassName modelClassName = ClassName.get((TypeElement) currentModelElement);
        ParameterizedTypeName parametrisedDBTableClassName = ParameterizedTypeName.get(dbTableClassName, modelClassName);
        ParameterizedTypeName wildcardDBTableClassName = ParameterizedTypeName.get(dbTableClassName, WildcardTypeName.subtypeOf(TypeName.OBJECT));
        ParameterSpec wildcardDBTableParameter = ParameterSpec.builder(wildcardDBTableClassName, "innerTable").build();

        currentClassBuilder = TypeSpec.classBuilder(currentTableName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(parametrisedDBTableClassName);

        isStartOfJoin = true;
        currentJoinBuilder = MethodSpec.methodBuilder("getJoinToInnerTable")
                .addParameter(wildcardDBTableParameter)
                .returns(TypeName.get(String.class))
                .addModifiers(Modifier.PROTECTED);
    }

    private void addConstructor() {
        String dbName = currentModelAnnotation.tableName().equals(JoTable.STRING_IGNORE) ? currentTableName : currentModelAnnotation.tableName();

        currentClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super($S, $L.class)", dbName, currentModelElement.getSimpleName())
                .build());
    }

    private void getIdElement(RoundEnvironment roundEnvironment) {
        currentIdElement = null;
        for (Element maybeIdElement : roundEnvironment.getElementsAnnotatedWith(JoId.class)) {
            if (maybeIdElement.getEnclosingElement().equals(currentModelElement)) {
                currentIdElement = maybeIdElement;
            }
        }
    }

    private void addIdMethods() {
        List<MethodSpec> idMethods = new ArrayList<>();
        idMethods.add(MethodSpec.methodBuilder("selectId")
                .returns(currentTableClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("selectId($S)", currentIdElement == null ? "oui" : currentIdElement.getSimpleName())
                .addStatement("return this")
                .build());

        idMethods.add(MethodSpec.methodBuilder("getId")
                .returns(String.class)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return $S", currentIdElement == null ? "oui" : currentIdElement.getSimpleName())
                .build());


        currentClassBuilder.addMethods(idMethods);
    }

    private void addFieldRelatedMethods(Element fieldElement) {
        List<MethodSpec> fieldMethods = new ArrayList<>();
        if (fieldElement.getKind().equals(ElementKind.FIELD) && fieldElement.getAnnotation(JoIgnore.class) == null) {
            if (fieldElement != currentIdElement) {
                if (currentModelAnnotation.isGeneratingSelect()) {
                    MethodSpec method = getSelectMethod(fieldElement);
                    if (method != null) {
                        fieldMethods.add(method);
                    }
                }
                if (currentModelAnnotation.isGeneratingWrite()) {
                    MethodSpec method = getWriteMethod(fieldElement);
                    if (method != null) {
                        fieldMethods.add(method);
                    }
                }
            }
            addColumnField(fieldElement);
            if (fieldElement.asType().getKind() == TypeKind.DECLARED) {
                buildGetJoin(fieldElement);
            }
        }
        currentClassBuilder.addMethods(fieldMethods);
    }

    private void buildGetJoin(Element fieldElement) {
        JoJoin joinAnnotation = fieldElement.getAnnotation(JoJoin.class);

        if (joinAnnotation != null && isJoinCustomClassDefined(joinAnnotation)) {
            DeclaredType customTableClassType = (DeclaredType) getTableClass(joinAnnotation);
            TypeName customTableClassTypeName = ClassName.get(customTableClassType);
            if (!customTableClassTypeName.equals(ClassName.get(DBTable.class))) {
                TypeElement customTableClassElement = (TypeElement) customTableClassType.asElement();

                for (Element customTableClassEnclosedElement : customTableClassElement.getEnclosedElements()) {
                    JoCustomJoin customJoinAnnotation = customTableClassEnclosedElement.getAnnotation(JoCustomJoin.class);
                    if (customTableClassEnclosedElement.getKind() == ElementKind.METHOD && customJoinAnnotation != null) {
                        newConditionForJoin(customTableClassType);
                        currentJoinBuilder.addStatement("return (($L) $L).$L($L)",
                                customTableClassElement.getSimpleName(),
                                "innerTable",
                                customTableClassEnclosedElement.getSimpleName(),
                                customJoinAnnotation.getParams());
                    }
                }
            } else {
                newConditionForJoin(customTableClassType);
                currentJoinBuilder.addStatement("return getJoinOn$L(innerTable, $S, $L)",
                        joinAnnotation.isReferenceJoin() ? "Ref" : "Id", joinAnnotation.getTableRef(),
                        joinAnnotation.isLeftJoin());
            }
        }
    }

    private void newConditionForJoin(DeclaredType customClassType) {
        if (isStartOfJoin) {
            currentJoinBuilder.beginControlFlow("if (innerTable instanceof $T)", customClassType);
            isStartOfJoin = false;
        } else {
            currentJoinBuilder.nextControlFlow("else if (innerTable instanceof $T)", customClassType);
        }
    }

    private void addColumnField(Element fieldElement) {
        String columnFieldValue = String.valueOf(fieldElement.getSimpleName());
        String columnFieldName = "COLUMN_" + camelToSnake(columnFieldValue).toUpperCase();
        currentClassBuilder.addField(
                FieldSpec.builder(TypeName.get(String.class), columnFieldName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$S", columnFieldValue)
                        .build());
    }

    private MethodSpec getSelectMethod(Element fieldElement) {
        String selectTypeName = getTypeName(fieldElement);
        String statementFormat = "select$L($S)";
        String parameterName = snakeToCamel(fieldElement.getSimpleName().toString());
        String selectMethodName = "select" + parameterName.substring(0, 1).toUpperCase() + parameterName.substring(1);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(selectMethodName);

        if (selectTypeName.equals("declared")) {
            DeclaredType fieldDeclaredType = (DeclaredType) fieldElement.asType();
            JoJoin fieldJoinAnnotation = fieldElement.getAnnotation(JoJoin.class);
            TypeElement fieldTypeElement = (TypeElement) fieldDeclaredType.asElement();
            if (ClassName.get(fieldDeclaredType).equals(ClassName.get(String.class))) {
                selectTypeName = "String";
            } else if (fieldJoinAnnotation != null) {
                TypeName className = null;
                if (isJoinCustomClassDefined(fieldJoinAnnotation)) {
                    className = ClassName.get(getTableClass(fieldJoinAnnotation));
                } else {
                    DeclaredType parameterDeclaredType = getTypeParameterDeclaredType(fieldDeclaredType);
                    while (fieldTypeElement.getAnnotation(JoTable.class) == null && (parameterDeclaredType == null || parameterDeclaredType.asElement().getAnnotation(JoTable.class) == null)) {
                        TypeMirror superclass = fieldTypeElement.getSuperclass();
                        if (superclass instanceof DeclaredType) {
                            fieldTypeElement = (TypeElement) ((DeclaredType) superclass).asElement();
                            parameterDeclaredType = getTypeParameterDeclaredType((DeclaredType) fieldElement.asType());
                        } else {
                            break;
                        }
                    }

                    if (fieldTypeElement.getAnnotation(JoTable.class) != null) {
                        className = ClassName.get(currentTablePackageName, fieldTypeElement.getSimpleName() + "Table");
                    } else if (parameterDeclaredType != null && parameterDeclaredType.asElement().getAnnotation(JoTable.class) != null) {
                        className = ClassName.get(currentTablePackageName, parameterDeclaredType.asElement().getSimpleName() + "Table");
                    }
                }

                if (className != null) {
                    selectTypeName = "Table";
                    statementFormat = "select$L($L)";
                    builder.addParameter(ParameterSpec.builder(className, parameterName).build());
                } else {
                    return null;
                }
            } else {
                return null;
            }

        }

        return builder.returns(currentTableClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement(statementFormat, selectTypeName, parameterName)
                .addStatement("return this")
                .build();
    }

    private boolean isJoinCustomClassDefined(JoJoin fieldJoinAnnotation) {
        return !ClassName.get(getTableClass(fieldJoinAnnotation)).equals(ClassName.get(DBTable.class));
    }

    private DeclaredType getTypeParameterDeclaredType(DeclaredType declaredType) {
        DeclaredType parameterDeclaredType = null;
        TypeMirror superParameterType = null;
        if (declaredType.getTypeArguments().size() == 1) {
            superParameterType = declaredType.getTypeArguments().get(0);
        }
        if (superParameterType != null && superParameterType.getKind() == TypeKind.DECLARED) {
            parameterDeclaredType = (DeclaredType) superParameterType;
        }
        return parameterDeclaredType;
    }

    private MethodSpec getWriteMethod(Element selectable) {
        String typeName = getTypeName(selectable);
        TypeMirror typeMirror = selectable.asType();
        ParameterSpec value = ParameterSpec.builder(TypeName.get(typeMirror), "value").build();
        if (typeName.equals("declared")) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            TypeElement typeElement = (TypeElement) declaredType.asElement();
            if (typeElement.getAnnotation(JoTable.class) != null) {
                return null; //todo
            } else {
                typeName = "" + typeElement.getSimpleName();
            }

        }

        String selectableSimpleName = selectable.getSimpleName().toString();
        selectableSimpleName = selectableSimpleName.substring(0, 1).toUpperCase() + selectableSimpleName.substring(1);

        return MethodSpec.methodBuilder("write" + selectableSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(value)
                .addStatement("write$L($S, $L)", typeName, selectable.getSimpleName(), "value")
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

    private String getTypeName(Element selectable) {
        TypeMirror typeMirror = selectable.asType();
        switch (typeMirror.getKind()) {
            case BOOLEAN:
                return "Boolean";
            case BYTE:
                return "Byte";
            case SHORT:
                return "Short";
            case INT:
                return "Int";
            case LONG:
                return "Long";
            case CHAR:
                return "Char";
            case FLOAT:
                return "Float";
            case DOUBLE:
                return "Double";
            case ARRAY:
                return "Array";
            case DECLARED:
                return "declared";
            default:
                return "lolType";

        }
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

    @SuppressWarnings("unused")
    private void getDebugMethod(String toDisplay) {
        messager.printMessage(Diagnostic.Kind.NOTE, toDisplay);
        currentClassBuilder.addMethod(MethodSpec.methodBuilder("debug" + debugCount++)
                .returns(TypeName.get(String.class))
                .addStatement("return $S", toDisplay)
                .build());
    }
}
