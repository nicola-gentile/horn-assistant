# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

horn-assistant: a Java 25 CLI that reads a Constrained Horn Clause system in SMT-LIB v2, applies transformations
(currently only `norm`) and writes SMT-LIB v2 again. `SPECIFICATION.md` is the source of truth for behaviour;
every interpretation of a gap in it is recorded in `DECISIONS.md` (add new ones there).

## Commands

The build needs JDK 25 (locally installed via SDKMAN: `export JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem`).
Always use the Maven wrapper; the system Maven is too old (the enforcer requires 3.9+).

```sh
./mvnw verify                                   # ANTLR codegen, compile, all tests, fat jar target/horn-assistant.jar
./mvnw -q test -Dtest=ResolverTest              # one test class
./mvnw -q test -Dtest='ResolverTest#inlinesLet' # one test method
./horn-assistant FILE.smt2                      # launcher script, runs the jar (build first)
./mvnw -Pnative package                         # GraalVM native image target/horn-assistant (needs GraalVM 25 as JAVA_HOME; add -DskipNativeTests to skip native:test)
./mvnw test -Dtest='GoldenTest,ErrorCasesTest,CliTest' -Dhorn.exe='java -jar target/horn-assistant.jar'   # CLI tests against a real executable
```

GraalVM is not installed locally; native builds only run in CI (`.github/workflows/ci.yml`, `release.yml`).
Validate workflow edits with `actionlint`.

## Architecture

Pipeline: `text → ANTLR parse tree → syntax AST (Script) → Resolver → typed Terms → ClauseExtractor → Clauses → SingleSystem → transformations → write/dump`.

- `smtlib.parser`: grammar `src/main/antlr4/hornassistant/smtlib/parser/SmtLib.g4` (generated sources are not committed).
  `AstBuilder` is the only class that touches parse-tree types; `SmtLibParserFacade.parse` is the entry point.
  Command names are lexer tokens but are accepted as ordinary symbols inside terms.
- `smtlib.syntax`: pure syntax AST (symbols stored unquoted). `smtlib.print.SyntaxPrinter` prints it back (used in error messages).
- `smtlib.sort.Resolver`: one pass that builds the signature, inlines `let`/`define-fun` with capture avoidance
  (fresh names from `transform.NameSupply`), type-checks via `Theory`, promotes Int numerals to Real, desugars n-ary `=>`.
  `smtlib.term.Term` constructors re-validate sorts, so building an ill-sorted term throws.
- `chc.ClauseExtractor`: spec §4.2 (premise/conclusion split, and-tree rebuild, errors 4/5). `ChcLoader` ties parse → resolve → extract and raises error 2.
- `system.SingleSystem`: the only mutable structure (hypergraph; `LinkedHashMap` ids, never reused, copied by `copy()`).
  `ClauseFormula` + `smtlib.print.Printer` produce the output format of spec §5.
- `transform`: `Transformation` (in-place `transform`, `transformed` = copy + transform), immutable `TransformationRegistry`
  (register new transformations explicitly in `standard()`), `Norm`.
- `cli`: `Main.run(args, in, out, err[, TerminalCapabilities])` is the testable entry point; only `main` calls `System.exit`.
  Output is buffered and written only after the whole pipeline succeeds. Work runs on a thread with a 1 GiB stack
  (terms are processed recursively). All input errors are `ChcInputException` → exit 1, one `error: …` line.
- `cli.ui`: the only package importing TamboUI (`tamboui-core` only). Help/version/errors are `Doc` values rendered by
  `PlainRenderer` or `TamboRenderer`; SMT-LIB output is never styled.

## Code conventions (enforced by design, keep them)

- ASTs and semantic values are sealed interfaces with nested `record` variants; collections copied with `List.copyOf` in compact constructors; no nulls in ADTs.
- Operations are static functions with pattern-matching `switch` over sealed types and **no `default` branch** (list every variant), no `instanceof`, no visitors over own ASTs, no mutable static state.
- No reflection, `ServiceLoader` or dynamic class loading (native-image friendliness). Never name a class `System` (it is `ChcSystem`).
- Determinism: never iterate a `HashMap`/`HashSet` where order reaches output.
- Development is test-first; add golden cases rather than ad-hoc checks.

## Tests

- Golden cases: `src/test/resources/golden/<case>.smt2` with `<case>.<opts>.expected`, where `<opts>` is `default`
  (no `--opt`), `none` (`--opt ""`) or the literal `--opt` value. `GoldenTest` discovers them automatically, and
  `NormPropertiesTest` checks norm's properties on every `.smt2` there. Files must stay LF (`.gitattributes`).
- `CliRunner` (test code) runs either in-process or, when `-Dhorn.exe=...` is set, the given executable as a subprocess; tests that force styling skip themselves in subprocess mode.
