package hornassistant.smtlib.print;

import hornassistant.smtlib.syntax.Attribute;
import hornassistant.smtlib.syntax.Binding;
import hornassistant.smtlib.syntax.Command;
import hornassistant.smtlib.syntax.Identifier;
import hornassistant.smtlib.syntax.Index;
import hornassistant.smtlib.syntax.SExpr;
import hornassistant.smtlib.syntax.Script;
import hornassistant.smtlib.syntax.SortExpr;
import hornassistant.smtlib.syntax.SortedVar;
import hornassistant.smtlib.syntax.TermExpr;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Prints the syntax AST back as SMT-LIB text, one command per line. A {@code match} term keeps only its
 * scrutinee, so it is printed with a single catch-all case.
 */
public final class SyntaxPrinter {

    private SyntaxPrinter() {}

    public static String print(Script script) {
        return script.commands().stream().map(SyntaxPrinter::command).map(c -> c + "\n").collect(Collectors.joining());
    }

    private static String list(Stream<String> items) {
        return items.collect(Collectors.joining(" ", "(", ")"));
    }

    private static <T> String list(List<T> items, Function<T, String> f) {
        return list(items.stream().map(f));
    }

    private static String app(String head, Stream<String> args) {
        return list(Stream.concat(Stream.of(head), args));
    }

    public static String command(Command command) {
        return switch (command) {
            case Command.SetLogic(var logic) -> "(set-logic " + Symbols.print(logic) + ")";
            case Command.DeclareFun(var name, var args, var result) ->
                    "(declare-fun " + Symbols.print(name) + " " + list(args, SyntaxPrinter::sort) + " " + sort(result) + ")";
            case Command.DeclareConst(var name, var sort) -> "(declare-const " + Symbols.print(name) + " " + sort(sort) + ")";
            case Command.DefineFun(var name, var params, var result, var body) ->
                    "(define-fun " + Symbols.print(name) + " " + list(params, SyntaxPrinter::sortedVar) + " " + sort(result)
                            + " " + term(body) + ")";
            case Command.Assert(var term) -> "(assert " + term(term) + ")";
            case Command.CheckSat() -> "(check-sat)";
            case Command.Unsupported(var name, var args) -> app(name, args.stream().map(SyntaxPrinter::sExpr));
            case Command.Other(var name, var args) -> app(name, args.stream().map(SyntaxPrinter::sExpr));
        };
    }

    public static String identifier(Identifier id) {
        return id.indices().isEmpty()
                ? Symbols.print(id.symbol())
                : app("_", Stream.concat(Stream.of(Symbols.print(id.symbol())), id.indices().stream().map(SyntaxPrinter::index)));
    }

    private static String index(Index index) {
        return switch (index) {
            case Index.Num(var value) -> value.toString();
            case Index.Sym(var name) -> Symbols.print(name);
        };
    }

    public static String sort(SortExpr sort) {
        return sort.params().isEmpty()
                ? identifier(sort.id())
                : app(identifier(sort.id()), sort.params().stream().map(SyntaxPrinter::sort));
    }

    private static String sortedVar(SortedVar v) {
        return "(" + Symbols.print(v.name()) + " " + sort(v.sort()) + ")";
    }

    private static String binding(Binding b) {
        return "(" + Symbols.print(b.name()) + " " + term(b.value()) + ")";
    }

    public static String term(TermExpr term) {
        return switch (term) {
            case TermExpr.Constant c -> constant(c);
            case TermExpr.QualId q -> qualId(q);
            case TermExpr.App(var f, var args) -> app(qualId(f), args.stream().map(SyntaxPrinter::term));
            case TermExpr.Let(var bindings, var body) -> "(let " + list(bindings, SyntaxPrinter::binding) + " " + term(body) + ")";
            case TermExpr.Quantified(var q, var vars, var body) ->
                    "(" + q.keyword() + " " + list(vars, SyntaxPrinter::sortedVar) + " " + term(body) + ")";
            case TermExpr.Annotated(var t, var attributes) ->
                    app("!", Stream.concat(Stream.of(term(t)), attributes.stream().map(SyntaxPrinter::attribute)));
            case TermExpr.Match(var scrutinee) -> "(match " + term(scrutinee) + " ((|_| " + term(scrutinee) + ")))";
        };
    }

    private static String qualId(TermExpr.QualId q) {
        return q.as().map(s -> "(as " + identifier(q.id()) + " " + sort(s) + ")").orElseGet(() -> identifier(q.id()));
    }

    public static String constant(TermExpr.Constant constant) {
        return switch (constant) {
            case TermExpr.Numeral(var value) -> value.toString();
            case TermExpr.Decimal(var value) -> value.toPlainString();
            case TermExpr.Hex(var digits) -> "#x" + digits;
            case TermExpr.Bin(var digits) -> "#b" + digits;
            case TermExpr.Str(var value) -> "\"" + value.replace("\"", "\"\"") + "\"";
        };
    }

    private static String attribute(Attribute a) {
        return ":" + a.keyword() + a.value().map(v -> " " + sExpr(v)).orElse("");
    }

    public static String sExpr(SExpr s) {
        return switch (s) {
            case SExpr.Literal(var c) -> constant(c);
            case SExpr.Symbol(var name) -> Symbols.print(name);
            case SExpr.Keyword(var name) -> ":" + name;
            case SExpr.Reserved(var word) -> word;
            case SExpr.ListExpr(var items) -> list(items, SyntaxPrinter::sExpr);
        };
    }
}
