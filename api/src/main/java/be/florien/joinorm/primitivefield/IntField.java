
package be.florien.joinorm.primitivefield;

import android.database.Cursor;

import be.florien.joinorm.architecture.DBPrimitiveField;

public class IntField extends DBPrimitiveField<Integer> {

    public IntField(String fieldName) {
        super(fieldName);
    }

    @Override
    public void extractRowValue(Cursor cursor, int column) {
        mCurrentObject = cursor.getInt(column);
        setComplete();
    }

}
