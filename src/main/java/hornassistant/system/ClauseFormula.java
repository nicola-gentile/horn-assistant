package hornassistant.system;

import hornassistant.chc.Clause;
import hornassistant.chc.Head;
import hornassistant.smtlib.syntax.Quantifier;
import hornassistant.smtlib.term.Op;
import hornassistant.smtlib.term.Term;
import hornassistant.smtlib.term.Terms;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

/** The formula of a clause (specification §5.2). */
public final class ClauseFormula {

    private ClauseFormula() {}

    public static Term of(Clause clause) {
        return close(implication(clause));
    }

    private static Term implication(Clause clause) {
        Term head = switch (clause.head()) {
            case Head.Pred(var app) -> app;
            case Head.False _ -> Term.FALSE;
        };
        boolean trivial = clause.constraint().equals(Term.TRUE);
        if (clause.body().isEmpty()) {
            return trivial ? head : Term.app(Op.IMPLIES, clause.constraint(), head);
        }
        List<Term> conjuncts = trivial ? List.of() : topLevelConjuncts(clause.constraint());
        Term premise = Term.app(Op.AND, Stream.concat(clause.body().stream(), conjuncts.stream()).toList());
        return Term.app(Op.IMPLIES, premise, head);
    }

    private static List<Term> topLevelConjuncts(Term constraint) {
        return switch (constraint) {
            case Term.App(var op, var _, var args, var _) when op == Op.AND -> args;
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.PredApp _, Term.Quant _ ->
                    List.of(constraint);
        };
    }

    /** Wraps {@code formula} in a {@code forall} over its free variables, sorted by name. */
    private static Term close(Term formula) {
        List<Term.Var> vars = Terms.freeVars(formula).stream().sorted(Comparator.comparing(Term.Var::name)).toList();
        var names = new HashSet<String>();
        for (Term.Var v : vars) {
            if (!names.add(v.name())) {
                throw new IllegalStateException("two free variables named " + v.name() + " with different sorts");
            }
        }
        return vars.isEmpty() ? formula : new Term.Quant(Quantifier.FORALL, vars, formula);
    }
}
