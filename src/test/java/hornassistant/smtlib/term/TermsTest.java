package hornassistant.smtlib.term;

import static hornassistant.smtlib.sort.Sort.BOOL;
import static hornassistant.smtlib.sort.Sort.INT;
import static org.assertj.core.api.Assertions.assertThat;

import hornassistant.chc.Predicate;
import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.syntax.Quantifier;
import hornassistant.transform.NameSupply;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TermsTest {

    private static final Term.Var X = new Term.Var("x", INT);
    private static final Term.Var Y = new Term.Var("y", INT);
    private static final Term.Var Z = new Term.Var("z", INT);
    private static final Predicate P = new Predicate("P", List.of(INT));

    private static Term forall(Term.Var v, Term body) {
        return new Term.Quant(Quantifier.FORALL, List.of(v), body);
    }

    @Test
    void freeVarsAreInFirstOccurrenceOrderAndSkipBoundOnes() {
        Term t = Term.app(Op.AND, Term.app(Op.LT, Y, X), forall(Z, Term.app(Op.LT, Z, Y)), forall(X, Term.app(Op.LT, X, Z)));
        assertThat(Terms.freeVars(t)).containsExactly(Y, X, Z);
    }

    @Test
    void freeVarsLookInsidePredicateArguments() {
        assertThat(Terms.freeVars(new Term.PredApp(P, List.of(Term.app(Op.ADD, X, Y))))).containsExactly(X, Y);
    }

    @Test
    void substitutesFreeOccurrencesOnly() {
        Term t = Term.app(Op.AND, Term.app(Op.LT, X, Y), forall(X, Term.app(Op.LT, X, Y)));
        Term result = Terms.substitute(t, Map.of(X, Term.intLit(1)), new NameSupply(List.of("x", "y")));
        assertThat(Printer.term(result)).isEqualTo("(and (< 1 y) (forall ((x Int)) (< x y)))");
    }

    @Test
    void renamesABinderThatWouldCaptureAVariableOfTheReplacement() {
        Term t = forall(Y, Term.app(Op.LT, X, Y));
        Term result = Terms.substitute(t, Map.of(X, Term.app(Op.ADD, Y, Term.intLit(1))), new NameSupply(List.of("x", "y")));
        assertThat(Printer.term(result)).isEqualTo("(forall ((y_0 Int)) (< (+ y 1) y_0))");
    }

    @Test
    void aBinderOfAnotherSortWithTheSameNameAlsoCaptures() {
        var yBool = new Term.Var("y", BOOL);
        Term t = forall(yBool, Term.app(Op.AND, yBool, Term.app(Op.LT, X, Term.intLit(0))));
        Term result = Terms.substitute(t, Map.of(X, Y), new NameSupply(List.of("x", "y")));
        assertThat(Printer.term(result)).isEqualTo("(forall ((y_0 Bool)) (and y_0 (< y 0)))");
    }

    @Test
    void substitutesInsidePredicateArguments() {
        Term result = Terms.substitute(new Term.PredApp(P, List.of(X)), Map.of(X, Y), new NameSupply(List.of()));
        assertThat(result).isEqualTo(new Term.PredApp(P, List.of(Y)));
    }

    @Test
    void countsPredicateOccurrences() {
        var bool = new Predicate("B", List.of());
        Term none = Term.app(Op.LT, X, Y);
        Term two = Term.app(Op.OR, new Term.PredApp(P, List.of(X)), forall(Y, new Term.PredApp(bool, List.of())));
        assertThat(Terms.containsPredicate(none)).isFalse();
        assertThat(Terms.predicateCount(none)).isZero();
        assertThat(Terms.containsPredicate(two)).isTrue();
        assertThat(Terms.predicateCount(two)).isEqualTo(2);
    }

    @Test
    void namesIncludeBoundVariablesAndPredicates() {
        Term t = Term.app(Op.AND, new Term.PredApp(P, List.of(X)), forall(Y, Term.app(Op.LT, Y, Z)));
        assertThat(Terms.names(t)).containsExactlyInAnyOrder("P", "x", "y", "z");
    }
}
