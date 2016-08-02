package be.florien.joinorm.architecture;


public class DBDelete {
    
    private String mValue;
    private String mTableName;
    private String mId;
    
    public DBDelete(String tableName, String id, String value){
        mValue = value;
        mTableName = tableName;
        mId = id;
    }

    public String getTableName() {
        return mTableName;
    }

    public String getWhereClause() {
        return mId + " = ?";
    }

    public String getWhereArgs() {
        return mValue;
    }

}
