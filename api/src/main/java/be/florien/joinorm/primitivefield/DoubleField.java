
package be.florien.joinorm.primitivefield;

import android.database.Cursor;

import be.florien.joinorm.architecture.DBPrimitiveField;

public class DoubleField extends DBPrimitiveField<Double> {

    public DoubleField(String fieldName) {
        super(fieldName);
    }

    @Override
    public void extractRowValue(Cursor cursor, int column) {
        currentObject = cursor.getDouble(column);
        setComplete();
    }

}
