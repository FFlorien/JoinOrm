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

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Class<JoTable> aClass = JoTable.class;
        for (Element classElement : roundEnvironment.getElementsAnnotatedWith(aClass)) {

            Element idElement = null;
            for (Element maybeIdElement : roundEnvironment.getElementsAnnotatedWith(JoId.class)) {
                if (maybeIdElement.getEnclosingElement().equals(classElement)) {
                    idElement = maybeIdElement;
                }
            }

            try {
                ClassName dbTableClassName = ClassName.get(DBTable.class);
                ClassName modelObjectClassName = ClassName.get((TypeElement) classElement);
                ParameterizedTypeName paramDBTableClassName = ParameterizedTypeName.get(dbTableClassName, modelObjectClassName);
                ParameterizedTypeName wildcardDBTableClassName = ParameterizedTypeName.get(dbTableClassName, WildcardTypeName.subtypeOf(TypeName.OBJECT));
                ParameterSpec wildcardDBTableParameter = ParameterSpec.builder(wildcardDBTableClassName, "innerTable").build();
                String tableName = classElement.getSimpleName() + "Table";

                MethodSpec constructor = MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("super(\"" + tableName + "\", " + classElement.getSimpleName() + ".class)")
                        .build();

                MethodSpec.Builder joinToInnerTableBuilder = MethodSpec.methodBuilder("getJoinToInnerTable")
                        .addParameter(wildcardDBTableParameter)
                        .returns(TypeName.get(String.class))
                        .addModifiers(Modifier.PROTECTED);

                MethodSpec selectId = MethodSpec.methodBuilder("selectId")
                        .returns(paramDBTableClassName)
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("selectId(\"" + (idElement == null ? "oui" : idElement.getSimpleName()) + "\")")
                        .addStatement("return this")
                        .build();

                MethodSpec getId = MethodSpec.methodBuilder("getId")
                        .returns(String.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement("return \"" + (idElement == null ? "oui" : idElement.getSimpleName()) + "\"")
                        .build();

                List<MethodSpec> selectMethods = new ArrayList<>();

                for (Element field : classElement.getEnclosedElements()) {
                    if (field != idElement && field.getKind().equals(ElementKind.FIELD)) {
                        MethodSpec methodSpec = getSelectMethod(paramDBTableClassName, field);
                        selectMethods.add(methodSpec);
                    }
//                    TypeMirror typeMirror = field.asType();
//                    Class<TypeKind> declaringClass = typeMirror.getKind().getDeclaringClass();
//                    JoTable annotation = typeMirror.getAnnotation(aClass);
//                    if (annotation != null) { // todo verify superclass
//                        Name fieldName = ((DeclaredType) field).asElement().getSimpleName();
//                        ClassName fieldTableClassName = ClassName.bestGuess(fieldName + "Table");
//                        joinToInnerTableBuilder.beginControlFlow("if(innerTable instanceof %s)", fieldTableClassName)
//                                .addStatement("return getJoinOnRef(innerTable, %s, false)", fieldName + "_id")
//                                .endControlFlow();
//                    }else{
//                        joinToInnerTableBuilder.addStatement("return \"Field == \" +  $S + \" | Type == \" +  $S + \" | DeclaringClass == \" +  $S ", field.getSimpleName(), typeMirror.toString(), declaringClass.getSimpleName());
//                    }
                }

                MethodSpec joinToInnerTable = joinToInnerTableBuilder.addStatement("return \"\"")
                        .build();

                TypeSpec typeSpec = TypeSpec.classBuilder(tableName)
                        .addModifiers(Modifier.PUBLIC)
                        .superclass(paramDBTableClassName)
                        .addMethod(constructor)
                        .addMethod(getId)
                        .addMethod(selectId)
                        .addMethods(selectMethods)
                        .addMethod(joinToInnerTable)
                        .build();

                JavaFile.builder("be.florien.joinorm.generated", typeSpec)
                        .build().writeTo(processingEnv.getFiler());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private MethodSpec getSelectMethod(ParameterizedTypeName extendsClass, Element selectable) {
        String typeName;
        TypeMirror typeMirror = selectable.asType();
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
                typeName = typeElement.getSimpleName().toString();
                break;
            default:
                typeName = "lolType";

        }

        String selectableSimpleName = selectable.getSimpleName().toString();
        selectableSimpleName = selectableSimpleName.substring(0, 1).toUpperCase() + selectableSimpleName.substring(1);

        return MethodSpec.methodBuilder("select" + selectableSimpleName)
                .returns(extendsClass)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("select" + typeName + "(\"" + selectable.getSimpleName() + "\")")
                .addStatement("return this")
                .build();
    }
}
