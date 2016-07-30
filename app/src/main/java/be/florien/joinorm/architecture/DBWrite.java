package be.florien.joinorm.architecture;

import android.content.ContentValues;

public class DBWrite {
    
    private ContentValues mValues;
    private String mTableName;
    
    public DBWrite(String tableName, ContentValues values){
        mValues = values;
        this.mTableName = tableName;
    }

    public ContentValues getValue() {
        return mValues;
    }

    public String getTableName() {
        return mTableName;
    }

}
