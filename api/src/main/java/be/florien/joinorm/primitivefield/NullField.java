package be.florien.joinorm.primitivefield;

import android.database.Cursor;

import be.florien.joinorm.architecture.DBPrimitiveField;

public class NullField extends DBPrimitiveField<Void> {

    public NullField(String fieldName) {
        super(fieldName);
    }

    @Override
    protected void parseRowValue(Cursor cursor, int column) {
        currentObject = null;
        setComplete();
    }

}
