
package be.florien.joinorm.architecture;

public enum WhereCondition {
    NOTNULL(" IS NOT NULL"),
    NULL(" IS NULL"),
    EQUAL(" = "),
    LESS(" < "),
    MORE(" > "),
    LESS_EQUAL(" <= "),
    MORE_EQUAL(" >= "),
    IN(" IN "),
    BETWEEN(" BETWEEN "),
    LIKE(" LIKE ");
    private String condition;

    private WhereCondition(String condition) {
        this.condition = condition;
    }

    public String getCondition() {
        return condition;
    }

}
