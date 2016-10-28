
package be.florien.joinorm.architecture;

import java.util.ArrayList;
import java.util.List;

public abstract class DBPrimitiveField<T> extends DBData<T> {

    public DBPrimitiveField(String fieldName) {
        dataName = fieldName;
    }

    @Override
    public List<String> buildSelect(String tableName) {
        List<String> projection = new ArrayList<String>();
        projection.add(tableName + "." + dataName);
        return projection;
    }

}
