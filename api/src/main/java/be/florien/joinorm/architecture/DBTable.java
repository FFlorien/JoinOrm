
package be.florien.joinorm.architecture;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
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
 * <strong>PLEASE BE CAREFUL</strong> The model object T, result of the conversion, must have its fields'names corresponding exactly to one of the following:
 * <ul>
 * <li>the names of the table field
 * <li>the other table's name if the object's field is another model object created from database
 * <li>the alias specified for this parser by {@link be.florien.joinorm.architecture.DBTable#setAlias(String) setAlias}
 * </ul>
 *
 * @param <T> model object representing the table, which will get the info from the database at the end of the parsing
 * @author Florien Flament
 */
public abstract class DBTable<T> extends DBData<T> {
    //todo reorganize methods by accessibility / overriding role
    /*
     * CONSTANTS
     */

    private static final int QUERY_ALL_ITEMS = -20;

    /*
     * FIELDS
     */

    private final List<DBTable<?>> tableWrites = new ArrayList<>();
    private final List<DBPrimitiveField<?>> primitiveWrites = new ArrayList<>();
    private final List<String> tableNameWrites = new ArrayList<>();
    private final List<String> tableValueRefWrites = new ArrayList<>();
    private final List<DBTable<?>> tableQueries = new ArrayList<>();
    private final List<DBPrimitiveField<?>> primitiveQueries = new ArrayList<>();
    private final List<DbId> deleteIds = new ArrayList<>();
    private final List<WhereStatement> wheres = new ArrayList<>();
    private final HashMap<String, Field> fields = new HashMap<>();

    private final String tableName;
    protected final Class<T> modelObjectClass;
    //TODO Precision and handling of joinTable ? (table_B that consist of table_A_id and table_C_id)
    private int initialRowPosition;
    private int redundantRows;
    private int columnQueriedCount = -1;
    private int idColumnCount;
    private boolean isRedundant = false;
    private boolean arePrimitiveParsed = false;

    private boolean isSubTableFinished;
    private T objectToWrite;
    private DbId ids = new DbId();
    private List<T> results = new ArrayList<>();
    private Cursor cursor = null;

    /*
     * CONSTRUCTOR
     */

    /**
     * Constructs a new DBTable. Implementation of this class should do a no-parameters constructor calling this constructor.
     *
     * @param tableName  Name of the table as in the database
     * @param modelClass Class of the result model object
     */
    protected DBTable(String tableName, Class<T> modelClass) {
        this.tableName = tableName;
        dataName = tableName;
        modelObjectClass = modelClass;
        try {
            objectToWrite = modelObjectClass.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        resetCurrentParsing();
    }

    /*
     * ABSTRACT METHODS
     */

    /**
     * Add the ID to the query. Implementation of this class must use {@link DBTable#selectId(String...)}.
     *
     * @return this instance of DBTable
     */
    public abstract DBTable<T> selectId();

    /**
     * Return the column name for this table id without the table name or its alias
     *
     * @return The column name for this table id
     */
    protected abstract List<String> getId();

    /*
     * OVERRIDDEN METHODS
     */

    @Override
    protected List<String> buildSelect(String tableName) {
        List<String> projection = new ArrayList<>();
        for (DBData<?> fieldToSelect : primitiveQueries) {
            projection.addAll(fieldToSelect.buildSelect(dataName));
        }
        for (DBData<?> fieldToSelect : tableQueries) {
            projection.addAll(fieldToSelect.buildSelect(dataName));
        }
        return projection;
    }

    @Override
    protected void parseRowValue(Cursor cursor, int column) {
        try {
            if (initialRowPosition == -1) {
                initialRowPosition = cursor.getPosition();
            }

            isSubTableFinished = false;
            int currentColumn = column;

            if (arePrimitiveParsed) {
                currentColumn += primitiveQueries.size();
            } else {
                currentColumn = parsePrimitives(cursor, currentColumn);
            }

            parseTables(cursor, currentColumn);
        } catch (Exception ex) {
            throw new DBArchitectureException(ex);
        }
    }

    @Override
    protected void setComplete() {
        super.setComplete();

        try {
            for (DBTable<?> tableToExtract : tableQueries) {
                tableToExtract.setComplete();
                if (!tableToExtract.isRedundant) {
                    setValues(tableToExtract);
                }
                tableToExtract.setWillBeRedundant(false, 0);
                tableToExtract.resetCurrentParsing();
                tableToExtract.resetList();
            }
        } catch (Exception ex) {
            throw new DBArchitectureException("Exception caught during the parsing of table " + tableName + "(alias : " + dataName + ")", ex);
        }
    }

    @Override
    protected void resetCurrentParsing() {
        super.resetCurrentParsing();
        try {
            currentObject = modelObjectClass.newInstance();
            for (DBData<?> fieldToReset : tableQueries) {
                fieldToReset.resetCurrentParsing();
            }
            ids = new DbId();
            arePrimitiveParsed = false;
            isSubTableFinished = false;
        } catch (Exception e) {
            throw new DBArchitectureException(e);
        }
    }

    /*
     * PUBLIC METHODS
     */

    /**
     * Add a statement to add to a collection of WhereStatement
     *
     * @param statement the where statement
     * @return this DBTable for chaining commands
     */
    @SuppressWarnings("unused")
    public DBTable<T> addWhere(WhereStatement statement) {
        wheres.add(statement);
        return this;
    }

    /**
     * Parse and return the complete list of object corresponding to this DBTable in the SQLite database provided in the SQLiteOpenHelper
     *
     * @param openHelper The helper providing access to the database to query
     * @return The list of results
     */
    @SuppressWarnings("unused")
    public List<T> getResult(SQLiteOpenHelper openHelper) {
        return getResult(openHelper, QUERY_ALL_ITEMS);
    }

    /**
     * Parse and return a list of the next nbItem objects corresponding to this DBTable in the SQLite database provided in the SQLiteOpenHelper
     *
     * @param openHelper The helper providing access to the database to query
     * @param nbItem     The number of item to retrieve
     * @return A list containing the next nbItem objects for this query
     */
    @SuppressWarnings("unused")
    public List<T> getResult(SQLiteOpenHelper openHelper, int nbItem) {
        return getResult(openHelper, nbItem, false);
    }

    /**
     * Parse and return a list of all item parsed so far if isReturningAllList is true plus the next nbItem objects corresponding
     * to this DBTable in the SQLite database provided in the SQLiteOpenHelper
     *
     * @param openHelper         The helper providing access to the database to query
     * @param nbItem             The number of item to retrieve
     * @param isReturningAllList If true, the list will contain all the items parsed so far
     * @return a list of object corresponding to the query for this database
     */
    @SuppressWarnings("unused")
    private List<T> getResult(SQLiteOpenHelper openHelper, int nbItem, boolean isReturningAllList) {
        if (openHelper == null) {
            throw new NullPointerException("Please provide an initialized SQLiteOpenHelper");
        }

        if (!isReturningAllList) {
            resetList();//todo completeResult AND lastResult ?
        }

        if (cursor == null) {
            SQLiteQueryBuilder query = new SQLiteQueryBuilder();
            query.setTables(getJoinComplete());
            cursor = query.query(openHelper.getReadableDatabase(), getSelect(), getWhere(), null, null, null, getOrderBy());
        }

        return getResult(cursor, nbItem);
    }

    /**
     * Calling this method will reset the result parsed so far, and query again from start.
     */
    @SuppressWarnings("unused")
    public void resetQuery() {
        resetList();
        cursor = null;
    }

    /**
     * Check if this table has more result to parse
     *
     * @return true if {@link #getResult(SQLiteOpenHelper, int, boolean)} of {@link #getResult(SQLiteOpenHelper, int)}
     * has more result to parse and return
     */
    @SuppressWarnings("unused")
    public boolean hasMoreResults() {
        return cursor != null && !cursor.isAfterLast();
    }

    /**
     * Write all the demanded object pass by write methods into the database represented by the SQLiteOpenHelper in parameter.
     *
     * @param openHelper the SQLiteOpenHelper for the database to write
     */
    @SuppressWarnings("unused")
    public void writeAll(SQLiteOpenHelper openHelper) {
        openHelper.getWritableDatabase().beginTransaction();
        try {
            List<DBWrite> write = new ArrayList<>();
            getWrite(write, "");
            for (DBWrite toWrite : write) {
                openHelper.getWritableDatabase().insert(toWrite.getTableName(), null, toWrite.getValue());
            }
            openHelper.getWritableDatabase().setTransactionSuccessful();
        } finally {
            openHelper.getWritableDatabase().endTransaction();
            openHelper.close();
        }
    }

    /**
     * Write all the demanded object pass by write methods into the database represented by the SQLiteOpenHelper in parameter.
     *
     * @param openHelper the SQLiteOpenHelper for the database to write
     */
    @SuppressWarnings("unused")
    public void deleteAll(SQLiteOpenHelper openHelper) {
        openHelper.getWritableDatabase().beginTransaction();
        try {
            List<DBDelete> deletes = getDelete();
            for (DBDelete delete : deletes) {

                List<String> whereArgs = delete.getWhereArgs();
                String[] argsArray = new String[whereArgs.size()];
                int i = 0;
                for (String integer : whereArgs) {
                    argsArray[i++] = integer;
                }
                openHelper.getWritableDatabase().delete(delete.getTableName(), delete.getWhereClause(), argsArray);
            }
            openHelper.getWritableDatabase().setTransactionSuccessful();
        } finally {
            openHelper.getWritableDatabase().endTransaction();
            openHelper.close();
        }
    }

    /*
     * PROTECTED METHODS
     */

    //QUERY BUILDING

    /**
     * Construct and return an array of fields' names selected for the query. This method should be used uniquely if this DBTable is not a inner
     * DBData.
     *
     * @return an array of fields'names to be queried
     */
    private String[] getSelect() {
        List<String> buildSelect = buildSelect("");
        String[] projection = new String[buildSelect.size()];
        return buildSelect.toArray(projection);
    }

    /**
     * Populate a list of DBWrite with the data asked for writing.
     *
     * @param writes    The list of DBWrite to populate.
     * @param reference If given the name of the id field, will return it's value. // todo I don't remember if this is correct, also, multiple ids.
     * @return The id if the name is provided, 0 otherwise.
     */
    private int getWrite(List<DBWrite> writes, String reference) {
        int referenceId = 0;
        try {
            ContentValues value = new ContentValues();
            for (int i = 0; i < tableNameWrites.size(); i++) {
                value.put(tableNameWrites.get(i), tableWrites.get(i).getWrite(writes, tableValueRefWrites.get(i)));
            }
            for (DBPrimitiveField<?> field : primitiveWrites) {
                if (field instanceof StringField) {
                    value.put(field.dataName, (String) getFieldToSet(field).get(objectToWrite));
                } else if (field instanceof DoubleField) {
                    value.put(field.dataName, (Double) getFieldToSet(field).get(objectToWrite));
                } else if (field instanceof NullField) {
                    value.putNull(field.dataName);
                } else if (field instanceof BooleanField) {
                    value.put(field.dataName, (Boolean) getFieldToSet(field).get(objectToWrite));
                } else if (field instanceof IntField) {

                    Integer integer = (Integer) getFieldToSet(field).get(objectToWrite);
                    if (field.dataName.equals(reference)) {
                        referenceId = integer;
                    }
                    value.put(field.dataName, integer);
                }
            }
            writes.add(new DBWrite(dataName, value));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return referenceId;
    }

    /**
     * Create and construct a list of {@link be.florien.joinorm.architecture.DBDelete DBDelete}
     *
     * @return A list of DBDelete
     */
    private List<DBDelete> getDelete() {
        List<DBDelete> deletes = new ArrayList<>();
        for (DbId id : deleteIds) {
            List<String> idNames = getCompleteId();
            deletes.add(new DBDelete(dataName, idNames, id.getIds()));
        }
        return deletes;
    }

    /**
     * Construct and return the where statement associated
     *
     * @return the where statement
     */
    protected String getWhere() {
        String where = "";
        for (WhereStatement statement : wheres) {
            if (!TextUtils.isEmpty(where)) {
                if (statement.isOr()) {
                    where += " OR ";
                } else {
                    where += " AND ";
                }
            } else {
                where += "(";
            }
            where += dataName + "." + statement.getStatement();
        }
        if (!TextUtils.isEmpty(where)) {
            where += ")";
        }
        for (DBTable<?> field : tableQueries) {
            String toAdd = field.getWhere();
            if (!TextUtils.isEmpty(toAdd) && !TextUtils.isEmpty(where)) {
                where += " AND ";
            }
            where += toAdd;
        }
        return where;
    }

    /**
     * Return a list of fields'names from the database to order the query by.
     *
     * @return a list of fields'names
     */
    protected String getOrderBy() {
        String orderBy = getOrderByForThis();
        for (DBTable<?> fieldToSelect : tableQueries) {
            orderBy += ", " + fieldToSelect.getOrderBy();
        }
        return orderBy;
    }

    /**
     * Return a column name in the form "TABLE_ALIAS.COLUMN_NAME". Override this method if you want the sorting to be another column than the ID
     * (Default). But be careful as the data from a single object must be in consecutive rows in the query result.
     *
     * @return A column name in the form "TABLE_ALIAS.COLUMN_NAME"
     */
    //todo annotation for overriding ?
    private String getOrderByForThis() {
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

    /**
     * Construct and return the JOIN statement for this table and all its inner tables. This method should be used uniquely if this DBTable is not a
     * inner DBData.
     *
     * @return The complete JOIN statement for the query
     */
    private String getJoinComplete() {
        return tableName + (dataName.equals(tableName) ? "" : " AS " + dataName) + getJoinsToAllTables();
    }

    private String getJoinsToAllTables() {
        String tables = "";
        for (DBTable<?> field : tableQueries) {
            tables = tables + " " + getJoinToInnerTable(field);
            tables = tables + " " + field.getJoinsToAllTables();
        }
        return tables;
    }

    // SELECT METHODS

    /**
     * Add the ID to the query. This implementation make sure that the ID can be found anytime during extraction of the data.
     *
     * @param columnNames The names of the field as in the database's table
     */
    protected void selectId(String... columnNames) {
        idColumnCount = columnNames.length;
        for (int position = 0; position < columnNames.length; position++) {
            String columnName = columnNames[position];
            IntField intField = new IntField(columnName);
            primitiveQueries.remove(intField);
            primitiveQueries.add(position, intField);
            setListForField(intField);
        }
    }

    /**
     * Add a simple String to the query.
     *
     * @param columnName the field's name as in the database's table
     */
    @SuppressWarnings("unused")
    protected void selectString(String columnName) {
        StringField stringField = new StringField(columnName);
        primitiveQueries.remove(stringField);
        primitiveQueries.add(stringField);
        setListForField(stringField);

    }

    /**
     * Add a simple int to the query.
     *
     * @param columnName the field's name as in the database's table
     */
    @SuppressWarnings("unused")
    protected void selectInt(String columnName) {
        IntField intField = new IntField(columnName);
        primitiveQueries.remove(intField);
        primitiveQueries.add(intField);
        setListForField(intField);
    }

    /**
     * Add a simple boolean to the query.
     *
     * @param columnName the field's name as in the database's table
     */
    @SuppressWarnings("unused")
    protected void selectBoolean(String columnName) {
        BooleanField booleanField = new BooleanField(columnName);
        primitiveQueries.remove(booleanField);
        primitiveQueries.add(booleanField);
        setListForField(booleanField);
    }

    /**
     * Add a simple double to the query.
     *
     * @param columnName the field's name as in the database's table
     */
    @SuppressWarnings("unused")
    protected void selectDouble(String columnName) {
        DoubleField doubleField = new DoubleField(columnName);
        primitiveQueries.remove(doubleField);
        primitiveQueries.add(doubleField);
        setListForField(doubleField);
    }

    /**
     * Add the table represented by tableField to the query.
     *
     * @param tableField A representation of the table to query.
     */
    @SuppressWarnings("unused")
    protected void selectTable(DBTable<?> tableField) {
        selectTable(tableField, null);
    }

    /**
     * Add the table represented by tableField to the query, with the alias given in parameter.
     *
     * @param tableField A representation of the table to query.
     * @param alias      The alias that will be set for the table in the request AND used to retrieve the model object's field to assign the value.
     */
    @SuppressWarnings("unused")
    protected void selectTable(DBTable<?> tableField, String alias) {
        if (alias != null) {
            tableField.setAlias(alias);
        }
        tableQueries.remove(tableField);
        selectId();
        tableQueries.add(tableField);
        setListForField(tableField);

    }

    private void setListForField(DBData<?> dataField) {
        try {
            Field field = getFieldToSet(dataField);
            Type genericType = field.getGenericType();
            dataField.setIsList(genericType instanceof ParameterizedType);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();//todo as always
        }
    }

    // WRITE METHODS

    /**
     * Add the value to be written in columnName
     *
     * @param columnName Name of the column corresponding to the value
     * @param value      The value for the object at corresponding columnName
     */
    @SuppressWarnings("unused")
    protected void writeString(String columnName, String value) {
        StringField stringField = new StringField(columnName);
        primitiveWrites.remove(stringField);
        primitiveWrites.add(stringField);
        try {
            getFieldToSet(columnName).set(objectToWrite, value);
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
        primitiveWrites.remove(nullField);
        primitiveWrites.add(nullField);
    }

    /**
     * Add a boolean value to be written
     *
     * @param columnName the field's name as in the database's table
     * @param bool       The value for the object at corresponding columnName
     */
    @SuppressWarnings("unused")
    protected void writeBoolean(String columnName, boolean bool) {
        BooleanField boolField = new BooleanField(columnName);
        primitiveWrites.remove(boolField);
        primitiveWrites.add(boolField);
        try {
            getFieldToSet(columnName).set(objectToWrite, bool);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a int value to be written
     *
     * @param columnName the field's name as in the database's table
     * @param integer    The value for the object at corresponding columnName
     */
    @SuppressWarnings("unused")
    protected void writeInt(String columnName, int integer) {
        IntField intField = new IntField(columnName);
        primitiveWrites.remove(intField);
        primitiveWrites.add(intField);
        try {
            getFieldToSet(columnName).set(objectToWrite, integer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add a double value to be written
     *
     * @param columnName  the field's name as in the database's table
     * @param doubleValue The value for the object at corresponding columnName
     */
    @SuppressWarnings("unused")
    protected void writeDouble(String columnName, double doubleValue) {
        DoubleField doubleField = new DoubleField(columnName);
        primitiveWrites.remove(doubleField);
        primitiveWrites.add(doubleField);
        try {
            getFieldToSet(columnName).set(objectToWrite, doubleValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Add the table represented by tableField to the query.
     *
     * @param tableField     A representation of the table to query.
     * @param tableRef       The name of the field containing the table
     * @param tableRefValue  I don't remember and I should try to investigate todo
     * @param objectToAssign The value to write
     */
    @SuppressWarnings("unused")
    protected void writeTable(DBTable<?> tableField, String tableRef, String tableRefValue, Object objectToAssign) {
        tableWrites.remove(tableField);
        tableWrites.add(tableField);
        tableNameWrites.remove(tableRef);
        tableNameWrites.add(tableRef);
        tableValueRefWrites.remove(tableRefValue);
        tableValueRefWrites.add(tableRefValue);
        try {
            getFieldToSet(tableField).set(objectToWrite, objectToAssign);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // DELETE METHODS

    /**
     * Add an ID (or composite) to the list of IDs to delete from the DataBase
     *
     * @param ids The ids of the object to delete
     */
    @SuppressWarnings("unused")
    protected void deleteId(int... ids) {
        DbId dbId = new DbId(ids);
        deleteIds.remove(dbId);
        deleteIds.add(dbId);
    }

    // DB TABLES AND JOIN HANDLING

    /**
     * Return the IDs with the form containing the alias
     *
     * @return The complete form of the IDs
     */
    protected List<String> getCompleteId() {
        List<String> ids = new ArrayList<>(getId().size());
        for (String idPart : getId()) {
            ids.add(dataName + "." + idPart);
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
     * @param innerTable The DBTable representing a model object which is present in this DBTable's model object. Implementation of this method should test which
     *                   table it represent and return the according JOIN statement.
     * @return The JOIN statement
     */
    protected abstract String getJoinToInnerTable(DBTable<?> innerTable);

    /**
     * Construct and return a JOIN statement where innerTable contain a reference to this table ID
     *
     * @param innerTable    The table to join to this one
     * @param isLeftJoin    Whether this table is a left join or not todo explain more what a left join is
     * @param innerTableRef The columnName which refer to this table ID, without the table's name
     * @return The JOIN statement in the form "[LEFT ]JOIN INNER_TABLE [AS INNER_TABLE_ALIAS] ON TABLE.ID = INNER_TABLE[_ALIAS].INNER_TABLE_REF"
     */
    protected String getJoinOnId(DBTable<?> innerTable, boolean isLeftJoin, String... innerTableRef) {
        return (isLeftJoin ? "LEFT " : "") + "JOIN " + innerTable.tableName + (innerTable.dataName.equals(tableName) ? "" : " AS " + innerTable.dataName)
                + getJoinConditionOnID(innerTable, innerTableRef);
    }

    /**
     * Construct and return a JOIN statement where this table contain a reference to innerTable ID
     *
     * @param innerTable   The table to join to this one
     * @param isLeftJoin   Whether this table is a left join or not todo explain more what a left join is
     * @param thisTableRef The columnName which refer to the innerTable ID, without the table's name
     * @return The JOIN statement in the form "JOIN INNER_TABLE [AS INNER_TABLE_ALIAS] ON TABLE.THIS_TABLE_REF = INNER_TABLE[_ALIAS].ID"
     */
    protected String getJoinOnRef(DBTable<?> innerTable, boolean isLeftJoin, String... thisTableRef) {
        return (isLeftJoin ? "LEFT " : "") + "JOIN " + innerTable.tableName + (innerTable.dataName.equals(tableName) ? "" : " AS " + innerTable.dataName)
                + getJoinConditionOnRef(innerTable, thisTableRef);
    }

    private String getJoinConditionOnID(DBTable<?> innerTable, String... innerTableRef) {
        String join = " ON ";
        List<String> ids = getCompleteId();
        for (int i = 0; i < ids.size() && innerTableRef != null && i < innerTableRef.length; i++) {
            if (i > 0) {
                join = join + " and ";
            }

            join = join + ids.get(i) + " = " + innerTable.dataName + "." + innerTableRef[i];
        }

        return join;
    }

    private String getJoinConditionOnRef(DBTable<?> innerTable, String... thisTableRef) {
        String join = " ON ";
        List<String> ids = getId();
        for (int i = 0; i < ids.size() && thisTableRef != null && i < thisTableRef.length; i++) {
            if (i > 0) {
                join = join + " and ";
            }

            join = join + dataName + "." + thisTableRef[i] + " = " + innerTable.dataName + "." + innerTable.getId().get(i);
        }

        return join;
    }

    /*
     * PRIVATE METHODS
     */

    /**
     * Set an alias for this table. Said alias could be use in case where:
     * <ul>
     * <li>a model object contains more than one occurrence of another table's model object
     * <li>the table is referenced multiple times in the JOIN statement, creating confusion for the request
     * </ul>
     *
     * @param aliasName The alias that will be set for the table in the request AND used to retrieve the model object's field to assign the value
     */
    private void setAlias(String aliasName) {
        dataName = aliasName;
    }

    // DATA EXTRACTION

    /**
     * Used to know how many columns has been read by this parser
     */
    private int getNumberOfColumnsQueried() {
        if (columnQueriedCount == -1) {
            columnQueriedCount = primitiveQueries.size();
            for (DBTable<?> field : tableQueries) {
                columnQueriedCount += field.getNumberOfColumnsQueried();
            }
        }
        return columnQueriedCount;
    }

    /**
     * Extract the data from the Cursor in parameter and return a List of model object filled with queried fields
     *
     * @param cursor The cursor which has received the data from the database.
     * @param nbItem The number of item to retrieve
     * @return a List of model object
     */
    private List<T> getResult(Cursor cursor, int nbItem) {
        if (cursor.isBeforeFirst()) {
            cursor.moveToFirst();
        }
        int itemParsed = 0;
        if (!cursor.isAfterLast()) {
            initId(cursor, 0);
        } else {
            return getResultList();
        }
        while (!cursor.isAfterLast() && (nbItem == QUERY_ALL_ITEMS || itemParsed < nbItem)) {
            if (compareIDs(cursor, 0)) {
                parseRowValue(cursor, 0);
                int rowToFinishParsing = getRowToFinishParsing();
                for (int rowToPass = rowToFinishParsing; rowToPass > 0 && !cursor.isAfterLast(); rowToPass--) {
                    cursor.moveToNext();
                }
            } else {
                setComplete();
                addResultToList();
                resetCurrentParsing();
                initId(cursor, 0);
                itemParsed++;
            }

        }
        if (cursor.isAfterLast()) {
            setComplete();
            addResultToList();
        }
        return getResultList();
    }

    private void initId(Cursor cursor, int column) {
        for (int offset = 0; offset < idColumnCount; offset++) {
            ids.getIds().add(String.valueOf(cursor.getInt(column + offset)));
        }
    }

    private boolean compareIDs(Cursor cursor, int column) {
        if (ids.getIds().size() < 1) {
            return true;
        }
        for (int offset = 0; offset < idColumnCount; offset++) {
            if (!ids.getIds().get(offset).equals(String.valueOf(cursor.getInt(column + offset)))) {
                return false;
            }
        }
        return true;
    }

    private int parsePrimitives(Cursor cursor, int currentColumn) throws IllegalAccessException {
        for (DBPrimitiveField<?> primitiveToExtract : primitiveQueries) {
            primitiveToExtract.parseRowValue(cursor, currentColumn);
            try {
                Field field = getFieldToSet(primitiveToExtract);
                field.set(currentObject, primitiveToExtract.getValue());
            } catch (NoSuchFieldException exception) {
                Log.e("WHAT", "error extracting a value in table " + dataName, exception);
            }
            currentColumn++;
        }
        arePrimitiveParsed = true;
        return currentColumn;
    }

    private void parseTables(Cursor cursor, int currentColumn) throws NoSuchFieldException, IllegalAccessException {
        for (DBTable<?> tableToExtract : tableQueries) {
            Field field = getFieldToSet(tableToExtract);
            if (isSubTableFinished && !tableToExtract.isRedundant) {
                if (tableToExtract.isList()) {
                    tableToExtract.addResultToList();
                    field.set(currentObject, tableToExtract.getResultListAndParseNext(cursor, currentColumn));
                } else {
                    field.set(currentObject, tableToExtract.getResultAndParseNext(cursor, currentColumn));
                }
            } else if (!tableToExtract.isRedundant) {
                if (!tableToExtract.compareIDs(cursor, currentColumn)) {
                    tableToExtract.setComplete();
                    if (tableToExtract.isList()) {
                        tableToExtract.addResultToList();
                    } else {
                        field.set(currentObject, tableToExtract.getValue());
                    }
                    tableToExtract.resetCurrentParsing();
                    isSubTableFinished = true;
                }
                tableToExtract.initId(cursor, currentColumn);
                tableToExtract.parseRowValue(cursor, currentColumn);
                isSubTableFinished = isSubTableFinished || tableToExtract.isSubTableFinished;
            }
            currentColumn += tableToExtract.getNumberOfColumnsQueried();
        }
    }

    private List<T> getResultListAndParseNext(Cursor cursor, int currentColumn) {
        results.add(currentObject);
        ArrayList<T> list = new ArrayList<>(getResultList());
        parseNext(cursor, currentColumn);
        return list;
    }

    private T getResultAndParseNext(Cursor cursor, int currentColumn) {
        T object = currentObject;
        parseNext(cursor, currentColumn);
        return object;
    }

    private void parseNext(Cursor cursor, int currentColumn) {
        setComplete();
        setWillBeRedundant(true, cursor.getPosition());
        resetCurrentParsing();
        initId(cursor, currentColumn);
        parseRowValue(cursor, currentColumn);
    }

    /**
     * Return the number of rows used for the making of this representation
     *
     * @return the number of rows used
     */
    private int getRowToFinishParsing() {
        int rows;
        if (!isRedundant) {
            rows = 1;
            for (DBTable<?> table : tableQueries) {
                int rowToFinishParsing = table.getRowToFinishParsing();
                rows = (rows >= rowToFinishParsing ? rows : rowToFinishParsing);
            }
        } else {
            rows = redundantRows;
        }
        return rows;
    }

    /**
     * Return the {@link java.lang.reflect.Field Field} which will be used by the parser to set the value to the correct filed in the model object. Retrieve
     * said field by using the columnName or the alias is one is set. Override this method if you want the model object's field name and the columnName/alias
     * to be different.
     *
     * @param fieldToSet Representation for retrieving the data from the database. Used to get the model object field name
     * @return The Field to be set
     * @throws NoSuchFieldException
     */
    protected Field getFieldToSet(DBData<?> fieldToSet) throws NoSuchFieldException {
        return getFieldToSet(fieldToSet.dataName);
    }

    /**
     * Return the {@link java.lang.reflect.Field Field} which will be used by the parser to set the value to the correct filed in the model object. Retrieve
     * said field by using the field to set. Override this method if you want the model object's field name and fieldToSet to be different.
     *
     * @param fieldToSet The model object field name
     * @return The Field to be set
     * @throws NoSuchFieldException
     */
    private Field getFieldToSet(String fieldToSet) throws NoSuchFieldException {
        if (!fields.containsKey(fieldToSet)) {
            fields.put(fieldToSet, modelObjectClass.getField(fieldToSet));
        }
        return fields.get(fieldToSet);
    }

    private void setWillBeRedundant(boolean isRedundant, int cursorPosition) {
        if (this.isRedundant == isRedundant) {
            return;
        }
        this.isRedundant = isRedundant;
        if (isRedundant) {
            redundantRows = cursorPosition - initialRowPosition;
        } else {
            redundantRows = 0;
            for (DBTable<?> table : tableQueries) {
                table.setWillBeRedundant(false, cursorPosition);
            }
        }
    }

    private void resetList() {
        results = new ArrayList<>();
        initialRowPosition = -1;
    }

    private void setValues(DBTable<?> tableToExtract) throws NoSuchFieldException, IllegalAccessException {
        Field field = getFieldToSet(tableToExtract);
        if (tableToExtract.isList()) {
            tableToExtract.addResultToList();
            field.set(currentObject, tableToExtract.getResultList());
        } else {
            field.set(currentObject, tableToExtract.getValue());
        }
    }

    private void addResultToList() {
        if (isComplete) {
            results.add(currentObject);
        }
    }

    private List<T> getResultList() {
        return results;
    }

    /*
     * EQUALS
     */

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((dataName == null) ? 0 : dataName.hashCode());
        result = prime * result + ((tableName == null) ? 0 : tableName.hashCode());
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
        if (dataName == null) {
            if (other.dataName != null)
                return false;
        } else if (!dataName.equals(other.dataName))
            return false;
        if (tableName == null) {
            if (other.tableName != null)
                return false;
        } else if (!tableName.equals(other.tableName))
            return false;
        return true;
    }

}
