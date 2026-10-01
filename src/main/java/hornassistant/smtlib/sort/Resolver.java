package hornassistant.smtlib.sort;

import hornassistant.chc.ChcInputException;
import hornassistant.chc.Predicate;
import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.print.Symbols;
import hornassistant.smtlib.print.SyntaxPrinter;
import hornassistant.smtlib.syntax.Binding;
import hornassistant.smtlib.syntax.Command;
import hornassistant.smtlib.syntax.Identifier;
import hornassistant.smtlib.syntax.Script;
import hornassistant.smtlib.syntax.SortExpr;
import hornassistant.smtlib.syntax.SortedVar;
import hornassistant.smtlib.syntax.TermExpr;
import hornassistant.smtlib.term.Op;
import hornassistant.smtlib.term.Term;
import hornassistant.smtlib.term.Terms;
import hornassistant.transform.NameSupply;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Turns the syntax AST into well-sorted terms, in one pass over the script:
 * <ul>
 *   <li>builds the signature from {@code declare-fun} (predicates), {@code declare-const} (free constants,
 *       which become variables) and {@code define-fun} (macros);</li>
 *   <li>resolves every symbol, inlines {@code let} and {@code define-fun} without capturing variables,
 *       drops {@code !} annotations and type-checks every application;</li>
 *   <li>promotes Int numerals where a Real is needed and reads {@code (=> a1 ... an c)} as
 *       {@code (=> (and a1 ... an) c)}.</li>
 * </ul>
 * Every problem is reported as a {@link ChcInputException}.
 */
public final class Resolver {

    /** The declared predicates in declaration order and the asserted formulas in assertion order. */
    public record Resolved(List<Predicate> predicates, List<Term> assertions) {
        public Resolved {
            predicates = List.copyOf(predicates);
            assertions = List.copyOf(assertions);
        }
    }

    /** Longest rendering of an offending term in an error message. */
    private static final int MAX_TERM_IN_MESSAGE = 200;

    private static final Set<String> THEORY_SYMBOLS = Stream.concat(
                    Stream.of("true", "false"),
                    Stream.of(Op.values()).filter(op -> op != Op.CONST_ARRAY).map(Op::symbol))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** What a global name stands for. */
    private sealed interface Global {
        record Pred(Predicate predicate) implements Global {}

        record Const(Term.Var var) implements Global {}

        record Macro(List<Term.Var> params, Term body) implements Global {}
    }

    /** What a local name stands for. */
    private sealed interface Local {
        record Bound(Term.Var var) implements Local {}

        record LetValue(Term value) implements Local {}
    }

    /**
     * The local scope. {@code capturable} holds the names of the free variables of the let values in
     * scope: a quantifier binding one of them must be renamed, or the inlined value would be captured.
     */
    private record Env(Map<String, Local> locals, Set<String> capturable) {
        static final Env EMPTY = new Env(Map.of(), Set.of());

        Env {
            locals = Map.copyOf(locals);
            capturable = Set.copyOf(capturable);
        }

        Env with(Map<String, Local> more, Set<String> moreCapturable) {
            var newLocals = new HashMap<>(locals);
            newLocals.putAll(more);
            var newCapturable = new HashSet<>(capturable);
            newCapturable.addAll(moreCapturable);
            return new Env(newLocals, newCapturable);
        }
    }

    // State of one resolution run; a Resolver never outlives a call to resolve().
    private final Map<String, Global> globals = new LinkedHashMap<>();
    private final List<Predicate> predicates = new ArrayList<>();
    /** Names of the free variables (declared constants) of all macro bodies. */
    private final Set<String> macroFreeNames = new HashSet<>();
    private final NameSupply names;

    private Resolver(NameSupply names) {
        this.names = names;
    }

