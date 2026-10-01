package hornassistant.smtlib.term;

import hornassistant.transform.NameSupply;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;

/** Pure functions over terms. */
public final class Terms {

    private Terms() {}

    /** The free variables of {@code term}, in order of first occurrence. */
    public static SequencedSet<Term.Var> freeVars(Term term) {
        var result = new LinkedHashSet<Term.Var>();
        collectFreeVars(term, Set.of(), result);
        return result;
    }

    private static void collectFreeVars(Term term, Set<Term.Var> bound, SequencedSet<Term.Var> out) {
        switch (term) {
            case Term.Var v -> {
                if (!bound.contains(v)) {
                    out.add(v);
                }
            }
            case Term.BoolLit _, Term.IntLit _, Term.RealLit _ -> {}
            case Term.App app -> app.args().forEach(a -> collectFreeVars(a, bound, out));
            case Term.PredApp app -> app.args().forEach(a -> collectFreeVars(a, bound, out));
            case Term.Quant q -> {
                var inner = new HashSet<>(bound);
                inner.addAll(q.vars());
                collectFreeVars(q.body(), inner, out);
            }
        }
    }

    /**
     * Replaces the free occurrences of the variables in {@code substitution}. A quantifier whose bound
     * variable has the name of a free variable of an inserted term is renamed to a fresh name first, so
     * nothing is captured. Each replacement must have the sort of the variable it replaces.
     */
    public static Term substitute(Term term, Map<Term.Var, Term> substitution, NameSupply names) {
        substitution.forEach((v, t) -> {
            if (!v.sort().equals(t.sort())) {
                throw new IllegalArgumentException("substituting " + t + " for " + v + " changes the sort");
            }
        });
        return substitution.isEmpty() ? term : subst(term, substitution, names);
    }

    private static Term subst(Term term, Map<Term.Var, Term> substitution, NameSupply names) {
        return switch (term) {
            case Term.Var v -> substitution.getOrDefault(v, v);
            case Term.BoolLit _, Term.IntLit _, Term.RealLit _ -> term;
            case Term.App(var op, var indices, var args, var sort) -> new Term.App(op, indices, substAll(args, substitution, names), sort);
            case Term.PredApp(var predicate, var args) -> new Term.PredApp(predicate, substAll(args, substitution, names));
            case Term.Quant(var quantifier, var vars, var body) -> {
                var inner = new LinkedHashMap<>(substitution);
                vars.forEach(inner::remove);
                if (inner.isEmpty()) {
                    yield term;
                }
                Set<String> captured = new HashSet<>();
                for (Term replacement : inner.values()) {
                    freeVars(replacement).forEach(v -> captured.add(v.name()));
                }
                var newVars = vars.stream()
                        .map(v -> captured.contains(v.name()) ? new Term.Var(names.fresh(v.name()), v.sort()) : v)
                        .toList();
                for (int i = 0; i < vars.size(); i++) {
                    if (!newVars.get(i).equals(vars.get(i))) {
                        inner.put(vars.get(i), newVars.get(i));
                    }
                }
                yield new Term.Quant(quantifier, newVars, subst(body, inner, names));
            }
        };
    }

    private static List<Term> substAll(List<Term> terms, Map<Term.Var, Term> substitution, NameSupply names) {
        return terms.stream().map(t -> subst(t, substitution, names)).toList();
    }

    public static boolean containsPredicate(Term term) {
        return predicateCount(term) > 0;
    }

    /** The number of predicate applications anywhere in {@code term}. */
    public static int predicateCount(Term term) {
        return switch (term) {
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _ -> 0;
            case Term.App app -> app.args().stream().mapToInt(Terms::predicateCount).sum();
            case Term.PredApp app -> 1 + app.args().stream().mapToInt(Terms::predicateCount).sum();
            case Term.Quant q -> predicateCount(q.body());
        };
    }

    /** Every variable name (free or bound) and predicate name occurring in {@code term}. */
    public static Set<String> names(Term term) {
        var out = new HashSet<String>();
        collectNames(term, out);
        return out;
    }

    private static void collectNames(Term term, Set<String> out) {
        switch (term) {
            case Term.Var v -> out.add(v.name());
            case Term.BoolLit _, Term.IntLit _, Term.RealLit _ -> {}
            case Term.App app -> app.args().forEach(a -> collectNames(a, out));
            case Term.PredApp app -> {
                out.add(app.predicate().name());
                app.args().forEach(a -> collectNames(a, out));
            }
            case Term.Quant q -> {
                q.vars().forEach(v -> out.add(v.name()));
                collectNames(q.body(), out);
            }
        }
    }
}
