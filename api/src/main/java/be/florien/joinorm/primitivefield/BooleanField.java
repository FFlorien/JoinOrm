package be.florien.joinorm.primitivefield;

import android.database.Cursor;

import be.florien.joinorm.architecture.DBPrimitiveField;

public class BooleanField extends DBPrimitiveField<Boolean> {

    public BooleanField(String fieldName) {
        super(fieldName);
    }

    @Override
    protected void extractRowValue(Cursor cursor, int column) {
        int value = cursor.getInt(column);
        mCurrentObject = (value == 1);
    }

}