    public static Resolved resolve(Script script) {
        var seed = new HashSet<>(THEORY_SYMBOLS);
        script.commands().forEach(c -> SyntaxNames.collect(c, seed));
        var resolver = new Resolver(new NameSupply(seed));
        var assertions = new ArrayList<Term>();
        for (Command command : script.commands()) {
            resolver.command(command).ifPresent(assertions::add);
        }
        return new Resolved(resolver.predicates, assertions);
    }

    // ------------------------------------------------------------ commands

    /** Processes one command and returns the asserted formula, if it is an {@code assert}. */
    private Optional<Term> command(Command command) {
        switch (command) {
            case Command.SetLogic _, Command.CheckSat _, Command.Other _ -> {}
            case Command.Unsupported(var name, var _) -> throw new ChcInputException("unsupported command: " + name);
            case Command.DeclareFun(var name, var args, var result) -> {
                Sort resultSort = sort(result);
                if (!resultSort.equals(Sort.BOOL)) {
                    throw new ChcInputException(Symbols.print(name) + " is declared with result sort " + Printer.sort(resultSort)
                            + "; only predicates (result sort Bool) can be declared");
                }
                checkUndeclared(name);
                var predicate = new Predicate(name, args.stream().map(Resolver::sort).toList());
                globals.put(name, new Global.Pred(predicate));
                predicates.add(predicate);
            }
            case Command.DeclareConst(var name, var sort) -> {
                Sort s = sort(sort);
                checkUndeclared(name);
                globals.put(name, new Global.Const(new Term.Var(name, s)));
            }
            case Command.DefineFun(var name, var params, var result, var body) -> defineFun(name, params, result, body);
            case Command.Assert(var expr) -> {
                Term term = term(expr, Env.EMPTY);
                if (!term.sort().equals(Sort.BOOL)) {
                    throw illSorted(expr, "asserted term has sort " + Printer.sort(term.sort()) + ", expected Bool");
                }
                return Optional.of(term);
            }
        }
        return Optional.empty();
    }

    private void defineFun(String name, List<SortedVar> params, SortExpr result, TermExpr body) {
        Sort resultSort = sort(result);
        checkUndeclared(name);
        checkDistinct(params.stream().map(SortedVar::name).toList());
        var locals = new HashMap<String, Local>();
        var vars = new ArrayList<Term.Var>();
        for (SortedVar p : params) {
            // A parameter must not share its name with a constant that an inlined macro brings in.
            String internal = macroFreeNames.contains(p.name()) ? names.fresh(p.name()) : p.name();
            var v = new Term.Var(internal, sort(p.sort()));
            vars.add(v);
            locals.put(p.name(), new Local.Bound(v));
        }
        Term resolvedBody = term(body, Env.EMPTY.with(locals, Set.of()));
        if (!resolvedBody.sort().equals(resultSort)) {
            throw illSorted(body, Symbols.print(name) + " is declared with sort " + Printer.sort(resultSort)
                    + ", but its body has sort " + Printer.sort(resolvedBody.sort()));
        }
        Terms.freeVars(resolvedBody).stream().filter(v -> !vars.contains(v)).forEach(v -> macroFreeNames.add(v.name()));
        globals.put(name, new Global.Macro(vars, resolvedBody));
    }

    private void checkUndeclared(String name) {
        if (globals.containsKey(name) || THEORY_SYMBOLS.contains(name)) {
            throw new ChcInputException(Symbols.print(name) + " is already declared");
        }
    }

    private static void checkDistinct(List<String> boundNames) {
        var seen = new HashSet<String>();
        for (String n : boundNames) {
            if (!seen.add(n)) {
                throw new ChcInputException("variable " + Symbols.print(n) + " is bound twice");
            }
        }
    }

    // ------------------------------------------------------------ sorts

