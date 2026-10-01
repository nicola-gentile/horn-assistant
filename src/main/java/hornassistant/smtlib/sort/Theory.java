package hornassistant.smtlib.sort;

import static hornassistant.smtlib.sort.Sort.BOOL;
import static hornassistant.smtlib.sort.Sort.INT;
import static hornassistant.smtlib.sort.Sort.REAL;

import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.term.Op;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Signatures of the theory operators (Core, Ints, Reals, Reals_Ints, ArraysEx). All functions are pure
 * and report an ill-sorted application with an {@link IllSortedException} whose message is the reason.
 */
public final class Theory {

    private Theory() {}

    /**
     * Checks an application and returns its result sort.
     *
     * @param declared the sort given by {@code (as ...)}; required for {@link Op#CONST_ARRAY}, ignored otherwise
     */
    public static Sort check(Op op, List<BigInteger> indices, List<Sort> args, Optional<Sort> declared) {
        if (!indices.isEmpty()) {
            throw new IllSortedException(op.symbol() + " takes no indices");
        }
        return switch (op) {
            case NOT -> fixed(op, args, List.of(BOOL), BOOL);
            case AND, OR, XOR -> allOf(op, args, 0, BOOL, BOOL);
            case IMPLIES -> fixed(op, args, List.of(BOOL, BOOL), BOOL);
            case EQ, DISTINCT -> {
                atLeast(op, args, 2);
                sameSort(op, args);
                yield BOOL;
            }
            case ITE -> {
                arity(op, args, 3);
                expect(op, 1, args.get(0), BOOL);
                expect(op, 3, args.get(2), args.get(1));
                yield args.get(1);
            }
            case ADD, MUL -> numeric(op, args, 2);
            case SUB -> numeric(op, args, 1);
            case DIV_REAL -> allOf(op, args, 2, REAL, REAL);
            case IDIV -> allOf(op, args, 2, INT, INT);
            case MOD -> fixed(op, args, List.of(INT, INT), INT);
            case ABS -> fixed(op, args, List.of(INT), INT);
            case LE, LT, GE, GT -> {
                numeric(op, args, 2);
                yield BOOL;
            }
            case TO_REAL -> fixed(op, args, List.of(INT), REAL);
            case TO_INT -> fixed(op, args, List.of(REAL), INT);
            case IS_INT -> fixed(op, args, List.of(REAL), BOOL);
            case SELECT -> {
                arity(op, args, 2);
                Sort.Array array = array(op, args.get(0));
                expect(op, 2, args.get(1), array.index());
                yield array.element();
            }
            case STORE -> {
                arity(op, args, 3);
                Sort.Array array = array(op, args.get(0));
                expect(op, 2, args.get(1), array.index());
                expect(op, 3, args.get(2), array.element());
                yield array;
            }
            case CONST_ARRAY -> {
                arity(op, args, 1);
                Sort sort = declared.orElseThrow(() -> new IllSortedException("const needs an (as const (Array I E)) sort"));
                Sort.Array array = switch (sort) {
                    case Sort.Array a -> a;
                    case Sort.Bool _, Sort.Int _, Sort.Real _ ->
                            throw new IllSortedException("const needs an array sort, got " + Printer.sort(sort));
                };
                expect(op, 1, args.get(0), array.element());
                yield array;
            }
        };
    }

    private static void arity(Op op, List<Sort> args, int n) {
        if (args.size() != n) {
            throw new IllSortedException(op.symbol() + " expects " + n + " argument" + (n == 1 ? "" : "s") + ", got " + args.size());
        }
    }

    private static void atLeast(Op op, List<Sort> args, int n) {
        if (args.size() < n) {
            throw new IllSortedException(op.symbol() + " expects at least " + n + " argument" + (n == 1 ? "" : "s") + ", got " + args.size());
        }
    }

    private static void expect(Op op, int position, Sort actual, Sort expected) {
        if (!actual.equals(expected)) {
            throw new IllSortedException("argument " + position + " of " + op.symbol() + " has sort " + Printer.sort(actual)
                    + ", expected " + Printer.sort(expected));
        }
    }

    private static Sort fixed(Op op, List<Sort> args, List<Sort> expected, Sort result) {
        arity(op, args, expected.size());
        for (int i = 0; i < args.size(); i++) {
            expect(op, i + 1, args.get(i), expected.get(i));
        }
        return result;
    }

    private static Sort allOf(Op op, List<Sort> args, int min, Sort argSort, Sort result) {
        atLeast(op, args, min);
        for (int i = 0; i < args.size(); i++) {
            expect(op, i + 1, args.get(i), argSort);
        }
        return result;
    }

    private static void sameSort(Op op, List<Sort> args) {
        for (int i = 1; i < args.size(); i++) {
            expect(op, i + 1, args.get(i), args.get(0));
        }
    }

    /** All arguments share one sort, Int or Real, which is returned. */
    private static Sort numeric(Op op, List<Sort> args, int min) {
        atLeast(op, args, min);
        Sort first = args.get(0);
        if (!first.equals(INT) && !first.equals(REAL)) {
            throw new IllSortedException("argument 1 of " + op.symbol() + " has sort " + Printer.sort(first) + ", expected Int or Real");
        }
        sameSort(op, args);
        return first;
    }

    private static Sort.Array array(Op op, Sort sort) {
        return switch (sort) {
            case Sort.Array a -> a;
            case Sort.Bool _, Sort.Int _, Sort.Real _ ->
                    throw new IllSortedException("argument 1 of " + op.symbol() + " has sort " + Printer.sort(sort) + ", expected an array");
        };
    }
}
