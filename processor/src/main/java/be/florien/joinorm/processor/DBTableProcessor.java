package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
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

import be.florien.joinorm.annotation.JoId;
import be.florien.joinorm.annotation.JoIgnore;
import be.florien.joinorm.annotation.JoTable;
import be.florien.joinorm.architecture.DBTable;

@SupportedAnnotationTypes("be.florien.joinorm.annotation.JoTable")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class DBTableProcessor extends AbstractProcessor {

    private int debugCount = 0;
    private String currentTableName;
    private Element currentModelElement;
    private List<Element> currentIdElements;
    private String currentTablePackageName;
    private ClassName currentTableClassName;
    private JoTable currentModelAnnotation;
    private TypeSpec.Builder currentClassBuilder;
    private FieldRelatedMethodsBuilder fieldMethodBuilder;
    private JoinToInnerTableMethodBuilder joinMethodBuilder;
    private Messager messager;

    //TODO supportedAnnotationTypes is it important ????

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
            getIdElement(roundEnvironment);
            addConstructor();
            addIdMethods();

            for (Element field : currentModelElement.getEnclosedElements()) {
                addFieldRelatedMethods(field);
            }

            currentClassBuilder.addMethods(fieldMethodBuilder.getMethods());
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

        fieldMethodBuilder = new FieldRelatedMethodsBuilder(currentModelAnnotation.isGeneratingSelect(), currentModelAnnotation.isGeneratingWrite(), currentTablePackageName, currentTableClassName);
        joinMethodBuilder = new JoinToInnerTableMethodBuilder(currentTablePackageName);

    }

    private void addConstructor() {
        String dbName = currentModelAnnotation.tableName().equals(JoTable.STRING_IGNORE) ? currentTableName : currentModelAnnotation.tableName();

        currentClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super($S, $L.class)", dbName, currentModelElement.getSimpleName())
                .build());
    }

    private void getIdElement(RoundEnvironment roundEnvironment) {
        currentIdElements = new ArrayList<>();
        for (Element maybeIdElement : roundEnvironment.getElementsAnnotatedWith(JoId.class)) {
            if (maybeIdElement.getEnclosingElement().equals(currentModelElement)) {
                currentIdElements.add(maybeIdElement);
            }
        }
    }

    private void addIdMethods() {
        List<MethodSpec> idMethods = new ArrayList<>();
        MethodSpec.Builder selectIdBuilder = MethodSpec.methodBuilder("selectId")
                .addAnnotation(Override.class)
                .returns(currentTableClassName)
                .addModifiers(Modifier.PUBLIC);

        if (currentIdElements.size() == 1) {
            selectIdBuilder.addStatement("selectId($S)", currentIdElements.get(0).getSimpleName());
        } else if (currentIdElements.size() == 2) {
            selectIdBuilder.addStatement("selectId($S, $S)", currentIdElements.get(0).getSimpleName(), currentIdElements.get(1).getSimpleName());
        } else {
            return;
        }

        idMethods.add(selectIdBuilder.addStatement("return this")
                .build());

        idMethods.add(MethodSpec.methodBuilder("getId")
                .returns(String.class)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return $S", currentIdElements.get(0).getSimpleName()) //todo change here for when dual id is correctly implemented (triple id ? quadruple id ??? <not defined number> id ??!??!?)
                .build());


        currentClassBuilder.addMethods(idMethods);
    }

    private void addFieldRelatedMethods(Element fieldElement) {
        List<MethodSpec> fieldMethods = new ArrayList<>();
        if (fieldElement.getKind().equals(ElementKind.FIELD) && fieldElement.getAnnotation(JoIgnore.class) == null) {
            if (!currentIdElements.contains(fieldElement)) {
                fieldMethodBuilder.addFieldRelatedMethods(fieldElement);
            }
            addColumnField(fieldElement);
            if (fieldElement.asType().getKind() == TypeKind.DECLARED) {
                joinMethodBuilder.buildGetJoin(fieldElement);
            }
        }
        currentClassBuilder.addMethods(fieldMethods);
    }

    private void addColumnField(Element fieldElement) {
        String columnFieldValue = String.valueOf(fieldElement.getSimpleName());
        String columnFieldName = "COLUMN_" + camelToSnake(columnFieldValue).toUpperCase();
        currentClassBuilder.addField(
                FieldSpec.builder(TypeName.get(String.class), columnFieldName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$S", columnFieldValue)
                        .build());
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
