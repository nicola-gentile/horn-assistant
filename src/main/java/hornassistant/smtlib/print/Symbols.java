package hornassistant.smtlib.print;

import java.util.Set;
import java.util.regex.Pattern;

/** Prints SMT-LIB symbols, quoting them with {@code |...|} when they are not valid simple symbols. */
public final class Symbols {

    private static final Pattern SIMPLE = Pattern.compile("[a-zA-Z~!@$%^&*_\\-+=<>.?/][a-zA-Z0-9~!@$%^&*_\\-+=<>.?/]*");

    /** Reserved words of SMT-LIB plus the command names, which this tool's lexer also reserves. */
    private static final Set<String> RESERVED = Set.of(
            "_", "!", "as", "let", "forall", "exists", "match", "par",
            "BINARY", "DECIMAL", "HEXADECIMAL", "NUMERAL", "STRING",
            "set-logic", "set-info", "set-option", "declare-fun", "declare-const", "define-fun",
            "define-fun-rec", "define-funs-rec", "declare-sort", "define-sort", "declare-datatype",
            "declare-datatypes", "assert", "check-sat", "check-sat-assuming", "get-model", "get-info",
            "get-option", "get-value", "get-assertions", "get-proof", "get-unsat-core", "push", "pop",
            "reset", "reset-assertions", "echo", "exit");

    private Symbols() {}

    /** Whether {@code name} can be printed without quotes. */
    public static boolean isSimple(String name) {
        return SIMPLE.matcher(name).matches() && !RESERVED.contains(name);
    }

    /** The symbol as SMT-LIB text: bare when possible, otherwise {@code |name|}. */
    public static String print(String name) {
        return isSimple(name) ? name : "|" + name + "|";
    }
}
