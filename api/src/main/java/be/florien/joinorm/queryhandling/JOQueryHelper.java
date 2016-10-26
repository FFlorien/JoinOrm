
package be.florien.joinorm.queryhandling;

import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import be.florien.joinorm.architecture.DBDelete;
import be.florien.joinorm.architecture.DBTable;
import be.florien.joinorm.architecture.DBWrite;

public class JOQueryHelper {

    private static final int INT_NOT_FOUND = -404;

    private SQLiteOpenHelper mDBHelper;

    public JOQueryHelper(SQLiteOpenHelper dbHelper) {
        mDBHelper = dbHelper;
    }

    public <E> List<E> queryList(DBTable<E> table) {
        try {
            SQLiteQueryBuilder query = new SQLiteQueryBuilder();
            query.setTables(table.getJoinComplete());
            Cursor cursor = query.query(mDBHelper.getReadableDatabase(), table.getProjection(), table.getWhere(), null, null, null, table.getOrderBy());
            return table.getResult(cursor);
        } catch (Exception what) {
            Log.e("WHAT", what.getMessage());
            throw what;
        } finally {
            mDBHelper.close();
        }
    }

    public <E> List<E> queryList(DBTable<E> table, int nbItem) {
        try {
            SQLiteQueryBuilder query = new SQLiteQueryBuilder();
            query.setTables(table.getJoinComplete());
            Cursor cursor = query.query(mDBHelper.getReadableDatabase(), table.getProjection(), table.getWhere(), null, null, null, table.getOrderBy());
            return table.getResult(cursor, nbItem);
        } catch (Exception what) {
            Log.e("WHAT", what.getMessage());
            throw what;
        } finally {
            mDBHelper.close();
        }
    }

    public int queryInt(String projection, String tables, String where, String orderBy) {
        SQLiteQueryBuilder query = new SQLiteQueryBuilder();
        String projections[] = new String[1];
        projections[0] = projection;
        query.setTables(tables);
        Cursor cursor = query.query(mDBHelper.getReadableDatabase(), projections, where, null, null, null, orderBy);
        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            return cursor.getInt(0);
        } else {
            return INT_NOT_FOUND;
        }
    }

    public <E> void insert(DBTable<E> table) {
        mDBHelper.getWritableDatabase().beginTransaction();
        try {
            List<DBWrite> write = new ArrayList<>();
            table.getValuesToWrite(write, "");
            for (DBWrite toWrite : write) {
                mDBHelper.getWritableDatabase().insert(toWrite.getTableName(), null, toWrite.getValue());
            }
            mDBHelper.getWritableDatabase().setTransactionSuccessful();
        } finally {
            mDBHelper.getWritableDatabase().endTransaction();
            mDBHelper.close();
        }
    }

    public <E> void delete(DBTable<E> table) {
        mDBHelper.getWritableDatabase().beginTransaction();
        try {
            List<DBDelete> deletes = table.getDelete();
            for (DBDelete delete : deletes) {

                List<String> whereArgs = delete.getWhereArgs();
                String[] argsArray = new String[whereArgs.size()];
                int i = 0;
                for (String integer : whereArgs) {
                    argsArray[i++] = integer;
                }
                mDBHelper.getWritableDatabase().delete(delete.getTableName(), delete.getWhereClause(), argsArray);
            }
            mDBHelper.getWritableDatabase().setTransactionSuccessful();
        } finally {
            mDBHelper.getWritableDatabase().endTransaction();
            mDBHelper.close();
        }
    }

    public void close() {
        mDBHelper.close();
    }
}
