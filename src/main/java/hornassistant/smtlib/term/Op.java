package hornassistant.smtlib.term;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The interpreted operators of the supported theories, with their SMT-LIB names. */
public enum Op {
    NOT("not"),
    AND("and"),
    OR("or"),
    XOR("xor"),
    IMPLIES("=>"),
    EQ("="),
    DISTINCT("distinct"),
    ITE("ite"),
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV_REAL("/"),
    IDIV("div"),
    MOD("mod"),
    ABS("abs"),
    LE("<="),
    LT("<"),
    GE(">="),
    GT(">"),
    TO_REAL("to_real"),
    TO_INT("to_int"),
    IS_INT("is_int"),
    SELECT("select"),
    STORE("store"),
    /** {@code ((as const (Array I E)) e)}; the array sort is the sort of the application. */
    CONST_ARRAY("const");

    private static final Map<String, Op> BY_SYMBOL =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Op::symbol, Function.identity()));

    private final String symbol;

    Op(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public static Optional<Op> bySymbol(String symbol) {
        return Optional.ofNullable(BY_SYMBOL.get(symbol));
    }
}
