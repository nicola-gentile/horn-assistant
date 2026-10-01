package hornassistant.chc;

import hornassistant.smtlib.sort.Sort;
import hornassistant.smtlib.term.Term;
import hornassistant.smtlib.term.Terms;
import java.util.List;
import java.util.stream.Stream;

/**
 * A clause {@code (A1 ∧ ... ∧ Ak ∧ φ) → H}. The constraint is a predicate-free formula, and the arguments
 * of the body and head applications are predicate-free as well.
 */
public record Clause(List<Term.PredApp> body, Term constraint, Head head) {
    public Clause {
        body = List.copyOf(body);
        if (!constraint.sort().equals(Sort.BOOL)) {
            throw new IllegalArgumentException("constraint is not a formula: " + constraint);
        }
        if (Terms.containsPredicate(constraint)) {
            throw new IllegalArgumentException("constraint contains a predicate: " + constraint);
        }
        if (Stream.concat(body.stream(), headApp(head).stream()).flatMap(a -> a.args().stream()).anyMatch(Terms::containsPredicate)) {
            throw new IllegalArgumentException("predicate inside a predicate argument");
        }
    }

    private static List<Term.PredApp> headApp(Head head) {
        return switch (head) {
            case Head.Pred(var app) -> List.of(app);
            case Head.False _ -> List.of();
        };
    }

    public boolean isFact() {
        return body.isEmpty();
    }

    public boolean isQuery() {
        return switch (head) {
            case Head.Pred _ -> false;
            case Head.False _ -> true;
        };
    }

    /** The body applications followed by the head application, if any. */
    public List<Term.PredApp> applications() {
        return Stream.concat(body.stream(), headApp(head).stream()).toList();
    }
}
