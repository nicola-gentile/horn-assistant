package hornassistant.smtlib.print;

import hornassistant.smtlib.sort.Sort;
import hornassistant.smtlib.term.Op;
import hornassistant.smtlib.term.Term;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Renders sorts and terms as one-line SMT-LIB text. */
public final class Printer {

    private Printer() {}

    public static String sort(Sort sort) {
        return switch (sort) {
            case Sort.Bool _ -> "Bool";
            case Sort.Int _ -> "Int";
            case Sort.Real _ -> "Real";
            case Sort.Array(var index, var element) -> "(Array " + sort(index) + " " + sort(element) + ")";
        };
    }

    public static String term(Term term) {
        var out = new StringBuilder();
        term(term, out);
        return out.toString();
    }

    /** Variables declared by a quantifier: {@code ((x Int) (y Int))}. */
    public static String sortedVars(List<Term.Var> vars) {
        return vars.stream().map(v -> "(" + Symbols.print(v.name()) + " " + sort(v.sort()) + ")")
                .collect(Collectors.joining(" ", "(", ")"));
    }

    private static void term(Term term, StringBuilder out) {
        switch (term) {
            case Term.Var(var name, var _) -> out.append(Symbols.print(name));
            case Term.BoolLit(var value) -> out.append(value);
            case Term.IntLit(var value) -> out.append(value);
            case Term.RealLit(var value) -> out.append(value.toPlainString());
            case Term.App(var op, var indices, var args, var sort) -> application(function(op, indices, sort), args, out);
            case Term.PredApp(var predicate, var args) when args.isEmpty() -> out.append(Symbols.print(predicate.name()));
            case Term.PredApp(var predicate, var args) -> application(Symbols.print(predicate.name()), args, out);
            case Term.Quant(var quantifier, var vars, var body) -> {
                out.append('(').append(quantifier.keyword()).append(' ').append(sortedVars(vars)).append(' ');
                term(body, out);
                out.append(')');
            }
        }
    }

    private static String function(Op op, List<BigInteger> indices, Sort sort) {
        if (op == Op.CONST_ARRAY) {
            return "(as const " + sort(sort) + ")";
        }
        return indices.isEmpty()
                ? op.symbol()
                : Stream.concat(Stream.of("_", op.symbol()), indices.stream().map(BigInteger::toString))
                        .collect(Collectors.joining(" ", "(", ")"));
    }

    private static void application(String head, List<Term> args, StringBuilder out) {
        out.append('(').append(head);
        for (Term arg : args) {
            out.append(' ');
            term(arg, out);
        }
        out.append(')');
    }
}
