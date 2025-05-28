package routine.statement.condition;

public enum ConditionRelation {
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    LESS("<"),
    LESS_EQUAL("<=");

    private String strRepr;

    private ConditionRelation(String strRepr) {
        this.strRepr = strRepr;
    }

    public static ConditionRelation getCondFromVal(String val) {
        switch (val) {
            case "==":
                return EQUAL;
            case "!=":
                return NOT_EQUAL;
            case ">":
                return GREATER;
            case ">=":
                return GREATER_EQUAL;
            case "<":
                return LESS;
            case "<=":
                return LESS_EQUAL;
            default:
                return EQUAL;
        }
    }

    @Override
    public String toString() {
        return strRepr;
    }
}
