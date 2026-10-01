package hornassistant.smtlib.sort;

import hornassistant.smtlib.syntax.Command;
import hornassistant.smtlib.syntax.SortedVar;
import hornassistant.smtlib.syntax.TermExpr;
import java.util.Set;

/** Collects every symbol a script declares, binds or uses, so that fresh names can avoid all of them. */
final class SyntaxNames {

    private SyntaxNames() {}

    static void collect(Command command, Set<String> out) {
        switch (command) {
            case Command.DeclareFun(var name, var _, var _) -> out.add(name);
            case Command.DeclareConst(var name, var _) -> out.add(name);
            case Command.DefineFun(var name, var params, var _, var body) -> {
                out.add(name);
                params.forEach(p -> out.add(p.name()));
                collect(body, out);
            }
            case Command.Assert(var term) -> collect(term, out);
            case Command.SetLogic _, Command.CheckSat _, Command.Unsupported _, Command.Other _ -> {}
        }
    }

    private static void collect(TermExpr term, Set<String> out) {
        switch (term) {
            case TermExpr.Constant _ -> {}
            case TermExpr.QualId q -> out.add(q.id().symbol());
            case TermExpr.App(var f, var args) -> {
                out.add(f.id().symbol());
                args.forEach(a -> collect(a, out));
            }
            case TermExpr.Let(var bindings, var body) -> {
                bindings.forEach(b -> {
                    out.add(b.name());
                    collect(b.value(), out);
                });
                collect(body, out);
            }
            case TermExpr.Quantified(var _, var vars, var body) -> {
                vars.stream().map(SortedVar::name).forEach(out::add);
                collect(body, out);
            }
            case TermExpr.Annotated(var inner, var _) -> collect(inner, out);
            case TermExpr.Match(var scrutinee) -> collect(scrutinee, out);
        }
    }
}
