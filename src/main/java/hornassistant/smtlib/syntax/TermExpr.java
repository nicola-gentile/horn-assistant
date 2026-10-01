package hornassistant.smtlib.syntax;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** A term as written in the script. */
public sealed interface TermExpr {

    sealed interface Constant extends TermExpr {}

    record Numeral(BigInteger value) implements Constant {}

    record Decimal(BigDecimal value) implements Constant {}

    /** A hexadecimal literal; {@code digits} excludes the {@code #x} prefix. */
    record Hex(String digits) implements Constant {}

    /** A binary literal; {@code digits} excludes the {@code #b} prefix. */
    record Bin(String digits) implements Constant {}

    /** A string literal with the escaped quotes already decoded. */
    record Str(String value) implements Constant {}

    /** A qualified identifier: {@code id} or {@code (as id sort)}. */
    record QualId(Identifier id, Optional<SortExpr> as) implements TermExpr {
        public static QualId of(String symbol) {
            return new QualId(Identifier.of(symbol), Optional.empty());
        }
    }

    record App(QualId function, List<TermExpr> args) implements TermExpr {
        public App {
            args = List.copyOf(args);
        }
    }

    record Let(List<Binding> bindings, TermExpr body) implements TermExpr {
        public Let {
            bindings = List.copyOf(bindings);
        }
    }

    record Quantified(Quantifier quantifier, List<SortedVar> vars, TermExpr body) implements TermExpr {
        public Quantified {
            vars = List.copyOf(vars);
        }
    }

    record Annotated(TermExpr term, List<Attribute> attributes) implements TermExpr {
        public Annotated {
            attributes = List.copyOf(attributes);
        }
    }

    /** A {@code match} term. Only its scrutinee is kept, because {@code match} is always rejected later. */
    record Match(TermExpr scrutinee) implements TermExpr {}
}
