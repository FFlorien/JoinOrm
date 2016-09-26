package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;

import be.florien.joinorm.annotation.JoJoin;
import be.florien.joinorm.annotation.JoTable;
import be.florien.joinorm.architecture.DBTable;

class FieldRelatedMethodsBuilder {

    private boolean isGeneratingSelect;
    private boolean isGeneratingWrite;
    private String tablePackageName;
    private TypeName tableClassName;
    private List<MethodSpec> methods;

    FieldRelatedMethodsBuilder(boolean isGeneratingSelect, boolean isGeneratingWrite, String tablePackageName, TypeName tableClassName) {
        this.isGeneratingSelect = isGeneratingSelect;
        this.isGeneratingWrite = isGeneratingWrite;
        this.tablePackageName = tablePackageName;
        this.tableClassName = tableClassName;
        this.methods = new ArrayList<>();
    }

    void addFieldRelatedMethods(Element fieldElement) {
        if (isGeneratingSelect) {
            MethodSpec method = getSelectMethod(fieldElement);
            if (method != null) {
                methods.add(method);
            }
        }
        if (isGeneratingWrite) {
            MethodSpec method = getWriteMethod(fieldElement);
            if (method != null) {
                methods.add(method);
            }
        }
    }

    List<MethodSpec> getMethods() {
        return methods;
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
            if (ClassName.get(fieldDeclaredType).equals(ClassName.get(String.class))) {
                selectTypeName = "String";
            } else if (fieldJoinAnnotation != null) {
                TypeName className;
                if (isJoinCustomClassDefined(fieldJoinAnnotation)) {
                    className = ClassName.get(getTableClass(fieldJoinAnnotation));
                } else {
                    className = ProcessingUtil.getDBTableTypeName(fieldElement, tablePackageName);
                }

                if (className != null) {
                    selectTypeName = "Table";
                    statementFormat = "select$L($L)";
                    parameterName = snakeToCamel(parameterName);
                    builder.addParameter(ParameterSpec.builder(className, parameterName).build());
                } else {
                    return null;
                }
            } else {
                return null;
            }

        }

        return builder.returns(tableClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement(statementFormat, selectTypeName, parameterName)
                .addStatement("return this")
                .build();
    }

    private boolean isJoinCustomClassDefined(JoJoin fieldJoinAnnotation) {
        return !ClassName.get(getTableClass(fieldJoinAnnotation)).equals(ClassName.get(DBTable.class));
    }

    private MethodSpec getWriteMethod(Element selectable) {
        String typeName = getTypeName(selectable);
        TypeMirror typeMirror = selectable.asType();
        ParameterSpec value = ParameterSpec.builder(TypeName.get(typeMirror), "value").build();
        if (typeName.equals("declared")) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            TypeElement typeElement = (TypeElement) declaredType.asElement();
            if (typeElement.getAnnotation(JoTable.class) != null) {
                return null; //todo use ProcessorUtil to get parameter type
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
}
