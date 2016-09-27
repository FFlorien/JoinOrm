package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import be.florien.joinorm.annotation.JoTable;

class ProcessingUtil {

    static TypeName getDBTableTypeName(Element fieldElement, String tablePackageName) {
        TypeName className = null;
        DeclaredType fieldDeclaredType = (DeclaredType) fieldElement.asType();
        TypeElement fieldTypeElement = (TypeElement) fieldDeclaredType.asElement();
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
            className = ClassName.get(tablePackageName, fieldTypeElement.getSimpleName() + "Table");
        } else if (parameterDeclaredType != null && parameterDeclaredType.asElement().getAnnotation(JoTable.class) != null) {
            className = ClassName.get(tablePackageName, parameterDeclaredType.asElement().getSimpleName() + "Table");
        }
        return className;
    }

    static DeclaredType getTypeParameterDeclaredType(DeclaredType declaredType) {
        DeclaredType parameterDeclaredType = null;
        TypeMirror parameterType = getParameterType(declaredType);
        if (parameterType != null && parameterType.getKind() == TypeKind.DECLARED) {
            parameterDeclaredType = (DeclaredType) parameterType;
        }
        return parameterDeclaredType;
    }

    static TypeMirror getParameterType(DeclaredType declaredType) {
        TypeMirror parameterType = null;
        if (declaredType.getTypeArguments().size() == 1) {
            parameterType = declaredType.getTypeArguments().get(0);
        }
        return parameterType;
    }
}
