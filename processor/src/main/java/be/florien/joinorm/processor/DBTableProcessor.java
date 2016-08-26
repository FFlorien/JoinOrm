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
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import be.florien.joinorm.annotation.JoTable;
import be.florien.joinorm.architecture.DBTable;

@SupportedAnnotationTypes("be.florien.joinorm.annotation.JoTable")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class DBTableProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(JoTable.class)) {

            try {
                ClassName dbTableClass = ClassName.get(DBTable.class);
                ClassName javaObjectClass = ClassName.get((TypeElement) element);
                ParameterizedTypeName extendsClass = ParameterizedTypeName.get(dbTableClass, javaObjectClass);
                ParameterizedTypeName anyDBTableClass = ParameterizedTypeName.get(dbTableClass, WildcardTypeName.subtypeOf(TypeName.OBJECT));
                ParameterSpec otherTable = ParameterSpec.builder(anyDBTableClass, "innerTable").build();

                MethodSpec joinToInnerTable = MethodSpec.methodBuilder("getJoinToInnerTable")
                        .addParameter(otherTable)
                        .returns(TypeName.get(String.class))
                        .addModifiers(Modifier.PROTECTED)
                        .addStatement("return getJoinOnId(this, \"tutu\", false)")
                        .build();
                TypeSpec typeSpec = TypeSpec.classBuilder(element.getSimpleName() + "Table")
                        .addModifiers(Modifier.PUBLIC)
                        .superclass(extendsClass)
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
}