    static Sort sort(SortExpr expr) {
        if (expr.id().indices().isEmpty()) {
            switch (expr.id().symbol()) {
                case "Bool" -> {
                    if (expr.params().isEmpty()) {
                        return Sort.BOOL;
                    }
                }
                case "Int" -> {
                    if (expr.params().isEmpty()) {
                        return Sort.INT;
                    }
                }
                case "Real" -> {
                    if (expr.params().isEmpty()) {
                        return Sort.REAL;
                    }
                }
                case "Array" -> {
                    if (expr.params().size() == 2) {
                        return new Sort.Array(sort(expr.params().get(0)), sort(expr.params().get(1)));
                    }
                }
                default -> {}
            }
        }
        throw new ChcInputException("unknown sort: " + SyntaxPrinter.sort(expr));
    }

    // ------------------------------------------------------------ terms

    private Term term(TermExpr expr, Env env) {
        return switch (expr) {
            case TermExpr.Numeral(var value) -> new Term.IntLit(value);
            case TermExpr.Decimal(var value) -> new Term.RealLit(value);
            case TermExpr.Hex _, TermExpr.Bin _, TermExpr.Str _ ->
                    throw new ChcInputException("unsupported literal: " + SyntaxPrinter.term(expr));
            case TermExpr.QualId q -> qualId(q, env);
            case TermExpr.App(var f, var args) -> application(expr, f, args, env);
            case TermExpr.Let(var bindings, var body) -> let(bindings, body, env);
            case TermExpr.Quantified(var quantifier, var vars, var body) -> {
                checkDistinct(vars.stream().map(SortedVar::name).toList());
                var locals = new HashMap<String, Local>();
                var bound = new ArrayList<Term.Var>();
                for (SortedVar v : vars) {
                    boolean captures = env.capturable().contains(v.name()) || macroFreeNames.contains(v.name());
                    var var = new Term.Var(captures ? names.fresh(v.name()) : v.name(), sort(v.sort()));
                    bound.add(var);
                    locals.put(v.name(), new Local.Bound(var));
                }
                Term resolvedBody = term(body, env.with(locals, Set.of()));
                if (!resolvedBody.sort().equals(Sort.BOOL)) {
                    throw illSorted(expr, "quantifier body has sort " + Printer.sort(resolvedBody.sort()) + ", expected Bool");
                }
                yield new Term.Quant(quantifier, bound, resolvedBody);
            }
            case TermExpr.Annotated(var inner, var _) -> term(inner, env);
            case TermExpr.Match _ -> throw new ChcInputException("unsupported term: match");
        };
    }

    private Term let(List<Binding> bindings, TermExpr body, Env env) {
        checkDistinct(bindings.stream().map(Binding::name).toList());
        var locals = new HashMap<String, Local>();
        var capturable = new HashSet<String>();
        for (Binding b : bindings) {
            Term value = term(b.value(), env);
            locals.put(b.name(), new Local.LetValue(value));
            Terms.freeVars(value).forEach(v -> capturable.add(v.name()));
        }
        return term(body, env.with(locals, capturable));
    }

    private Term qualId(TermExpr.QualId q, Env env) {
        checkNotIndexed(q.id());
        Term term = symbol(q, q.id().symbol(), env);
        return q.as().map(asSort -> checkAs(q, term, sort(asSort))).orElse(term);
    }

    private static Term checkAs(TermExpr expr, Term term, Sort declared) {
        if (!term.sort().equals(declared)) {
            throw illSorted(expr, "term has sort " + Printer.sort(term.sort()) + ", not " + Printer.sort(declared));
        }
        return term;
    }

    /** A symbol used without arguments. */
    private Term symbol(TermExpr expr, String name, Env env) {
        Local local = env.locals().get(name);
        if (local != null) {
            return switch (local) {
                case Local.Bound(var v) -> v;
                case Local.LetValue(var value) -> value;
            };
        }
        Global global = globals.get(name);
        if (global != null) {
            return switch (global) {
                case Global.Const(var v) -> v;
                case Global.Pred(var p) -> predicateApplication(expr, p, List.of());
                case Global.Macro(var params, var body) -> macroApplication(expr, name, params, body, List.of());
            };
        }
        return switch (name) {
            case "true" -> Term.TRUE;
            case "false" -> Term.FALSE;
            default -> {
                if (THEORY_SYMBOLS.contains(name)) {
                    throw illSorted(expr, Symbols.print(name) + " needs arguments");
                }
                throw new ChcInputException("undeclared symbol: " + Symbols.print(name));
            }
        };
    }

