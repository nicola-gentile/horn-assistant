package hornassistant.smtlib.term;

import hornassistant.chc.Predicate;
import hornassistant.smtlib.sort.Sort;
import hornassistant.smtlib.sort.Theory;
import hornassistant.smtlib.syntax.Quantifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** A well-sorted term. Every constructor checks the sort invariants of its variant. */
public sealed interface Term {

    Term TRUE = new BoolLit(true);
    Term FALSE = new BoolLit(false);

    Sort sort();

    record Var(String name, Sort sort) implements Term {}

    record BoolLit(boolean value) implements Term {
        @Override
        public Sort sort() {
            return Sort.BOOL;
        }
    }

    /** A non-negative integer literal; negative numbers are written {@code (- n)}. */
    record IntLit(BigInteger value) implements Term {
        public IntLit {
            if (value.signum() < 0) {
                throw new IllegalArgumentException("negative integer literal " + value);
            }
        }

        @Override
        public Sort sort() {
            return Sort.INT;
        }
    }

    /**
     * A non-negative real literal in canonical form: no trailing zeros beyond the first fractional digit,
     * so that equal values are equal records.
     */
    record RealLit(BigDecimal value) implements Term {
        public RealLit {
            if (value.signum() < 0) {
                throw new IllegalArgumentException("negative real literal " + value);
            }
            BigDecimal stripped = value.stripTrailingZeros();
            value = stripped.scale() < 1 ? stripped.setScale(1) : stripped;
        }

        @Override
        public Sort sort() {
            return Sort.REAL;
        }
    }

    /** An application of a theory operator. The constructor checks it against the theory signature. */
    record App(Op op, List<BigInteger> indices, List<Term> args, Sort sort) implements Term {
        public App {
            indices = List.copyOf(indices);
            args = List.copyOf(args);
            Sort expected = Theory.check(op, indices, args.stream().map(Term::sort).toList(),
                    op == Op.CONST_ARRAY ? Optional.of(sort) : Optional.empty());
            if (!expected.equals(sort)) {
                throw new IllegalArgumentException("application of " + op.symbol() + " has sort " + expected + ", not " + sort);
            }
        }
    }

    /** An application of a predicate; for a Boolean predicate {@code args} is empty. */
    record PredApp(Predicate predicate, List<Term> args) implements Term {
        public PredApp {
            args = List.copyOf(args);
            if (args.size() != predicate.arity()) {
                throw new IllegalArgumentException(
                        "predicate " + predicate.name() + " expects " + predicate.arity() + " arguments, got " + args.size());
            }
            for (int i = 0; i < args.size(); i++) {
                if (!args.get(i).sort().equals(predicate.argSorts().get(i))) {
                    throw new IllegalArgumentException("argument " + (i + 1) + " of " + predicate.name() + " has sort "
                            + args.get(i).sort() + ", expected " + predicate.argSorts().get(i));
                }
            }
        }

        @Override
        public Sort sort() {
            return Sort.BOOL;
        }
    }

    record Quant(Quantifier quantifier, List<Var> vars, Term body) implements Term {
        public Quant {
            vars = List.copyOf(vars);
            if (vars.isEmpty()) {
                throw new IllegalArgumentException("quantifier without variables");
            }
            if (!body.sort().equals(Sort.BOOL)) {
                throw new IllegalArgumentException("quantifier body has sort " + body.sort());
            }
            var names = new HashSet<String>();
            for (Var v : vars) {
                if (!names.add(v.name())) {
                    throw new IllegalArgumentException("variable " + v.name() + " is bound twice");
                }
            }
        }

        @Override
        public Sort sort() {
            return Sort.BOOL;
        }
    }

    // ------------------------------------------------------------ smart constructors

    /** Applies a non-indexed operator, computing the result sort from the theory signature. */
    static App app(Op op, List<Term> args) {
        Sort sort = Theory.check(op, List.of(), args.stream().map(Term::sort).toList(), Optional.empty());
        return new App(op, List.of(), args, sort);
    }

    static App app(Op op, Term... args) {
        return app(op, List.of(args));
    }

    static App constArray(Sort.Array sort, Term element) {
        return new App(Op.CONST_ARRAY, List.of(), List.of(element), sort);
    }

    static IntLit intLit(long value) {
        return new IntLit(BigInteger.valueOf(value));
    }
}
