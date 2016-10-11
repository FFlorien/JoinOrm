package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import be.florien.joinorm.annotation.JoId;
import be.florien.joinorm.annotation.JoJoin;
import be.florien.joinorm.architecture.DBTable;

class FieldRelatedElementsBuilder {
    /**
     * Fields
     */

    private static final String DECLARED_TYPE_NAME = "declared";
    private static final String ERROR_TYPE = "error_type";

    private boolean isGeneratingSelect;
    private boolean isGeneratingWrite;
    private String tablePackageName;
    private TypeName tableClassName;
    private Messager messager;
    private List<MethodSpec> methods;
    private List<FieldSpec> fields;
    private List<Element> ids;
    private Element fieldElement;
    private MethodSpec.Builder getFieldValueMethod;
    private MethodSpec.Builder setFieldValueMethod;
    private boolean isStartOfGet = true;
    private boolean isStartOfSet = true;

    /**
     * Constructor
     */

    FieldRelatedElementsBuilder(boolean isGeneratingSelect, boolean isGeneratingWrite, String tablePackageName, TypeName tableClassName, Messager messager) {
        this.isGeneratingSelect = isGeneratingSelect;
        this.isGeneratingWrite = isGeneratingWrite;
        this.tablePackageName = tablePackageName;
        this.tableClassName = tableClassName;
        this.messager = messager;
        this.methods = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.ids = new ArrayList<>();
        this.getFieldValueMethod = MethodSpec.methodBuilder("getFieldValue").addAnnotation(Override.class).returns(ClassName.get(Object.class)).addParameter(String.class, "fieldName")
                .addModifiers(Modifier.PROTECTED);
        this.setFieldValueMethod = MethodSpec.methodBuilder("setFieldValue").addAnnotation(Override.class).addParameter(String.class, "fieldName").addParameter(TypeName.OBJECT, "value")
                .addModifiers(Modifier.PROTECTED);
    }

    /**
     * Accessible methods
     */

    void addFieldRelatedElements(Element fieldElement) {
        this.fieldElement = fieldElement;
        TypeMirror typeMirror = fieldElement.asType();
        boolean isId = (fieldElement.getAnnotation(JoId.class) != null);
        String dbTypeName = getTypeName(typeMirror);
        String selectStatementFormat = "select$L($S)";
        String fieldName = fieldElement.getSimpleName().toString();
        String parameterName = fieldName;
        String selectMethodName = snakeToCamel(parameterName);
        selectMethodName = "select" + selectMethodName.substring(0, 1).toUpperCase() + selectMethodName.substring(1);
        MethodSpec.Builder selectBuilder = MethodSpec.methodBuilder(selectMethodName);
        TypeName className = null;
        DeclaredType fieldDeclaredType = null;

        if (isId) {
            ids.add(fieldElement);
        }

        if (dbTypeName.equals(DECLARED_TYPE_NAME)) {
            fieldDeclaredType = (DeclaredType) typeMirror;
            DeclaredType fieldParameterDeclaredType = ProcessingUtil.getTypeParameterDeclaredType(fieldDeclaredType);
            JoJoin fieldJoinAnnotation = fieldElement.getAnnotation(JoJoin.class);

            if (ClassName.get(fieldDeclaredType).equals(ClassName.get(String.class)) ||
                    (fieldParameterDeclaredType != null && ClassName.get(fieldParameterDeclaredType).equals(ClassName.get(String.class)))) {
                dbTypeName = "String";
            } else if (fieldJoinAnnotation != null) {
                if (isJoinCustomClassDefined(fieldJoinAnnotation)) {
                    className = ClassName.get(getTableClass(fieldJoinAnnotation));
                } else {
                    className = ProcessingUtil.getDBTableTypeName(fieldElement, tablePackageName);
                }

                if (className != null) {
                    dbTypeName = "Table";
                    selectStatementFormat = "select$L($L)";
                    parameterName = snakeToCamel(parameterName);
                    selectBuilder.addParameter(ParameterSpec.builder(className, parameterName).build());

                    if (!fieldJoinAnnotation.getAlias().equals(JoJoin.IGNORE)) {
                        selectBuilder.addStatement("$L.setAlias($S)", parameterName, fieldJoinAnnotation.getAlias());
                    }
                } else {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Element annotated with JoJoin is not a DBTable", fieldElement);
                    return;
                }
            } else {
                messager.printMessage(Diagnostic.Kind.WARNING, "Element class is not comprehensible for the API", fieldElement);
                return;
            }
        }

        newConditionForGet(fieldName);
        getFieldValueMethod.addStatement("return currentObject.$L", fieldName);
        newConditionForSet(fieldName);
        if (fieldDeclaredType != null) {
            setFieldValueMethod.addStatement("currentObject.$L = ($L) value", fieldName, fieldDeclaredType);
        } else {
            setFieldValueMethod.addStatement("currentObject.$L = ($L) value", fieldName, dbTypeName.toLowerCase());

        }

        boolean shouldWriteColumnName = isId;
        ParameterSpec value = ParameterSpec.builder(TypeName.get(typeMirror), "value").build();

        if (isGeneratingWrite) {
            methods.add(MethodSpec.methodBuilder("write" + parameterName.substring(0, 1).toUpperCase() + parameterName.substring(1))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(value)
                    .addStatement("write$L($S, $L)", dbTypeName, fieldElement.getSimpleName(), "value")
                    .build());
            shouldWriteColumnName = true;
        }

        if (isGeneratingSelect && !isId) {
            methods.add(selectBuilder.returns(tableClassName)
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement(selectStatementFormat, dbTypeName, parameterName)
                    .addStatement("return this")
                    .build());
            shouldWriteColumnName = true;
        }

        if (shouldWriteColumnName) {
            addColumnField(fieldElement);
        }
    }

