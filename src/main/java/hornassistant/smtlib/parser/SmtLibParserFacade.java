package hornassistant.smtlib.parser;

import hornassistant.smtlib.syntax.Script;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/** Parses SMT-LIB text into the syntax AST. ANTLR types do not leave this package. */
public final class SmtLibParserFacade {

    private SmtLibParserFacade() {}

    /**
     * Parses a whole script.
     *
     * @throws hornassistant.chc.ChcInputException on the first lexical or syntax error
     */
    public static Script parse(String text) {
        var lexer = new SmtLibLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
        var parser = new SmtLibParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(ThrowingErrorListener.INSTANCE);
        return AstBuilder.script(parser.script());
    }
}
