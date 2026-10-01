package hornassistant.smtlib.sort;

/** A theory operator was applied to arguments of the wrong number or sort. The message gives the reason. */
public final class IllSortedException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public IllSortedException(String reason) {
        super(reason);
    }
}
