package hornassistant.chc;

import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.print.Symbols;
import hornassistant.smtlib.syntax.Quantifier;
import hornassistant.smtlib.term.Op;
import hornassistant.smtlib.term.Term;
import hornassistant.smtlib.term.Terms;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Turns an asserted formula into a clause (specification §4.2). All functions are pure. */
public final class ClauseExtractor {

    private static final int MAX_TERM_IN_MESSAGE = 200;

    private ClauseExtractor() {}

    private record Split(Term premise, Term conclusion) {}

    /**
     * @throws ChcInputException if a predicate occurs outside a body leaf or the head (error 4), or the
     *     conclusion holds more than one predicate (error 5)
     */
    public static Clause extract(Term assertion) {
        Split split = split(stripForall(assertion));
        List<Term.PredApp> body = bodyLeaves(split.premise());
        Term constraint = remainder(split.premise()).orElse(Term.TRUE);
        body.forEach(ClauseExtractor::checkArguments);

        Term conclusion = split.conclusion();
        return switch (conclusion) {
            case Term.PredApp app -> {
                checkArguments(app);
                yield new Clause(body, constraint, new Head.Pred(app));
            }
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.Quant _ -> {
                int predicates = Terms.predicateCount(conclusion);
                if (predicates >= 2) {
                    throw new ChcInputException("more than one predicate in the head: " + brief(conclusion));
                }
                if (predicates == 1) {
                    throw outside(conclusion);
                }
                yield new Clause(body, addNegated(constraint, conclusion), Head.FALSE);
            }
        };
    }

    private static Term stripForall(Term term) {
        return switch (term) {
            case Term.Quant(var q, var _, var body) when q == Quantifier.FORALL -> stripForall(body);
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.PredApp _, Term.Quant _ -> term;
        };
    }

    /** The premise/conclusion table of §4.2. */
    private static Split split(Term m) {
        return switch (m) {
            case Term.App(var op, var _, var args, var _) when op == Op.IMPLIES -> new Split(args.get(0), args.get(1));
            case Term.App(var op, var _, var args, var _) when op == Op.NOT -> new Split(args.getFirst(), Term.FALSE);
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.PredApp _, Term.Quant _ ->
                    new Split(Term.TRUE, m);
        };
    }

    /** The predicate-application leaves of the {@code and} tree of {@code premise}, left to right. */
    static List<Term.PredApp> bodyLeaves(Term premise) {
        return switch (premise) {
            case Term.App(var op, var _, var args, var _) when op == Op.AND ->
                    args.stream().flatMap(a -> bodyLeaves(a).stream()).toList();
            case Term.PredApp app -> List.of(app);
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.Quant _ -> List.of();
        };
    }

    /**
     * The {@code and} tree of {@code premise} without its predicate-application leaves: an {@code and} left
     * with no children disappears, one left with a single child is replaced by it. Every remaining leaf
     * must be predicate-free.
     */
    static Optional<Term> remainder(Term premise) {
        return switch (premise) {
            case Term.App(var op, var _, var args, var _) when op == Op.AND -> {
                List<Term> kept = args.stream().flatMap(a -> remainder(a).stream()).toList();
                yield switch (kept.size()) {
                    case 0 -> Optional.empty();
                    case 1 -> Optional.of(kept.getFirst());
                    default -> Optional.of(Term.app(Op.AND, kept));
                };
            }
            case Term.PredApp _ -> Optional.empty();
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.Quant _ -> {
                if (Terms.containsPredicate(premise)) {
                    throw outside(premise);
                }
                yield Optional.of(premise);
            }
        };
    }

    private static void checkArguments(Term.PredApp app) {
        if (app.args().stream().anyMatch(Terms::containsPredicate)) {
            throw outside(app);
        }
    }

    /** Adds {@code (not C)} as one more conjunct of {@code constraint}, unless {@code C} is {@code false}. */
    static Term addNegated(Term constraint, Term conclusion) {
        if (conclusion.equals(Term.FALSE)) {
            return constraint;
        }
        Term negated = Term.app(Op.NOT, conclusion);
        return switch (constraint) {
            case Term.BoolLit(var value) when value -> negated;
            case Term.App(var op, var _, var args, var _) when op == Op.AND ->
                    Term.app(Op.AND, Stream.concat(args.stream(), Stream.of(negated)).toList());
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _, Term.App _, Term.PredApp _, Term.Quant _ ->
                    Term.app(Op.AND, constraint, negated);
        };
    }

    private static ChcInputException outside(Term term) {
        String name = firstPredicate(term).map(p -> Symbols.print(p.name())).orElse("?");
        return new ChcInputException("predicate " + name + " occurs outside a body leaf or the head: " + brief(term));
    }

    /** The innermost-leftmost predicate inside the arguments of {@code term}, or {@code term}'s own. */
    private static Optional<Predicate> firstPredicate(Term term) {
        return switch (term) {
            case Term.Var _, Term.BoolLit _, Term.IntLit _, Term.RealLit _ -> Optional.empty();
            case Term.App app -> firstIn(app.args());
            case Term.PredApp app -> firstIn(app.args()).or(() -> Optional.of(app.predicate()));
            case Term.Quant q -> firstPredicate(q.body());
        };
    }

    private static Optional<Predicate> firstIn(List<Term> terms) {
        return terms.stream().flatMap(t -> firstPredicate(t).stream()).findFirst();
    }

    private static String brief(Term term) {
        String text = Printer.term(term);
        return text.length() > MAX_TERM_IN_MESSAGE ? text.substring(0, MAX_TERM_IN_MESSAGE) + "..." : text;
    }

}
