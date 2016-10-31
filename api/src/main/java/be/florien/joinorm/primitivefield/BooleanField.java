package be.florien.joinorm.primitivefield;

import android.database.Cursor;

import be.florien.joinorm.architecture.DBPrimitiveField;

public class BooleanField extends DBPrimitiveField<Boolean> {

    public BooleanField(String fieldName) {
        super(fieldName);
    }

    @Override
    protected void parseRowValue(Cursor cursor, int column) {
        int value = cursor.getInt(column);
        currentObject = (value == 1);
    }

}
