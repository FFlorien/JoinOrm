package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import be.florien.joinorm.annotation.JoId;
import be.florien.joinorm.annotation.JoTable;
import be.florien.joinorm.architecture.DBTable;

@SupportedAnnotationTypes("be.florien.joinorm.annotation.JoTable")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class DBTableProcessor extends AbstractProcessor {

    private static final String PACKAGE_NAME = "be.florien.joinorm.generated";
    private ParameterizedTypeName parametrisedDBTableClassName;
    private String currentTableName;
    private Element currentElement;
    private Element currentIdElement;
    private JoTable currentAnnotation;

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Class<JoTable> annotationTableClass = JoTable.class;
        for (Element classElement : roundEnvironment.getElementsAnnotatedWith(annotationTableClass)) {
            currentElement = classElement;
            currentAnnotation = currentElement.getAnnotation(annotationTableClass);

            if (classElement.getKind() != ElementKind.CLASS) {
                continue;
            }

            currentIdElement = null;
            for (Element maybeIdElement : roundEnvironment.getElementsAnnotatedWith(JoId.class)) {
                if (maybeIdElement.getEnclosingElement().equals(classElement)) {
                    currentIdElement = maybeIdElement;
                }
            }

            try {
                ClassName dbTableClassName = ClassName.get(DBTable.class);
                ClassName modelObjectClassName = ClassName.get((TypeElement) classElement);
                parametrisedDBTableClassName = ParameterizedTypeName.get(dbTableClassName, modelObjectClassName);
                ParameterizedTypeName wildcardDBTableClassName = ParameterizedTypeName.get(dbTableClassName, WildcardTypeName.subtypeOf(TypeName.OBJECT));
                ParameterSpec wildcardDBTableParameter = ParameterSpec.builder(wildcardDBTableClassName, "innerTable").build();
                currentTableName = classElement.getSimpleName() + "Table";

                MethodSpec constructor = getConstructors();

                MethodSpec.Builder joinToInnerTableBuilder = MethodSpec.methodBuilder("getJoinToInnerTable")
                        .addParameter(wildcardDBTableParameter)
                        .returns(TypeName.get(String.class))
                        .addModifiers(Modifier.PROTECTED);

                List<MethodSpec> idMethods = getIdMethods();
                List<MethodSpec> selectMethods = goThroughFields(joinToInnerTableBuilder);

                MethodSpec joinToInnerTable = joinToInnerTableBuilder.addStatement("return \"\"")
                        .build();

                TypeSpec typeSpec = TypeSpec.classBuilder(currentTableName)
                        .addModifiers(Modifier.PUBLIC)
                        .superclass(parametrisedDBTableClassName)
                        .addMethod(constructor)
                        .addMethods(idMethods)
                        .addMethods(selectMethods)
                        .addMethod(joinToInnerTable)
                        .build();

                JavaFile.builder(PACKAGE_NAME, typeSpec)
                        .build().writeTo(processingEnv.getFiler());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private MethodSpec getConstructors() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super($S, $L.class)", currentTableName, currentElement.getSimpleName())
                .build();
    }

    private List<MethodSpec> getIdMethods() {
        List<MethodSpec> idMethods = new ArrayList<>();
        idMethods.add(MethodSpec.methodBuilder("selectId")
                .returns(parametrisedDBTableClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("selectId($S)", currentIdElement == null ? "oui" : currentIdElement.getSimpleName())
                .addStatement("return this")
                .build());

        idMethods.add(MethodSpec.methodBuilder("getId")
                .returns(String.class)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return $S", currentIdElement == null ? "oui" : currentIdElement.getSimpleName())
                .build());

        return idMethods;
    }

    private List<MethodSpec> goThroughFields(MethodSpec.Builder joinToInnerTableBuilder) {
        List<MethodSpec> fieldsMethods = new ArrayList<>();

        for (Element field : currentElement.getEnclosedElements()) {
            if (field != currentIdElement && field.getKind().equals(ElementKind.FIELD)) {
                if (currentAnnotation.isGeneratingSelect()) {
                    MethodSpec method = getSelectMethod(field);
                    fieldsMethods.add(method);
                }
                if (currentAnnotation.isGeneratingWrite()) {
                    MethodSpec method = getWriteMethod(field);
                    if (method != null) {
                        fieldsMethods.add(method);
                    }
                }
                if (field.asType().getKind() == TypeKind.DECLARED) {
                    DeclaredType declaredType = (DeclaredType) field.asType();
                    TypeElement typeElement = (TypeElement) declaredType.asElement();
                    if (typeElement.getAnnotation(JoTable.class) != null) { // todo verify superclass
                        Name fieldName = declaredType.asElement().getSimpleName();
                        ClassName fieldTableClassName = ClassName.bestGuess(fieldName + "Table");
                        joinToInnerTableBuilder.beginControlFlow("if (innerTable instanceof $T)", fieldTableClassName)
                                .addStatement("return getJoinOnRef(innerTable, $S, false)", fieldName + "_id")
                                .endControlFlow();
                    }
                }
            }
        }
        return fieldsMethods;
    }

    private MethodSpec getSelectMethod(Element selectable) {
        String typeName;
        String statementFormat = "select$L($S)";
        TypeMirror typeMirror = selectable.asType();
        String selectableSimpleName = selectable.getSimpleName().toString();
        selectableSimpleName = selectableSimpleName.substring(0, 1).toUpperCase() + selectableSimpleName.substring(1);
        MethodSpec.Builder builder = MethodSpec.methodBuilder("select" + selectableSimpleName);

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
                    statementFormat = "select$L($L)";
                    ClassName className = ClassName.get(PACKAGE_NAME, typeElement.getSimpleName() + "Table");
                    typeName = "Table";
                    builder.addParameter(ParameterSpec.builder(className, String.valueOf(selectable.getSimpleName())).build());
                } else {
                    typeName = "" + typeElement.getSimpleName();
                }
                break;
            default:
                typeName = "lolType";

        }

        return builder.returns(parametrisedDBTableClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement(statementFormat, typeName, selectable.getSimpleName())
                .addStatement("return this")
                .build();
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
}
