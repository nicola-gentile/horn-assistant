package hornassistant.transform;

import hornassistant.chc.Clause;
import hornassistant.chc.Head;
import hornassistant.smtlib.term.Op;
import hornassistant.smtlib.term.Term;
import hornassistant.smtlib.term.Terms;
import hornassistant.system.ChcSystem;
import hornassistant.system.SingleSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The {@code norm} transformation (specification §7.2): flattens the top-level conjunction of each
 * constraint and makes every predicate argument a variable that occurs in no other argument position of
 * its clause, adding equalities for what it replaces.
 */
public final class Norm implements Transformation {

    @Override
    public String name() {
        return "norm";
    }

    @Override
    public String help() {
        return "flatten constraints and make every predicate argument a distinct variable";
    }

    @Override
    public void transform(ChcSystem system) {
        switch (system) {
            case SingleSystem single -> {
                var names = new NameSupply(symbols(single));
                for (int e : single.hyperedges()) {
                    Clause c = normalize(single.hyperedge(e), names);
                    single.replaceHyperedge(e, c.body(), c.constraint(), c.head());
                }
            }
        }
    }

    /** Every symbol of the system plus the theory symbols: fresh names must avoid all of them. */
    private static Set<String> symbols(SingleSystem system) {
        var used = new HashSet<String>();
        system.predicates().forEach(p -> used.add(p.name()));
        system.clauses().forEach(c -> used.addAll(Terms.names(c)));
        used.add("true");
        used.add("false");
        Stream.of(Op.values()).forEach(op -> used.add(op.symbol()));
        return used;
    }

    static Clause normalize(Clause clause, NameSupply names) {
        List<Term> conjuncts = new ArrayList<>(flatten(clause.constraint()));
        List<Term.PredApp> apps = clause.applications();

        Map<Term, Integer> occurrences = new HashMap<>();
        apps.forEach(a -> a.args().forEach(t -> occurrences.merge(t, 1, Integer::sum)));

        List<Term.PredApp> rewritten = new ArrayList<>();
        for (Term.PredApp app : apps) {
            List<Term> args = new ArrayList<>();
            for (Term t : app.args()) {
                if (isVar(t) && occurrences.get(t) == 1) {
                    args.add(t);
                } else {
                    var v = new Term.Var(names.fresh(prefix(t)), t.sort());
                    conjuncts.add(Term.app(Op.EQ, v, t));
                    args.add(v);
                }
            }
            rewritten.add(new Term.PredApp(app.predicate(), args));
        }

        List<Term.PredApp> body = rewritten.subList(0, clause.body().size());
        Head head = switch (clause.head()) {
            case Head.Pred _ -> new Head.Pred(rewritten.getLast());
            case Head.False f -> f;
        };
        return new Clause(body, conjunction(conjuncts), head);
    }

    /** The top-level conjuncts of {@code phi}; {@code true} has none. */
    static List<Term> flatten(Term phi) {
        return switch (phi) {
            case Term.App(var op, var _, var args, var _) when op == Op.AND ->
                    args.stream().flatMap(a -> flatten(a).stream()).toList();
            case Term.BoolLit(var value) when value -> List.of();
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.PredApp _, Term.Quant _ -> List.of(phi);
        };
    }

    private static Term conjunction(List<Term> conjuncts) {
        return switch (conjuncts.size()) {
            case 0 -> Term.TRUE;
            case 1 -> conjuncts.getFirst();
            default -> Term.app(Op.AND, conjuncts);
        };
    }

    private static boolean isVar(Term t) {
        return switch (t) {
            case Term.Var _ -> true;
            case Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.PredApp _, Term.Quant _ -> false;
        };
    }

    /** A fresh variable is named after the variable it replaces, or {@code v} for any other term. */
    private static String prefix(Term t) {
        return switch (t) {
            case Term.Var(var name, var _) -> name;
            case Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.PredApp _, Term.Quant _ -> "v";
        };
    }
}
