package hornassistant.smtlib.parser;

import hornassistant.chc.ChcInputException;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/** Turns the first lexer or parser error into a {@link ChcInputException}. */
final class ThrowingErrorListener extends BaseErrorListener {

    static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

    private ThrowingErrorListener() {}

    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e) {
        String oneLine = msg.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        throw new ChcInputException("syntax error at " + line + ":" + (charPositionInLine + 1) + ": " + oneLine);
    }
}
