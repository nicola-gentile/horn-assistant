package hornassistant.chc;

/** The input is not a CHC system. The message is one line and is shown to the user after {@code error: }. */
public final class ChcInputException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ChcInputException(String message) {
        super(message, null, false, false);
    }
}