    private Term application(TermExpr expr, TermExpr.QualId f, List<TermExpr> argExprs, Env env) {
        checkNotIndexed(f.id());
        String name = f.id().symbol();
        if (f.as().isPresent() && name.equals("const")) {
            return constArray(expr, sort(f.as().get()), argExprs, env);
        }
        Term result = plainApplication(expr, name, argExprs, env);
        return f.as().map(asSort -> checkAs(expr, result, sort(asSort))).orElse(result);
    }

    private Term plainApplication(TermExpr expr, String name, List<TermExpr> argExprs, Env env) {
        if (env.locals().containsKey(name)) {
            throw illSorted(expr, Symbols.print(name) + " is not a function");
        }
        Global global = globals.get(name);
        if (global != null) {
            return switch (global) {
                case Global.Const _ -> throw illSorted(expr, Symbols.print(name) + " is not a function");
                case Global.Pred(var p) -> predicateApplication(expr, p, terms(argExprs, env));
                case Global.Macro(var params, var body) -> macroApplication(expr, name, params, body, terms(argExprs, env));
            };
        }
        if (name.equals("true") || name.equals("false")) {
            throw illSorted(expr, name + " is not a function");
        }
        Op op = Op.bySymbol(name).orElseThrow(() -> new ChcInputException("undeclared symbol: " + Symbols.print(name)));
        List<Term> args = coerce(op, terms(argExprs, env));
        if (op == Op.IMPLIES && args.size() > 2) {
            Term premise = theoryApplication(expr, Op.AND, args.subList(0, args.size() - 1));
            args = List.of(premise, args.getLast());
        }
        return theoryApplication(expr, op, args);
    }

    private List<Term> terms(List<TermExpr> exprs, Env env) {
        return exprs.stream().map(e -> term(e, env)).toList();
    }

    private static Term theoryApplication(TermExpr expr, Op op, List<Term> args) {
        try {
            return Term.app(op, args);
        } catch (IllSortedException e) {
            throw illSorted(expr, e.getMessage());
        }
    }

    private Term constArray(TermExpr expr, Sort declared, List<TermExpr> argExprs, Env env) {
        List<Term> args = terms(argExprs, env);
        Sort.Array arraySort = switch (declared) {
            case Sort.Array a -> a;
            case Sort.Bool _, Sort.Int _, Sort.Real _ ->
                    throw illSorted(expr, "const needs an array sort, got " + Printer.sort(declared));
        };
        if (args.size() != 1) {
            throw illSorted(expr, "const expects 1 argument, got " + args.size());
        }
        Term element = toSort(args.getFirst(), arraySort.element());
        try {
            return Term.constArray(arraySort, element);
        } catch (IllSortedException e) {
            throw illSorted(expr, e.getMessage());
        }
    }

    private static Term predicateApplication(TermExpr expr, Predicate p, List<Term> args) {
        return new Term.PredApp(p, checkArguments(expr, "predicate " + Symbols.print(p.name()), p.argSorts(), args));
    }

    private Term macroApplication(TermExpr expr, String name, List<Term.Var> params, Term body, List<Term> args) {
        List<Term> checked = checkArguments(expr, Symbols.print(name), params.stream().map(Term::sort).toList(), args);
        var substitution = new LinkedHashMap<Term.Var, Term>();
        for (int i = 0; i < params.size(); i++) {
            substitution.put(params.get(i), checked.get(i));
        }
        return Terms.substitute(body, substitution, names);
    }

