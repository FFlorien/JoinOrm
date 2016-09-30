
package be.florien.joinorm.architecture;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import be.florien.joinorm.primitivefield.BooleanField;
import be.florien.joinorm.primitivefield.DoubleField;
import be.florien.joinorm.primitivefield.IntField;
import be.florien.joinorm.primitivefield.NullField;
import be.florien.joinorm.primitivefield.StringField;

/**
 * {@link be.florien.joinorm.architecture.DBData DBData} representing the requests concerning a table in the database. An id referring to
 * this table as a foreign key can be substituted in the model object by the Object constructed by this DBTable. This class provide methods to:
 * <ul>
 * <li>handle the selection of fields</li>
 * <li>handle the join of this table with all queried tables</li>
 * <li>convert the information provided by the database into the object's fields. Note that these fields could be a single Object or a List of said
 * Object.</li>
 * </ul>
 * Each implementation class will provide methods to:
 * <ul>
 * <li>select the fields to be present in the resulting object</li>
 * <li>join the possible tables referenced to this one, preferably using
 * {@link be.florien.joinorm.architecture.DBTable#getJoinOnId(DBTable, boolean, String...) getJoinOnId} or
 * {@link be.florien.joinorm.architecture.DBTable#getJoinOnRef(DBTable, boolean, String...) getJoinOnRef}</li>
 * </ul>
 * <strong>PLEASE BE CAREFUL</strong> The POJO T, result of the conversion, must have its fields'names corresponding exactly to one of the following:
 * <ul>
 * <li>the names of the table field
 * <li>the other table's name if the object's field is another POJO created from database
 * <li>the alias specified for this parser by {@link be.florien.joinorm.architecture.DBTable#setAlias(String) setAlias}
 * </ul>
 *
 * @param <T> POJO representing the table, which will get the info from the database at the end of the parsing
 * @author Florien Flament
 */
public abstract class DBTable<T> extends DBData<T> {

    /*
     * FIELDS
     */

    private List<DBTable<?>> mDBTableQueryList = new ArrayList<>();
    private List<DBPrimitiveField<?>> mDBPrimitiveQueryList = new ArrayList<>();
    private List<DBID> mDeleteIdsList = new ArrayList<>();
    private List<String> mDBTableNameWriteList = new ArrayList<>();
    private List<String> mDBTableValueRefWriteList = new ArrayList<>();
    private List<DBTable<?>> mDBTableWriteList = new ArrayList<>();
    private List<DBPrimitiveField<?>> mDBPrimitiveWriteList = new ArrayList<>();
    private List<WhereStatement> mWhereSet = new ArrayList<>();
    private final String mTableName;
    protected final Class<T> mClass;

    //todo getId return a list of id, and the rest is responsible to get it right ?
    //TODO Precision and handling of joinTable ? (table_B that consist of table_A_id and table_C_id)
    private int mInitRowPosition;
    private int mRedundantRows;

    private int mNumberOfColumnQueried = -1;
    private List<T> mResultList = new ArrayList<>();
    private DBID mIds = new DBID();
    private boolean mIsANewObject = true;
    private boolean mIsGonnaBeRedundant = false;
    private boolean mIsSubTableFinished;
    private T mObjectToWrite;
    private int mIdNumber;

    /*
     * CONSTRUCTOR
     */

