package be.florien.joinorm.architecture;

import android.os.Parcel;
import android.os.Parcelable;

public class WhereStatement implements Parcelable {
    private String columnName;
    private String value;
    private WhereCondition condition;
    private boolean isOr = false;

    public static final Parcelable.Creator<WhereStatement> CREATOR = new Creator<WhereStatement>() {

        @Override
        public WhereStatement[] newArray(int size) {
            return new WhereStatement[size];
        }

        @Override
        public WhereStatement createFromParcel(Parcel source) {
            return new WhereStatement(source);
        }
    };

    private WhereStatement(Parcel in) {
        columnName = in.readString();
        value = in.readString();
        condition = WhereCondition.values()[in.readInt()];
        isOr = (in.readInt() == 1);
    }

    public WhereStatement(String columnName, boolean isNull) {
        this.columnName = columnName;
        if (isNull) {
            condition = WhereCondition.NULL;
        } else {
            condition = WhereCondition.NOTNULL;
        }
    }

    public WhereStatement(String columnName, String value) {
        this.columnName = columnName;
        this.value = value;
        condition = WhereCondition.LIKE;
    }

    public WhereStatement(String columnName, int value, WhereCondition condition) {
        this.columnName = columnName;
        this.value = String.valueOf(value);
        if (condition == WhereCondition.LIKE) {
            this.condition = WhereCondition.EQUAL;
        } else {
            this.condition = condition;
        }
    }

    public WhereStatement(String columnName, String value, WhereCondition condition) {
        this.columnName = columnName;
        this.value = value;
        this.condition = condition;
    }

    public WhereStatement(String columnName, int id) {
        this.columnName = columnName;
        this.value = String.valueOf(id);
        condition = WhereCondition.EQUAL;
    }

    public String getStatement() {
        if (condition == WhereCondition.NOTNULL || condition == WhereCondition.NULL) {
            return columnName + condition.getCondition();
        }
        return columnName + condition.getCondition() + value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((columnName == null) ? 0 : columnName.hashCode());
        result = prime * result + ((condition == null) ? 0 : condition.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        WhereStatement other = (WhereStatement) obj;
        if (columnName == null) {
            if (other.columnName != null)
                return false;
        } else if (!columnName.equals(other.columnName))
            return false;
        if (condition != other.condition)
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(columnName);
        dest.writeString(value);
        dest.writeInt(condition.ordinal());
        dest.writeInt(isOr ? 1 : 0);
    }

    public boolean isOr() {
        return isOr;
    }

    public void setOr(boolean isOr) {
        this.isOr = isOr;
    }

}
