
package be.florien.joinorm.primitivefield;

import android.database.Cursor;

import be.florien.joinorm.architecture.DBPrimitiveField;

public class StringField extends DBPrimitiveField<String> {

    public StringField(String fieldName) {
        super(fieldName);
    }

    @Override
    public void extractRowValue(Cursor cursor, int column) {
        currentObject = cursor.getString(column);
        setComplete();
    }

}
