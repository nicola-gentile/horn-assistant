# Decisions

`SPECIFICATION.md` is the source of truth for behaviour. This file records how its gaps and ambiguous spots
were filled in. Decisions 1–10 were set by the task; the rest were made during the implementation.

## Decisions set by the task

1. **n-ary `=>`.** `(=> a1 … an c)` with n ≥ 2 is read as `(=> (and a1 … an) c)`. This is equivalent to the
   standard right-associative meaning.
2. **Adding `(not C)` to φ** (spec §4.2, head rule): if φ is `true` the result is `(not C)`; if φ is
   `(and c1 … cn)` it is `(and c1 … cn (not C))`; otherwise it is `(and φ (not C))`.
3. **`let` and `define-fun`** are inlined before clause extraction. `declare-sort`, `define-sort`,
   `declare-datatype(s)`, `define-fun-rec`, `define-funs-rec` and `match` are rejected with exit status 1
   (`error: unsupported command: …`, or `error: unsupported term: match`), because the output format cannot
   express them.
4. **`declare-const`** symbols become ordinary universally quantified variables in every clause that uses them.
5. **System name.** The file name of `INPUT` with its *last* extension removed: `a/b/foo.smt2` → `foo`,
   `bar.chc.smt2` → `bar.chc`, `noext` → `noext`. A leading dot does not start an extension
   (`.smt2` → `.smt2`).
6. **Order of CLI checks.** Option syntax, `INPUT`/`--in` exclusivity and `INPUT` readability (status 2), then
   transformation names (status 1), then parsing and loading (status 1), then the pipeline, then the output.
7. **Output is buffered.** Nothing goes to standard output, and no file is written, unless the whole pipeline
   succeeded. If the `-o` directory cannot be created or written, the status is 1 with
   `error: cannot write to <DIR>: <reason>`; standard output then stays empty too, because the dump happens
   before the write to standard output. Unexpected internal exceptions give status 1 and
   `error: internal error: <exception>`.
8. **Input encoding.** UTF-8 for files and standard input. Invalid UTF-8 is status 1,
   `error: input is not valid UTF-8`.
9. **Usage errors** (unknown option, missing option value, both or neither inputs, missing, non-regular or
   unreadable `INPUT`, surplus positional arguments) exit with 2. They print three lines to standard error:
   `error: <message>`, the usage synopsis, and `Try 'horn-assistant --help' for more information.`
10. **Styling never changes content.** With colours disabled, every byte on standard output and standard error
    is exactly what the spec and these decisions prescribe. Colours are chosen automatically (see 33); no
    `--color` option is added, because the spec fixes the option set.

## Parsing

11. **Command names are reserved only in command position.** The lexer has tokens for the command names (so
    that an unknown command is a syntax error), but inside terms, sorts and s-expressions a command name is an
    ordinary symbol: `(P reset)` is valid. The printer still quotes them (`|reset|`), so the output can be read
    by tools that reserve them everywhere.
12. **Syntax error positions** are `line:column`, both starting at 1. The ANTLR message is kept on one line.
13. **No empty applications.** SMT-LIB requires at least one argument in `(f t+)`, so `(and)` and `(Z)` are
    syntax errors, even though the theory signature of `and` accepts zero arguments.

## Sorts and terms

14. **Supported sorts:** `Bool`, `Int`, `Real`, `(Array I E)`. Any other sort is `error: unknown sort: …`.
    Hexadecimal, binary and string literals (`unsupported literal`) and indexed identifiers such as
    `(_ extract 7 0)` (`unsupported identifier`) are rejected with status 1; FixedSizeBitVectors (the optional
    milestone) are not implemented.
15. **Arity.** `+` and `*` need at least two arguments, `-` at least one (unary minus), `/` and `div` at least
    two, `mod` exactly two; `and`, `or`, `xor` accept any number; `=`, `distinct` and the comparisons need at
    least two arguments of one sort. All are kept n-ary (only `=>` is desugared, see 1).
16. **Numeral promotion.** An Int *numeral* — `5` or `(- 5)` — is promoted to a Real literal where a Real is
    needed: among the arguments of `+ - * = distinct < <= > >=` when another argument is Real, all arguments
    of `/`, the branches of `ite` when the other branch is Real, array indices and elements, constant-array
    elements, and arguments of predicates and `define-fun` macros declared Real. Int-sorted variables and
    other Int terms are never coerced; they are sort errors.
17. **Real literals** are kept in a canonical form: trailing zeros are dropped but at least one fractional digit
    stays (`2.50` → `2.5`, promoted `1` → `1.0`). Equal values are therefore equal terms, which matters for
    `norm`'s count of equal arguments.
18. **`declare-fun` must have result sort `Bool`**, also with zero arguments: `(declare-fun c () Int)` is error 3,
    not a constant. Use `declare-const` for constants.
19. **Names.** Declaring a name twice, or declaring a theory symbol (`and`, `+`, `true`, …), is error 1
    (`… is already declared`). A variable bound twice by one quantifier or `let` is error 1. Bound variables
    and `let` names shadow declared symbols.
