package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

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
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;

import be.florien.joinorm.annotation.JoIgnore;
import be.florien.joinorm.annotation.JoTable;
import be.florien.joinorm.architecture.DBTable;

@SupportedAnnotationTypes("be.florien.joinorm.annotation.JoTable")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class DBTableProcessor extends AbstractProcessor {

    private int debugCount = 0;
    private String currentTableName;
    private Element currentModelElement;
    private String currentTablePackageName;
    private ClassName currentTableClassName;
    private JoTable currentModelAnnotation;
    private TypeSpec.Builder currentClassBuilder;
    private FieldRelatedElementsBuilder fieldElementBuilder;
    private JoinToInnerTableMethodBuilder joinMethodBuilder;
    private Messager messager;

    //TODO supportedAnnotationTypes is it important ????
    //todo error launched at compile time for inconsistent annotation configuration

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

            initBuilders();
            addConstructor();

            for (Element field : currentModelElement.getEnclosedElements()) {
                addFieldRelatedElements(field);
            }

            fieldElementBuilder.addSpecToBuilder(currentClassBuilder);
            currentClassBuilder.addMethod(joinMethodBuilder.getJoinToInnerTableMethod());

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

    private void initBuilders() {
        ClassName dbTableClassName = ClassName.get(DBTable.class);
        ClassName modelClassName = ClassName.get((TypeElement) currentModelElement);
        ParameterizedTypeName parametrisedDBTableClassName = ParameterizedTypeName.get(dbTableClassName, modelClassName);

        currentClassBuilder = TypeSpec.classBuilder(currentTableName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(parametrisedDBTableClassName);

        fieldElementBuilder = new FieldRelatedElementsBuilder(currentModelAnnotation.isGeneratingSelect(), currentModelAnnotation.isGeneratingWrite(), currentTablePackageName, currentTableClassName);
        joinMethodBuilder = new JoinToInnerTableMethodBuilder(currentTablePackageName);

    }

    private void addConstructor() {
        String dbName = currentModelAnnotation.tableName().equals(JoTable.STRING_IGNORE) ? currentTableName : currentModelAnnotation.tableName();

        currentClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super($S, $L.class)", dbName, currentModelElement.getSimpleName())
                .build());
    }

    private void addFieldRelatedElements(Element fieldElement) {
        List<MethodSpec> fieldMethods = new ArrayList<>();
        if (fieldElement.getKind().equals(ElementKind.FIELD) && fieldElement.getAnnotation(JoIgnore.class) == null) {
            fieldElementBuilder.addFieldRelatedElements(fieldElement);
            if (fieldElement.asType().getKind() == TypeKind.DECLARED) {
                joinMethodBuilder.buildGetJoin(fieldElement);
            }
        }
        currentClassBuilder.addMethods(fieldMethods);
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