    void addSpecToBuilder(TypeSpec.Builder classBuilder) {
        classBuilder.addMethods(getIdMethods());
        classBuilder.addMethod(getGetFieldValueMethod());
        classBuilder.addMethod(getSetFieldValueMethod());
        classBuilder.addMethods(methods);
        classBuilder.addFields(fields);
    }

    private List<MethodSpec> getIdMethods() {
        List<MethodSpec> idMethods = new ArrayList<>(2);
        ParameterizedTypeName listTypeName = ParameterizedTypeName.get(List.class, String.class);
        String getIdParameter = "";
        for (Element idElement : ids) {
            if (!idElement.equals(ids.get(0))) {
                getIdParameter = getIdParameter + ", ";
            }

            getIdParameter = getIdParameter + "\"" + idElement.getSimpleName() + "\"";
        }
        idMethods.add(MethodSpec.methodBuilder("getId")
                .addModifiers(Modifier.PUBLIC)
                .returns(listTypeName)
                .addAnnotation(Override.class)
                .addStatement("return $T.asList($L)", Arrays.class, getIdParameter).build());
        idMethods.add(MethodSpec.methodBuilder("selectId")
                .addModifiers(Modifier.PUBLIC)
                .returns(tableClassName)
                .addAnnotation(Override.class)
                .addStatement("selectId($L)", getIdParameter)
                .addStatement("return this")
                .build());

        return idMethods;
    }

    /**
     * Get Methods
     */

    private void addColumnField(Element fieldElement) {
        String columnFieldValue = String.valueOf(fieldElement.getSimpleName());
        String columnFieldName = "COLUMN_" + camelToSnake(columnFieldValue).toUpperCase();
        fields.add(FieldSpec.builder(TypeName.get(String.class), columnFieldName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", columnFieldValue)
                .build());
    }

    /**
     * Utility methods
     */

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

    private String getTypeName(TypeMirror typeMirror) {
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
                return DECLARED_TYPE_NAME;
            default:
                messager.printMessage(Diagnostic.Kind.ERROR, "Cannot determine the type of element", fieldElement);
                return ERROR_TYPE;

        }
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

    private String snakeToCamel(String dataFieldName) {
        String columnFieldName = dataFieldName;

        for (int i = dataFieldName.length() - 1; i >= 0; i--) {
            if (dataFieldName.charAt(i) == '_') {
                columnFieldName = dataFieldName.substring(0, i) + columnFieldName.substring(i + 1, i + 2).toUpperCase() + columnFieldName.substring(i + 2, columnFieldName.length());
            }
        }

        return columnFieldName;
    }

    private MethodSpec getGetFieldValueMethod() {
        if (!isStartOfGet) {
            getFieldValueMethod.endControlFlow();
        }

        return getFieldValueMethod.addStatement("return \"\"").build();
    }

    private MethodSpec getSetFieldValueMethod() {
        if (!isStartOfSet) {
            setFieldValueMethod.endControlFlow();
        }

        return setFieldValueMethod.build();
    }

    private void newConditionForGet(String elementName) {
        if (isStartOfGet) {
            getFieldValueMethod.beginControlFlow("if ($S.equals(fieldName))", elementName);
            isStartOfGet = false;
        } else {
            getFieldValueMethod.nextControlFlow("else if ($S.equals(fieldName))", elementName);
        }
    }

    private void newConditionForSet(String elementName) {
        if (isStartOfSet) {
            setFieldValueMethod.beginControlFlow("if ($S.equals(fieldName))", elementName);
            isStartOfSet = false;
        } else {
            setFieldValueMethod.nextControlFlow("else if ($S.equals(fieldName))", elementName);
        }
    }
}
