
package be.florien.joinorm.architecture;

import android.database.Cursor;

import java.util.List;

/**
 * Class representing a request to the database and parsing said request, making the link between the Database representation with references and the
 * object model with classes and field containing the representation.
 * 
 * @author florien
 * @param <T> Represent an object which can be retrieved from a database
 */
public abstract class DBData<T> {

    /**
     * Name of the field to be retrieved
     */
    protected String dataName;
    protected boolean mIsComplete;
    protected T mCurrentObject;
    
    /**
     * Return the name of this field or table. In case of a DBTable, the dataName could be an alias.
     * @return The name of this field or table
     */
    public String getDataName(){
        return dataName;
    }

    /**
     * Convert the demanded field (or the table associated if the field is a foreign ID) into an Object from the row represented with the Cursor.
     * 
     * @param cursor The Cursor from which the selected field/table will be converted
     * @param column Represent the column number where the selected field/table is stored in the Cursor
     */
    protected abstract void extractRowValue(Cursor cursor, int column);

    /**
     * Construct and return the list of fields to be retrieved in order to make the object representation
     * 
     * @param tableName Name of the table containing the field
     * @return a list of fields' names to be retrieved
     */
    protected abstract List<String> buildProjection(String tableName);

    /**
     * Notify the table that the parsing of this object is finished
     */
    protected void setComplete() {
        mIsComplete = true;
    }

    /**
     * Get the result of the parsing if it's finished
     * 
     * @return the result of the parsing or null if it's not completed
     */
    public T getValue() {
        if (mIsComplete) {
            return mCurrentObject;
        }
        return null;
    }

    /**
     * Reset the current DBData, allowing the parsing of a new Object
     */
    protected void resetCurrentParsing() {
        mCurrentObject = null;
        mIsComplete = false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dataName == null) ? 0 : dataName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DBData<?> other = (DBData<?>) obj;
        if (dataName == null) {
            if (other.dataName != null)
                return false;
        } else if (!dataName.equals(other.dataName))
            return false;
        return true;
    }

}