    /**
     * Constructs a new DBTable. Implementation of this class should do a no-parameters constructor calling this constructor.
     *
     * @param tableName Name of the table as in the database
     * @param myClass   Class of the result POJO
     */
    protected DBTable(String tableName, Class<T> myClass) {
        mTableName = tableName;
        mDataName = tableName;
        mClass = myClass;
        try {
            mObjectToWrite = mClass.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        reset();
    }

    /**
     * Set an alias for this table. Said alias could be use in case where:
     * <ul>
     * <li>a POJO contains more than one occurrence of another table's POJO
     * <li>the table is referenced multiple times in the JOIN statement, creating confusion for the request
     * </ul>
     *
     * @param aliasName The alias that will be set for the table in the request AND used to retrieve the POJO's field to assign the value
     */
    public void setAlias(String aliasName) {
        mDataName = aliasName;
    }

    /*
     * DBDATA HANDLING
     */

    /**
     * Add the ID to the query. Implementation of this class must use {@link DBTable#selectId(String...)}.
     *
     * @return this instance of DBTable
     */
    public abstract DBTable<T> selectId();

    /**
     * Add the ID to the query. This implementation make sure that the ID can be found anytime during extraction of the datas.
     *
     * @param columnNames The names of the field as in the database's table
     */
    protected void selectId(String... columnNames) {
        mIdNumber = columnNames.length;
        for (int position = 0; position < columnNames.length; position++) {
            String columnName = columnNames[position];
            IntField intField = new IntField(columnName);
            mDBPrimitiveQueryList.remove(intField);
            mDBPrimitiveQueryList.add(position, intField);
        }
    }

    /**
     * Add a simple String to the query.
     *
     * @param columnName the field's name as in the database's table
     */
    protected void selectString(String columnName) {
        StringField stringField = new StringField(columnName);
        mDBPrimitiveQueryList.remove(stringField);
        mDBPrimitiveQueryList.add(stringField);

    }

    /**
     * Add a simple int to the query.
     *
     * @param columnName the field's name as in the database's table
     */
    protected void selectInt(String columnName) {
        IntField intField = new IntField(columnName);
        mDBPrimitiveQueryList.remove(intField);
        mDBPrimitiveQueryList.add(intField);
    }

    /**
     * Add a simple boolean to the query.
     *
     * @param columnName the field's name as in the database's table
     */
    protected void selectBoolean(String columnName) {
        BooleanField booleanField = new BooleanField(columnName);
        mDBPrimitiveQueryList.remove(booleanField);
        mDBPrimitiveQueryList.add(booleanField);
    }

    /**
     * Add a simple double to the query.
     *
     * @param columnName the field's name as in the database's table
     */
    protected void selectDouble(String columnName) {
        DoubleField doubleField = new DoubleField(columnName);
        mDBPrimitiveQueryList.remove(doubleField);
        mDBPrimitiveQueryList.add(doubleField);
    }

    /**
     * Add the table represented by tableField to the query.
     *
     * @param tableField A representation of the table to query.
     */
    protected void selectTable(DBTable<?> tableField) {
        mDBTableQueryList.remove(tableField);
        selectId();
        mDBTableQueryList.add(tableField);

    }

    /*
     * DELETE HANDLING
     */

    /**
     * Add an ID (or composite) to the list of IDs to delete from the DataBase
     *
     * @param ids The ids of the object to delete
     */
    protected void deleteId(int... ids) {
        DBID dbId = new DBID(ids);
        mDeleteIdsList.remove(dbId);
        mDeleteIdsList.add(dbId);
    }

    /**
     * Create and construct a list of {@link be.florien.joinorm.architecture.DBDelete DBDelete}
     *
     * @return A list of DBDelete
     */
    public List<DBDelete> getDelete() {
        List<DBDelete> deletes = new ArrayList<>();
        for (DBID id : mDeleteIdsList) {
            List<String> idNames = getCompleteId();
            deletes.add(new DBDelete(mDataName, idNames, id.getIds()));
        }
        return deletes;
    }

    /*
     * WRITING HANDLING
     */

    /**
     * Add the value to be written in columnName
     *
     * @param columnName Name of the column corresponding to the value
     * @param value      The value for the object at corresponding columnName
     */
    @SuppressWarnings("unused")
    protected void writeString(String columnName, String value) {
        StringField stringField = new StringField(columnName);
        mDBPrimitiveWriteList.remove(stringField);
        mDBPrimitiveWriteList.add(stringField);
        try {
            getFieldToSet(columnName).set(mObjectToWrite, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a null value to be written
     *
     * @param columnName the field's name as in the database's table
     */
    @SuppressWarnings("unused")
    protected void writeNull(String columnName) {
        NullField nullField = new NullField(columnName);
        mDBPrimitiveWriteList.remove(nullField);
        mDBPrimitiveWriteList.add(nullField);
    }

    /**
     * Add a boolean value to be written
     *
     * @param columnName the field's name as in the database's table
     */
    @SuppressWarnings("unused")
    protected void writeBoolean(String columnName, boolean bool) {
        BooleanField boolField = new BooleanField(columnName);
        mDBPrimitiveWriteList.remove(boolField);
        mDBPrimitiveWriteList.add(boolField);
        try {
            getFieldToSet(columnName).set(mObjectToWrite, bool);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a int value to be written
     *
     * @param columnName the field's name as in the database's table
     */
    @SuppressWarnings("unused")
    protected void writeInt(String columnName, int integer) {
        IntField intField = new IntField(columnName);
        mDBPrimitiveWriteList.remove(intField);
        mDBPrimitiveWriteList.add(intField);
        try {
            getFieldToSet(columnName).set(mObjectToWrite, integer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a double value to be written
     *
     * @param columnName the field's name as in the database's table
     */
    @SuppressWarnings("unused")
    protected void writeDouble(String columnName, double doubleValue) {
        DoubleField doubleField = new DoubleField(columnName);
        mDBPrimitiveWriteList.remove(doubleField);
        mDBPrimitiveWriteList.add(doubleField);
        try {
            getFieldToSet(columnName).set(mObjectToWrite, doubleValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add the table represented by tableField to the query.
     *
     * @param tableField A representation of the table to query.
     */
    @SuppressWarnings("unused")
    protected void writeTable(DBTable<?> tableField, String tableRef, String tableRefValue, Object pojoToAssign) {
        mDBTableWriteList.remove(tableField);
        mDBTableWriteList.add(tableField);
        mDBTableNameWriteList.remove(tableRef);
        mDBTableNameWriteList.add(tableRef);
        mDBTableValueRefWriteList.remove(tableRefValue);
        mDBTableValueRefWriteList.add(tableRefValue);
        try {
            getFieldToSet(tableField).set(mObjectToWrite, pojoToAssign);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Populate a list of DBWrite with the data asked for writing.
     *
     * @param writes    The list of DBWrite to populate.
     * @param reference If given the name of the id field, will return it's value. // todo I don't remember if this is correct, also, multiple ids.
     * @return The id if the name is provided, 0 otherwise.
     */
    public int getValuesToWrite(List<DBWrite> writes, String reference) {
        int referenceId = 0;
        try {
            ContentValues value = new ContentValues();
            for (int i = 0; i < mDBTableNameWriteList.size(); i++) {
                value.put(mDBTableNameWriteList.get(i), mDBTableWriteList.get(i).getValuesToWrite(writes, mDBTableValueRefWriteList.get(i)));
            }
            for (DBPrimitiveField<?> field : mDBPrimitiveWriteList) {
                if (field instanceof StringField) {
                    value.put(field.mDataName, (String) getFieldToSet(field).get(mObjectToWrite));
                } else if (field instanceof DoubleField) {
                    value.put(field.mDataName, (Double) getFieldToSet(field).get(mObjectToWrite));
                } else if (field instanceof NullField) {
                    value.putNull(field.mDataName);
                } else if (field instanceof BooleanField) {
                    value.put(field.mDataName, (Boolean) getFieldToSet(field).get(mObjectToWrite));
                } else if (field instanceof IntField) {

                    Integer integer = (Integer) getFieldToSet(field).get(mObjectToWrite);
                    if (field.mDataName.equals(reference)) {
                        referenceId = integer;
                    }
                    value.put(field.mDataName, integer);
                }
            }
            writes.add(new DBWrite(mDataName, value));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return referenceId;
    }

    /*
     * PROJECTION HANDLING
     */

    /**
     * Construct and return an array of fields' names selected for the query. This method should be used uniquely if this DBTable is not a inner
     * DBData.
     *
     * @return an array of fields'names to be queried
     */
    public String[] getProjection() {
        List<String> buildSelect = buildProjection("");
        String[] projection = new String[buildSelect.size()];
        return buildSelect.toArray(projection);
    }

    @Override
    protected List<String> buildProjection(String tableName) {
        List<String> projection = new ArrayList<>();
        for (DBData<?> fieldToSelect : mDBPrimitiveQueryList) {
            projection.addAll(fieldToSelect.buildProjection(mDataName));
        }
        for (DBData<?> fieldToSelect : mDBTableQueryList) {
            projection.addAll(fieldToSelect.buildProjection(mDataName));
        }
        return projection;
    }

    /**
     * Used to know how many columns has been read by this parser
     */
    private int getNumberOfColumnsQueried() {
        if (mNumberOfColumnQueried == -1) {
            mNumberOfColumnQueried = mDBPrimitiveQueryList.size();
            for (DBTable<?> field : mDBTableQueryList) {
                mNumberOfColumnQueried += field.getNumberOfColumnsQueried();
            }
        }
        return mNumberOfColumnQueried;
    }

    /*
     * DB TABLES AND JOIN HANDLING
     */

    /**
     * Return the column name for this table id without the table name or its alias
     *
     * @return The column name for this table id
     */
    protected abstract List<String> getId();

    /**
     * todo
     * @return
     */
    protected List<String> getCompleteId() {
        List<String> ids = new ArrayList<>(getId().size());
        for (String idPart : getId()) {
            ids.add(mDataName + "." + idPart);
        }

        return ids;
    }

    /**
     * Construct and return the join statement to join this table and the one in parameter, preferably using
     * {@link be.florien.joinorm.architecture.DBTable#getJoinOnId(DBTable, boolean, String...) getJoinOnId},
     * {@link be.florien.joinorm.architecture.DBTable#getJoinOnRef(DBTable, boolean, String...) getJoinOnRef}. Constructing and returning the
     * join statement manually is also supported, but it should be kept in mind that this table could have been assigned to an alias, and thus
     * {@link be.florien.joinorm.architecture.DBData#getDataName() getDataName} should be used to get the table's name or alias.
     *
     * @param innerTable The DBTable representing a POJO which is present in this DBTable's POJO. Implementation of this method should test which
     *                   table it represent and return the according JOIN statement.
     * @return The JOIN statement
     */
    protected abstract String getJoinToInnerTable(DBTable<?> innerTable);

    /**
     * Construct and return a JOIN statement where innerTable contain a reference to this table ID
     *
     * @param innerTable    The table to join to this one
     * @param innerTableRef The columnName which refer to this table ID, without the table's name
     * @return The JOIN statement in the form "JOIN INNER_TABLE [AS INNER_TABLE_ALIAS] ON TABLE.ID = INNER_TABLE[_ALIAS].INNER_TABLE_REF"
     */
    protected String getJoinOnId(DBTable<?> innerTable, boolean isLeftJoin, String... innerTableRef) {
        return (isLeftJoin ? "LEFT " : "") + "JOIN " + innerTable.mTableName + (innerTable.mDataName.equals(mTableName) ? "" : " AS " + innerTable.mDataName)
                + getJoinConditionOnID(innerTable, innerTableRef);
    }

    /**
     * Construct and return a JOIN statement where this table contain a reference to innerTable ID
     *
     * @param innerTable   The table to join to this one
     * @param thisTableRef The columnName which refer to the innerTable ID, without the table's name
     * @return The JOIN statement in the form "JOIN INNER_TABLE [AS INNER_TABLE_ALIAS] ON TABLE.THIS_TABLE_REF = INNER_TABLE[_ALIAS].ID"
     */
    protected String getJoinOnRef(DBTable<?> innerTable, boolean isLeftJoin, String... thisTableRef) {
        return (isLeftJoin ? "LEFT " : "") + "JOIN " + innerTable.mTableName + (innerTable.mDataName.equals(mTableName) ? "" : " AS " + innerTable.mDataName)
                + getJoinConditionOnRef(innerTable, thisTableRef);
    }

    private String getJoinConditionOnID(DBTable<?> innerTable, String... innerTableRef) {//todo verify if all is same size
        String join = " ON ";
        List<String> ids = getCompleteId();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                join = join + " and ";
            }

            join = join + ids.get(i) + " = " + innerTable.mDataName + "." + innerTableRef[i];
        }

        return join;
    }

    private String getJoinConditionOnRef(DBTable<?> innerTable, String... thisTableRef) {//todo verify if all is same size
        String join = " ON ";
        List<String> ids = getId();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                join = join + " and ";
            }

            join = join + mDataName + "." + thisTableRef[i] + " = " + innerTable.mDataName + "." + innerTable.getId().get(i);
        }

        return join;
    }

    /**
     * Construct and return the JOIN statement for this table and all its inner tables. This method should be used uniquely if this DBTable is not a
     * inner DBData.
     *
     * @return The complete JOIN statement for the query
     */
    public String getJoinComplete() {
        return mTableName + (mDataName.equals(mTableName) ? "" : " AS " + mDataName) + getJoinsToAllTables();
    }

    private String getJoinsToAllTables() {
        String tables = "";
        for (DBTable<?> field : mDBTableQueryList) {
            tables = tables + " " + getJoinToInnerTable(field);
            tables = tables + " " + field.getJoinsToAllTables();
        }
        return tables;
    }

    /*
     * WHERE HANDLING
     */

    /**
     * Construct and return the where statement associated
     *
     * @return the where statement
     */
    public String getWhere() {
        String where = "";
        for (WhereStatement statement : mWhereSet) {
            if (!TextUtils.isEmpty(where)) {
                if (statement.isOr()) {
                    where += " OR ";
                } else {
                    where += " AND ";
                }
            } else {
                where += "(";
            }
            where += mDataName + "." + statement.getStatement();
        }
        if (!TextUtils.isEmpty(where)) {
            where += ")";
        }
        for (DBTable<?> field : mDBTableQueryList) {
            String toAdd = field.getWhere();
            if (!TextUtils.isEmpty(toAdd) && !TextUtils.isEmpty(where)) {
                where += " AND ";
            }
            where += toAdd;
        }
        return where;
    }

    /**
     * Add a statement to add to a collection of WhereStatement
     *
     * @param statement the where statement
     */
    public DBTable<T> addWhere(WhereStatement statement) {
        mWhereSet.add(statement);
        return this;
    }

    /*
     * ORDER BY HANDLING
     */

    /**
     * Return a list of fields'names from the database to order the query by.
     *
     * @return a list of fields'names
     */
    public String getOrderBy() {
        String orderby = getOrderByForThis();
        for (DBTable<?> fieldToSelect : mDBTableQueryList) {
            orderby += ", " + fieldToSelect.getOrderBy();
        }
        return orderby;
    }

    /**
     * Return a column name in the form "TABLE_ALIAS.COLUMN_NAME". Override this method if you want the sorting to be another column than the ID
     * (Default). But be careful as the datas from a single object must be in consecutive rows in the query result.
     *
     * @return A column name in the form "TABLE_ALIAS.COLUMN_NAME"
     */
    protected String getOrderByForThis() {
        String orderBy = "";
        List<String> completeId = getCompleteId();
        for (String idComplete : completeId) {
            if (!idComplete.equals(completeId.get(0))) {
                orderBy = orderBy + ", ";
            }
            orderBy = orderBy + idComplete;
        }
        return orderBy;
    }

    /*
     * DATA EXTRACTION
     */

    /**
     * Extract the datas from the Cursor in parameter and return a List of POJO filled with queried fields
     *
     * @param cursor The cursor which has received the datas from the database.
     * @return a List of POJO
     */
    public List<T> getResult(Cursor cursor) {
        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            initId(cursor, 0);
        } else {
            return new ArrayList<>();
        }
        while (!cursor.isAfterLast()) {
            if (compareIDs(cursor, 0)) {
                extractRowValue(cursor, 0);
                int rowToFinishParsing = getRowToFinishParsing();
                // Log.d("POKEMON", "Row to pass == " + rowToFinishParsing + " | number of parsed :" + mResultList.size());
                for (int rowToPass = rowToFinishParsing; rowToPass > 0 && !cursor.isAfterLast(); rowToPass--) {
                    cursor.moveToNext();
                }
            } else {
                setComplete();
                addResultToList();
                reset();
                initId(cursor, 0);
            }

        }
        setComplete();
        addResultToList();
        return getResultList();
    }

    private void initId(Cursor cursor, int column) {
        for (int offset = 0; offset < mIdNumber; offset++) {
            mIds.getIds().add(String.valueOf(cursor.getInt(column + offset)));
        }
    }

    private boolean compareIDs(Cursor cursor, int column) {
        if (mIds.getIds().size() < 1) {
            return true;
        }
        for (int offset = 0; offset < mIdNumber; offset++) {
            if (!mIds.getIds().get(offset).equals(String.valueOf(cursor.getInt(column + offset)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return the number of rows used for the making of this representation
     *
     * @return the number of rows used
     */
    private int getRowToFinishParsing() {
        int rows;
        if (!mIsGonnaBeRedundant) {
            rows = 1;
            for (DBTable<?> table : mDBTableQueryList) {
                int rowToFinishParsing = table.getRowToFinishParsing();
                rows = (rows >= rowToFinishParsing ? rows : rowToFinishParsing);
            }
        } else {
            rows = mRedundantRows;
        }
        return rows;
    }

    /**
     * Return the {@link java.lang.reflect.Field Field} which will be used by the parser to set the value to the correct filed in the POJO. Retrieve
     * said field by using the columnName or the alias is one is set. Override this method if you want the POJO's field name and the columnName/alias
     * to be different.
     *
     * @param fieldToSet Representation for retrieving the data from the database. Used to get the POJO field name
     * @return The Field to be set
     * @throws NoSuchFieldException
     */
    protected Field getFieldToSet(DBData<?> fieldToSet) throws NoSuchFieldException {
        return getFieldToSet(fieldToSet.mDataName);
    }

    /**
     * Return the {@link java.lang.reflect.Field Field} which will be used by the parser to set the value to the correct filed in the POJO. Retrieve
     * said field by using the field to set. Override this method if you want the POJO's field name and fieldToSet to be different.
     *
     * @param fieldToSet The POJO field name
     * @return The Field to be set
     * @throws NoSuchFieldException
     */
    private Field getFieldToSet(String fieldToSet) throws NoSuchFieldException {
        return mClass.getField(fieldToSet);
    }

    private boolean isAList(DBData<?> dbFieldToExtract) throws NoSuchFieldException, IllegalAccessException {
        Field field = getFieldToSet(dbFieldToExtract);
        Type genericType = field.getGenericType();
        return genericType instanceof ParameterizedType;
    }

    @Override
    protected void extractRowValue(Cursor cursor, int column) {
        try {
            if (mInitRowPosition == -1) {
                mInitRowPosition = cursor.getPosition();
            }
            mIsSubTableFinished = false;

            int currentColumn = column;

            if (mIsANewObject) {
                for (DBPrimitiveField<?> primitiveToExtract : mDBPrimitiveQueryList) {
                    primitiveToExtract.extractRowValue(cursor, currentColumn);
                    Field field = getFieldToSet(primitiveToExtract);
                    field.set(mCurrentObject, primitiveToExtract.getValue());
                    currentColumn++;
                }
                mIsANewObject = false;
            } else {
                currentColumn += mDBPrimitiveQueryList.size();
            }

            for (DBTable<?> tableToExtract : mDBTableQueryList) {
                if (mIsSubTableFinished && !tableToExtract.mIsGonnaBeRedundant) {
                    tableToExtract.setComplete();
                    tableToExtract.setIsGonnaBeRedundant(true, cursor.getPosition());
                    setValues(tableToExtract);
                    tableToExtract.reset();
                    tableToExtract.initId(cursor, currentColumn);
                    tableToExtract.extractRowValue(cursor, currentColumn);
                } else if (!tableToExtract.mIsGonnaBeRedundant) {
                    if (!tableToExtract.compareIDs(cursor, currentColumn)) {
                        tableToExtract.setComplete();
                        if (isAList(tableToExtract)) {
                            tableToExtract.addResultToList();
                        } else {
                            Field field = getFieldToSet(tableToExtract);
                            field.set(mCurrentObject, tableToExtract.getValue());
                        }
                        tableToExtract.reset();
                        mIsSubTableFinished = true;
                    }
                    tableToExtract.initId(cursor, currentColumn);
                    tableToExtract.extractRowValue(cursor, currentColumn);
                    mIsSubTableFinished = mIsSubTableFinished || tableToExtract.mIsSubTableFinished;
                }
                currentColumn += tableToExtract.getNumberOfColumnsQueried();
            }
        } catch (Exception ex) {
            throw new DBArchitectureException(ex);
        }
    }

    @Override
    protected void setComplete() {
        super.setComplete();

        try {
            for (DBTable<?> tableToExtract : mDBTableQueryList) {
                tableToExtract.setComplete();
                if (!tableToExtract.mIsGonnaBeRedundant) {
                    setValues(tableToExtract);
                }
                tableToExtract.setIsGonnaBeRedundant(false, 0);
                tableToExtract.reset();
                tableToExtract.resetList();
            }
        } catch (Exception ex) {
            throw new DBArchitectureException("Exception caught during the parsing of table " + mTableName + "(alias : " + mDataName + ")", ex);
        }
    }

    private void setIsGonnaBeRedundant(boolean isRedundant, int cursorPosition) {
        if (mIsGonnaBeRedundant == isRedundant) {
            return;
        }
        mIsGonnaBeRedundant = isRedundant;
        if (isRedundant) {
            mRedundantRows = cursorPosition - mInitRowPosition;
        } else {
            mRedundantRows = 0;
            for (DBTable<?> table : mDBTableQueryList) {
                table.setIsGonnaBeRedundant(false, cursorPosition);
            }
        }
    }

    @Override
    protected void reset() {
        super.reset();
        try {
            // Log.d("POKEMON", "mCurrentObject resetted: " + mTableName);
            mCurrentObject = mClass.newInstance();
            for (DBData<?> fieldToReset : mDBTableQueryList) {
                fieldToReset.reset();
            }
            mIds = new DBID();
            mIsANewObject = true;
            mIsSubTableFinished = false;
        } catch (Exception e) {
            throw new DBArchitectureException(e);
        }
    }

    private void resetList() {
        mResultList = new ArrayList<>();
        mInitRowPosition = -1;
    }

    private void setValues(DBTable<?> tableToExtract) throws NoSuchFieldException, IllegalAccessException {
        Field field = getFieldToSet(tableToExtract);
        if (isAList(tableToExtract)) {
            tableToExtract.addResultToList();
            field.set(mCurrentObject, tableToExtract.getResultList());
        } else {
            field.set(mCurrentObject, tableToExtract.getValue());
        }
    }

    private void addResultToList() {
        if (mIsComplete) {
            mResultList.add(mCurrentObject);
        }
    }

    private List<T> getResultList() {
        return mResultList;
    }

    /*
     * EQUALS
     */

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((mDataName == null) ? 0 : mDataName.hashCode());
        result = prime * result + ((mTableName == null) ? 0 : mTableName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        DBTable<?> other = (DBTable<?>) obj;
        if (mDataName == null) {
            if (other.mDataName != null)
                return false;
        } else if (!mDataName.equals(other.mDataName))
            return false;
        if (mTableName == null) {
            if (other.mTableName != null)
                return false;
        } else if (!mTableName.equals(other.mTableName))
            return false;
        return true;
    }

}
