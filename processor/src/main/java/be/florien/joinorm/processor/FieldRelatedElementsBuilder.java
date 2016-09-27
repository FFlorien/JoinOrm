package be.florien.joinorm.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;

import be.florien.joinorm.annotation.JoId;
import be.florien.joinorm.annotation.JoJoin;
import be.florien.joinorm.architecture.DBTable;

class FieldRelatedElementsBuilder {

    private boolean isGeneratingSelect;
    private boolean isGeneratingWrite;
    private String tablePackageName;
    private TypeName tableClassName;
    private List<MethodSpec> methods;
    private List<FieldSpec> fields;

    FieldRelatedElementsBuilder(boolean isGeneratingSelect, boolean isGeneratingWrite, String tablePackageName, TypeName tableClassName) {
        this.isGeneratingSelect = isGeneratingSelect;
        this.isGeneratingWrite = isGeneratingWrite;
        this.tablePackageName = tablePackageName;
        this.tableClassName = tableClassName;
        this.methods = new ArrayList<>();
        this.fields = new ArrayList<>();
    }

    void addFieldRelatedElements(Element fieldElement) {
        MethodSpec selectMethod = null;
        MethodSpec writeMethod = null;
        boolean isId = (fieldElement.getAnnotation(JoId.class) != null);

        if (isGeneratingSelect) {
            selectMethod = getSelectMethod(fieldElement, isId);
            if (selectMethod != null) {
                methods.add(selectMethod);
            }
        }

        if (isId) {
            methods.add(MethodSpec.methodBuilder("getId")
                    .returns(String.class)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addStatement("return $S", fieldElement.getSimpleName()) //todo change here for when dual id is correctly implemented (triple id ? quadruple id ??? <not defined number> id ??!??!?)
                    .build());
        }

        if (isGeneratingWrite) {
            writeMethod = getWriteMethod(fieldElement);
            if (writeMethod != null) {
                methods.add(writeMethod);
            }
        }

        if ((isGeneratingSelect && selectMethod != null) || (isGeneratingWrite && writeMethod != null)) {
            fields.add(addColumnField(fieldElement));
        }
    }

    void addSpecToBuilder(TypeSpec.Builder classBuilder) {
        classBuilder.addMethods(methods);
        classBuilder.addFields(fields);
    }

    private MethodSpec getSelectMethod(Element fieldElement, boolean isId) {
        String selectTypeName = getTypeName(fieldElement.asType());
        String statementFormat = "select$L($S)";
        String parameterName = fieldElement.getSimpleName().toString();
        String selectMethodName = snakeToCamel(parameterName);
        MethodSpec.Builder builder;
        if (isId) {
            selectTypeName = "Id";
            builder = MethodSpec.methodBuilder("selectId");
            builder.addAnnotation(Override.class);
        } else {
            selectMethodName = "select" + selectMethodName.substring(0, 1).toUpperCase() + selectMethodName.substring(1);
            builder = MethodSpec.methodBuilder(selectMethodName);

        }

        if (selectTypeName.equals("declared")) {
            DeclaredType fieldDeclaredType = (DeclaredType) fieldElement.asType();
            DeclaredType typeParameterDeclaredType = ProcessingUtil.getTypeParameterDeclaredType(fieldDeclaredType);
            if (ClassName.get(fieldDeclaredType).equals(ClassName.get(String.class)) ||
                    (typeParameterDeclaredType != null &&
                            ClassName.get(typeParameterDeclaredType).equals(ClassName.get(String.class)))) {
                selectTypeName = "String";
            } else {
                JoJoin fieldJoinAnnotation = fieldElement.getAnnotation(JoJoin.class);
                TypeMirror parameterType = ProcessingUtil.getParameterType(fieldDeclaredType);
                String typeName = null;
                if (parameterType != null) {
                    typeName = getTypeName(parameterType);
                }
                if (typeName != null && !typeName.equals("declared") && !typeName.equals("lolType")) {//todo verify if is Collection
                    selectTypeName = typeName;
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

                        if (!fieldJoinAnnotation.getAlias().equals(JoJoin.IGNORE)) {
                            builder.addStatement("$L.setAlias($S)", parameterName, fieldJoinAnnotation.getAlias());
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }

        return builder.returns(tableClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement(statementFormat, selectTypeName, parameterName)
                .addStatement("return this")
                .build();
    }

    private MethodSpec getWriteMethod(Element fieldElement) {
        String writeTypeName = getTypeName(fieldElement.asType());
        TypeMirror typeMirror = fieldElement.asType();
        ParameterSpec value = ParameterSpec.builder(TypeName.get(typeMirror), "value").build();

        if (writeTypeName.equals("declared")) {
            DeclaredType fieldDeclaredType = (DeclaredType) typeMirror;
            JoJoin fieldJoinAnnotation = fieldElement.getAnnotation(JoJoin.class);
            if (ClassName.get(fieldDeclaredType).equals(ClassName.get(String.class))) {
                writeTypeName = "String";
            } else if (fieldJoinAnnotation != null) {
                TypeName className;
                if (isJoinCustomClassDefined(fieldJoinAnnotation)) {
                    className = ClassName.get(getTableClass(fieldJoinAnnotation));
                } else {
                    className = ProcessingUtil.getDBTableTypeName(fieldElement, tablePackageName);
                }

                if (className != null) {
                    writeTypeName = "Table";
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        String selectableSimpleName = fieldElement.getSimpleName().toString();
        selectableSimpleName = selectableSimpleName.substring(0, 1).toUpperCase() + selectableSimpleName.substring(1);

        return MethodSpec.methodBuilder("write" + selectableSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(value)
                .addStatement("write$L($S, $L)", writeTypeName, fieldElement.getSimpleName(), "value")
                .build();
    }

    private FieldSpec addColumnField(Element fieldElement) {
        String columnFieldValue = String.valueOf(fieldElement.getSimpleName());
        String columnFieldName = "COLUMN_" + camelToSnake(columnFieldValue).toUpperCase();
        return FieldSpec.builder(TypeName.get(String.class), columnFieldName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", columnFieldValue)
                .build();
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
