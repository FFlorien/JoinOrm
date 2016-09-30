package be.florien.joinorm.architecture;


import java.util.List;

public class DBDelete {

    private String mTableName;
    private List<String> mValues;
    private List<String> mId;
    
    public DBDelete(String tableName, List<String> ids, List<String> values){
        mValues = values;
        mTableName = tableName;
        mId = ids;
    }

    public String getTableName() {
        return mTableName;
    }

    public String getWhereClause() {
        String where = "";
        for (String id : mId) {
            if (!id.equals(mId.get(0))) {
                where = where + " and";
            }
            where = where + mId.get(0) + " = ?";
        }

        return where;
    }

    public List<String> getWhereArgs() {
        return mValues;
    }

}