20. **Capture avoidance.** `let` values are inlined. When a quantifier inside the `let` body binds a name that
    is free in a value in scope, the bound variable is renamed to a fresh `<x>_<n>`. The same applies to a
    quantifier that binds the name of a constant used by a `define-fun` body, and to a `define-fun` parameter
    with such a name. Macro application uses capture-avoiding substitution, renaming the macro's own bound
    variables when needed. Fresh names avoid every symbol of the script, as for `norm` (see 27).
21. **Annotations** (`!`) are dropped, including `:named`.

## Clauses

22. **Error 4 against error 5.** After the body leaves, the conclusion `C` is examined. If `C` is itself a
    predicate application, it is the head (its arguments must be predicate-free, else error 4). Otherwise:
    no predicate in `C` → query; two or more predicate applications anywhere in `C` → error 5; exactly one →
    error 4. The body is checked before the head.
23. **Outer quantifiers.** Only `forall` is stripped. A top-level `exists` is "anything else" in the table of
    §4.2: it is the conclusion, so a predicate inside it is error 4, and without predicates it becomes a query.
    Quantifiers inside constraints are kept as they are, with their variables in source order.
24. **Clause formula with one body application** (§5.2): when φ is `true` the premise is `(and A1 … Ak)` even
    for k = 1, so `(=> (P x) false)` is written `(=> (and (P x)) false)`, as the specification states literally.

## Error messages

25. Messages are one line (`\n` and `\r` inside are escaped). An offending term is printed in SMT-LIB syntax,
    cut after 200 characters with `...`. The forms are:

    | Condition | Message |
    |---|---|
    | lexer/parser error | `syntax error at L:C: <ANTLR message>` |
    | ill-sorted term | `not well sorted: <term>: <reason>` |
    | unknown symbol | `undeclared symbol: <name>` |
    | unknown sort | `unknown sort: <sort>` |
    | error 2 | `no assert command` |
    | error 3 | `<f> is declared with result sort <S>; only predicates (result sort Bool) can be declared` |
    | error 4 | `predicate <P> occurs outside a body leaf or the head: <term>` |
    | error 5 | `more than one predicate in the head: <term>` |
    | unsupported | `unsupported command: <name>`, `unsupported term: match`, `unsupported literal: <lit>`, `unsupported identifier: <id>` |
    | `--opt` | `unknown transformation: <name>` (the first unknown name) |

## Systems

26. **Hypergraph operations.** `add_predicate` of a predicate already present is a no-op, and of a different
    predicate with the same name it fails. `remove_hyperedge`, `replace_hyperedge` and the accessors fail on an
    unknown identifier. `outgoing(P)` lists each clause once, however often `P` occurs in its body.
    `dependency_graph()` has one edge per body *application* (a clause with body `P(x) ∧ P(y)` gives two edges
    `P → H`). A clause that is both a fact and a query gives the edge `true → false`.

## Transformations

27. **Fresh names in `norm`.** One `NameSupply` per run of `transform`, seeded with every predicate name,
    every variable name (free or bound) of every clause, and every theory symbol. `<n>` is the smallest
    non-negative integer whose name is unused; names handed out are marked used. Fresh names are therefore
    unique across all clauses of one run. Each `--opt` entry is its own run (its own "session").
28. **Constraint list order in `norm`.** Flattened conjuncts first, then the equalities in the order the
    arguments are visited (body applications left to right, then the head).

## Command line and output

29. **`--help` and `--version`** win over every other argument, including unknown options and a missing or
    duplicated input (`horn-assistant --frob --help` prints the help and exits with 0). This is picocli's
    behaviour for help options and matches spec §6.2 ("print usage and exit with status 0").
30. **Help layout:** synopsis, description, arguments, options, transformations with their help text, the
    default list (`Default: norm`), and the exit statuses.
31. **Version** comes from the Maven project version (resource filtering of `hornassistant/version.properties`);
    `--version` prints `horn-assistant <version>`.
32. **Line endings** are always `\n`, also on Windows. `.gitattributes` keeps the golden files LF.
33. **Terminal detection.** Styles are used for a stream only when the process has an interactive terminal
    (`System.console()` and `Console.isTerminal()`), `NO_COLOR` is unset or empty, and `TERM` is not `dumb`.
    The JDK cannot tell standard error apart from standard output, so both follow `Console.isTerminal()`,
    which is false as soon as standard input or standard output is redirected. This errs towards plain output.
34. **TamboUI module set:** only `dev.tamboui:tamboui-core`. The `TamboRenderer` turns a `Doc` into TamboUI
    `Line`s of styled `Span`s and writes each span with `AnsiStringBuilder.styleToAnsi(style)` and
    `AnsiStringBuilder.RESET`. No backend, `InlineDisplay`, raw mode or alternate screen is involved: those
    need a terminal backend (and `ServiceLoader`), which a non-interactive CLI does not. If TamboUI throws,
    the plain rendering is used.
35. **Deep inputs.** The work runs on a thread with a 1 GiB stack (reserved address space, committed on demand),
    because terms are processed recursively. If even that overflows, the status is 1 with
    `error: the input is nested too deeply`.
