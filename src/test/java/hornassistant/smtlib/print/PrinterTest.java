package hornassistant.smtlib.print;

import static hornassistant.smtlib.sort.Sort.BOOL;
import static hornassistant.smtlib.sort.Sort.INT;
import static hornassistant.smtlib.sort.Sort.REAL;
import static org.assertj.core.api.Assertions.assertThat;

import hornassistant.chc.Predicate;
import hornassistant.smtlib.sort.Sort;
import hornassistant.smtlib.syntax.Quantifier;
import hornassistant.smtlib.term.Op;
import hornassistant.smtlib.term.Term;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrinterTest {

    private static final Term.Var X = new Term.Var("x", INT);
    private static final Term.Var Y = new Term.Var("y", INT);

    @Test
    void printsSorts() {
        assertThat(Printer.sort(new Sort.Array(INT, new Sort.Array(REAL, BOOL)))).isEqualTo("(Array Int (Array Real Bool))");
    }

    @Test
    void printsVariablesQuotedWhenNeeded() {
        assertThat(Printer.term(X)).isEqualTo("x");
        assertThat(Printer.term(new Term.Var("a b", INT))).isEqualTo("|a b|");
        assertThat(Printer.term(new Term.Var("forall", INT))).isEqualTo("|forall|");
        assertThat(Printer.term(new Term.Var("1x", INT))).isEqualTo("|1x|");
        assertThat(Printer.term(new Term.Var("", INT))).isEqualTo("||");
    }

    @Test
    void printsLiterals() {
        assertThat(Printer.term(Term.TRUE)).isEqualTo("true");
        assertThat(Printer.term(Term.FALSE)).isEqualTo("false");
        assertThat(Printer.term(Term.intLit(42))).isEqualTo("42");
        assertThat(Printer.term(new Term.RealLit(new BigDecimal("2")))).isEqualTo("2.0");
        assertThat(Printer.term(new Term.RealLit(new BigDecimal("2.50")))).isEqualTo("2.5");
        assertThat(Printer.term(new Term.RealLit(new BigDecimal("0.001")))).isEqualTo("0.001");
        assertThat(Printer.term(new Term.RealLit(new BigDecimal("1E+3")))).isEqualTo("1000.0");
    }

    @Test
    void printsApplications() {
        assertThat(Printer.term(Term.app(Op.ADD, X, Term.intLit(1)))).isEqualTo("(+ x 1)");
        assertThat(Printer.term(Term.app(Op.AND))).isEqualTo("(and)");
        assertThat(Printer.term(Term.app(Op.IMPLIES, Term.app(Op.LE, X, Y), Term.FALSE))).isEqualTo("(=> (<= x y) false)");
    }

    @Test
    void printsConstantArrays() {
        assertThat(Printer.term(Term.constArray(new Sort.Array(INT, BOOL), Term.FALSE)))
                .isEqualTo("((as const (Array Int Bool)) false)");
    }

    @Test
    void printsPredicateApplications() {
        var inv = new Predicate("inv", List.of(INT, INT));
        assertThat(Printer.term(new Term.PredApp(inv, List.of(X, Y)))).isEqualTo("(inv x y)");
        assertThat(Printer.term(new Term.PredApp(new Predicate("Init", List.of()), List.of()))).isEqualTo("Init");
        assertThat(Printer.term(new Term.PredApp(new Predicate("my pred", List.of()), List.of()))).isEqualTo("|my pred|");
    }

    @Test
    void printsQuantifiersInTheirOrder() {
        var body = Term.app(Op.LT, Y, X);
        assertThat(Printer.term(new Term.Quant(Quantifier.EXISTS, List.of(Y, X), body)))
                .isEqualTo("(exists ((y Int) (x Int)) (< y x))");
    }
}
