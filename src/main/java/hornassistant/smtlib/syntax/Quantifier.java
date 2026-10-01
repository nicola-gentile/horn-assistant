package hornassistant.smtlib.syntax;

/** The two SMT-LIB quantifiers. */
public enum Quantifier {
    FORALL,
    EXISTS;

    /** The SMT-LIB keyword of this quantifier. */
    public String keyword() {
        return switch (this) {
            case FORALL -> "forall";
            case EXISTS -> "exists";
        };
    }
}
