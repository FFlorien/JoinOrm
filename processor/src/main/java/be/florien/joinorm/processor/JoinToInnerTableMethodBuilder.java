package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.WildcardTypeName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;

import be.florien.joinorm.annotation.JoCustomJoin;
import be.florien.joinorm.annotation.JoJoin;
import be.florien.joinorm.architecture.DBTable;

class JoinToInnerTableMethodBuilder {
    private boolean isStartOfJoin;
    private MethodSpec.Builder currentJoinBuilder;
    private String packageName;

    JoinToInnerTableMethodBuilder(String packageName) {
        this.packageName = packageName;
        ClassName dbTableClassName = ClassName.get(DBTable.class);
        ParameterizedTypeName wildcardDBTableClassName = ParameterizedTypeName.get(dbTableClassName, WildcardTypeName.subtypeOf(TypeName.OBJECT));
        ParameterSpec wildcardDBTableParameter = ParameterSpec.builder(wildcardDBTableClassName, "innerTable").build();
        isStartOfJoin = true;
        currentJoinBuilder = MethodSpec.methodBuilder("getJoinToInnerTable")
                .addParameter(wildcardDBTableParameter)
                .addAnnotation(Override.class)
                .returns(TypeName.get(String.class))
                .addModifiers(Modifier.PROTECTED);
    }

    void buildGetJoin(Element fieldElement) {
        JoJoin joinAnnotation = fieldElement.getAnnotation(JoJoin.class);

        if (joinAnnotation != null) {
            if (isJoinCustomClassDefined(joinAnnotation)) {
                DeclaredType customTableClassType = (DeclaredType) getTableClass(joinAnnotation);
                TypeElement customTableClassElement = (TypeElement) customTableClassType.asElement();

                for (Element customTableClassEnclosedElement : customTableClassElement.getEnclosedElements()) {
                    JoCustomJoin customJoinAnnotation = customTableClassEnclosedElement.getAnnotation(JoCustomJoin.class);
                    if (customTableClassEnclosedElement.getKind() == ElementKind.METHOD && customJoinAnnotation != null) {
                        newConditionForJoin(ClassName.get(customTableClassType));
                        currentJoinBuilder.addStatement("return (($L) $L).$L($L)",
                                customTableClassElement.getSimpleName(),
                                "innerTable",
                                customTableClassEnclosedElement.getSimpleName(),
                                customJoinAnnotation.getParams());
                    }
                }
            } else if (!joinAnnotation.getTableRef().equals(JoJoin.IGNORE)){
                TypeName className = ProcessingUtil.getDBTableTypeName(fieldElement, packageName);
                if (className != null) {
                    newConditionForJoin(className);
                    currentJoinBuilder.addStatement("return getJoinOn$L(innerTable, $S, $L)",
                            joinAnnotation.isReferenceJoin() ? "Ref" : "Id", joinAnnotation.getTableRef(),
                            joinAnnotation.isLeftJoin());
                }
            }
        }
    }

    MethodSpec getJoinToInnerTableMethod() {

        if (!isStartOfJoin) {
            currentJoinBuilder.endControlFlow();
        }
        return currentJoinBuilder.addStatement("return \"\"").build();
    }

    private boolean isJoinCustomClassDefined(JoJoin fieldJoinAnnotation) {
        return !ClassName.get(getTableClass(fieldJoinAnnotation)).equals(ClassName.get(DBTable.class));
    }

    private TypeMirror getTableClass(JoJoin annotation) {
        try {
            annotation.getTableClass();
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
        return (TypeMirror) ClassName.get(DBTable.class).box();
    }

    private void newConditionForJoin(TypeName customClassName) {
        if (isStartOfJoin) {
            currentJoinBuilder.beginControlFlow("if (innerTable instanceof $T)", customClassName);
            isStartOfJoin = false;
        } else {
            currentJoinBuilder.nextControlFlow("else if (innerTable instanceof $T)", customClassName);
        }
    }
}
