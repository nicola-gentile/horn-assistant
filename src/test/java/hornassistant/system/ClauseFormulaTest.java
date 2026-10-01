package hornassistant.system;

import static hornassistant.smtlib.sort.Sort.BOOL;
import static hornassistant.smtlib.sort.Sort.INT;
import static org.assertj.core.api.Assertions.assertThat;

import hornassistant.chc.Clause;
import hornassistant.chc.Head;
import hornassistant.chc.Predicate;
import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.term.Op;
import hornassistant.smtlib.term.Term;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClauseFormulaTest {

    private static final Term.Var X = new Term.Var("x", INT);
    private static final Term.Var Y = new Term.Var("y", INT);
    private static final Term.Var B = new Term.Var("b", BOOL);
    private static final Predicate P = new Predicate("P", List.of(INT));
    private static final Predicate INIT = new Predicate("Init", List.of());

    private static Term.PredApp p(Term arg) {
        return new Term.PredApp(P, List.of(arg));
    }

    private static String formula(List<Term.PredApp> body, Term constraint, Head head) {
        return Printer.term(ClauseFormula.of(new Clause(body, constraint, head)));
    }

    @Test
    void factWithTrueConstraintIsTheHeadAlone() {
        assertThat(formula(List.of(), Term.TRUE, new Head.Pred(new Term.PredApp(INIT, List.of())))).isEqualTo("Init");
        assertThat(formula(List.of(), Term.TRUE, new Head.Pred(p(Term.intLit(0))))).isEqualTo("(P 0)");
        assertThat(formula(List.of(), Term.TRUE, Head.FALSE)).isEqualTo("false");
    }

    @Test
    void factPremiseIsTheConstraint() {
        assertThat(formula(List.of(), Term.app(Op.AND, Term.app(Op.LT, X, Y), B), new Head.Pred(p(X))))
                .isEqualTo("(forall ((b Bool) (x Int) (y Int)) (=> (and (< x y) b) (P x)))");
    }

    @Test
    void trueConstraintGivesTheConjunctionOfTheBody() {
        assertThat(formula(List.of(p(X)), Term.TRUE, Head.FALSE)).isEqualTo("(forall ((x Int)) (=> (and (P x)) false))");
        assertThat(formula(List.of(p(Y), p(X)), Term.TRUE, Head.FALSE))
                .isEqualTo("(forall ((x Int) (y Int)) (=> (and (P y) (P x)) false))");
    }

    @Test
    void topLevelConjunctsFollowTheBody() {
        Term phi = Term.app(Op.AND, Term.app(Op.LT, X, Y), Term.app(Op.AND, B, B));
        assertThat(formula(List.of(p(X)), phi, new Head.Pred(p(Y))))
                .isEqualTo("(forall ((b Bool) (x Int) (y Int)) (=> (and (P x) (< x y) (and b b)) (P y)))");
        assertThat(formula(List.of(p(X)), Term.app(Op.NOT, B), Head.FALSE))
                .isEqualTo("(forall ((b Bool) (x Int)) (=> (and (P x) (not b)) false))");
    }

    @Test
    void variablesAreSortedByName() {
        var vars = List.of(new Term.Var("x_1", INT), new Term.Var("x", INT), new Term.Var("X", INT), new Term.Var("x_0", INT));
        Term phi = Term.app(Op.DISTINCT, List.<Term>copyOf(vars));
        assertThat(formula(List.of(), phi, Head.FALSE))
                .isEqualTo("(forall ((X Int) (x Int) (x_0 Int) (x_1 Int)) (=> (distinct x_1 x X x_0) false))");
    }

    @Test
    void boundVariablesAreNotQuantifiedAgain() {
        Term phi = new Term.Quant(hornassistant.smtlib.syntax.Quantifier.EXISTS, List.of(Y), Term.app(Op.LT, X, Y));
        assertThat(formula(List.of(), phi, Head.FALSE)).isEqualTo("(forall ((x Int)) (=> (exists ((y Int)) (< x y)) false))");
    }
}
