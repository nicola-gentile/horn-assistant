package hornassistant.smtlib.syntax;

import java.util.List;

/** A generic s-expression, used for attribute values and the arguments of commands without effect. */
public sealed interface SExpr {

    record Literal(TermExpr.Constant constant) implements SExpr {}

    record Symbol(String name) implements SExpr {}

    /** A keyword, stored without the colon. */
    record Keyword(String name) implements SExpr {}

    /** A reserved word such as {@code par} or {@code _}. */
    record Reserved(String word) implements SExpr {}

    record ListExpr(List<SExpr> items) implements SExpr {
        public ListExpr {
            items = List.copyOf(items);
        }
    }
}