    /** Checks arity and sorts of arguments to a function with a fixed signature, promoting numerals. */
    private static List<Term> checkArguments(TermExpr expr, String what, List<Sort> sorts, List<Term> args) {
        if (args.size() != sorts.size()) {
            throw illSorted(expr, what + " expects " + sorts.size() + " argument" + (sorts.size() == 1 ? "" : "s")
                    + ", got " + args.size());
        }
        var result = new ArrayList<Term>();
        for (int i = 0; i < args.size(); i++) {
            Term arg = toSort(args.get(i), sorts.get(i));
            if (!arg.sort().equals(sorts.get(i))) {
                throw illSorted(expr, "argument " + (i + 1) + " of " + what + " has sort " + Printer.sort(arg.sort())
                        + ", expected " + Printer.sort(sorts.get(i)));
            }
            result.add(arg);
        }
        return result;
    }

    // ------------------------------------------------------------ numeral promotion

    /** Promotes Int numerals among the arguments of {@code op} where the signature needs a Real. */
    private static List<Term> coerce(Op op, List<Term> args) {
        return switch (op) {
            case ADD, SUB, MUL, LE, LT, GE, GT, EQ, DISTINCT ->
                    args.stream().anyMatch(a -> a.sort().equals(Sort.REAL)) ? allToReal(args) : args;
            case DIV_REAL -> allToReal(args);
            case ITE -> {
                if (args.size() == 3 && (args.get(1).sort().equals(Sort.REAL) || args.get(2).sort().equals(Sort.REAL))) {
                    yield List.of(args.get(0), toSort(args.get(1), Sort.REAL), toSort(args.get(2), Sort.REAL));
                }
                yield args;
            }
            case SELECT, STORE -> arrayOf(args).map(array -> {
                var result = new ArrayList<>(args);
                result.set(1, toSort(args.get(1), array.index()));
                if (args.size() > 2) {
                    result.set(2, toSort(args.get(2), array.element()));
                }
                return List.copyOf(result);
            }).orElse(args);
            case NOT, AND, OR, XOR, IMPLIES, IDIV, MOD, ABS, TO_REAL, TO_INT, IS_INT, CONST_ARRAY -> args;
        };
    }

    private static Optional<Sort.Array> arrayOf(List<Term> args) {
        if (args.size() < 2) {
            return Optional.empty();
        }
        return switch (args.getFirst().sort()) {
            case Sort.Array a -> Optional.of(a);
            case Sort.Bool _, Sort.Int _, Sort.Real _ -> Optional.empty();
        };
    }

    private static List<Term> allToReal(List<Term> args) {
        return args.stream().map(a -> toSort(a, Sort.REAL)).toList();
    }

    /**
     * Promotes an Int numeral ({@code 5} or {@code (- 5)}) to a Real literal when {@code expected} is Real.
     * Every other term is returned unchanged; in particular Int-sorted variables are never coerced.
     */
    private static Term toSort(Term term, Sort expected) {
        if (!expected.equals(Sort.REAL) || !term.sort().equals(Sort.INT)) {
            return term;
        }
        return switch (term) {
            case Term.IntLit(var value) -> new Term.RealLit(new BigDecimal(value));
            case Term.App(var op, var _, var args, var _) when op == Op.SUB && args.size() == 1 -> {
                Term inner = toSort(args.getFirst(), Sort.REAL);
                yield inner.sort().equals(Sort.REAL) ? Term.app(Op.SUB, inner) : term;
            }
            case Term.Var _, Term.BoolLit _, Term.RealLit _, Term.App _, Term.PredApp _, Term.Quant _ -> term;
        };
    }

    // ------------------------------------------------------------ errors

    private static void checkNotIndexed(Identifier id) {
        if (!id.indices().isEmpty()) {
            throw new ChcInputException("unsupported identifier: " + SyntaxPrinter.identifier(id));
        }
    }

    private static ChcInputException illSorted(TermExpr expr, String reason) {
        String text = SyntaxPrinter.term(expr);
        if (text.length() > MAX_TERM_IN_MESSAGE) {
            text = text.substring(0, MAX_TERM_IN_MESSAGE) + "...";
        }
        return new ChcInputException("not well sorted: " + text + ": " + reason);
    }
}
