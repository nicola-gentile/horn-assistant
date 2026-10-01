# horn-assistant

horn-assistant is a solver-agnostic preprocessor for Constrained Horn Clause (CHC) systems. It reads one
CHC system written in SMT-LIB v2, applies an ordered list of satisfiability-preserving **transformations**,
and writes the result as SMT-LIB v2 again. [`SPECIFICATION.md`](SPECIFICATION.md) describes the behaviour,
and [`DECISIONS.md`](DECISIONS.md) records how its open points were settled.

## Install

Every [GitHub release](https://github.com/nicola-gentile/horn-assistant/releases) contains:

| File | Platform |
|---|---|
| `horn-assistant-<version>-linux-x86_64` | Linux x86_64, native executable |
| `horn-assistant-<version>-linux-aarch64` | Linux aarch64, native executable |
| `horn-assistant-<version>-macos-aarch64` | macOS on Apple silicon, native executable |
| `horn-assistant-<version>-windows-x86_64.exe` | Windows x86_64, native executable |
| `horn-assistant-<version>.jar` | any platform with Java 25 or newer |
| `SHA256SUMS` | checksums of all of the above |

The native executables are single files that need no Java installation. They are built for a baseline CPU
of their architecture (`-march=compatibility`), so they also run on older machines.

### Native executable (Linux, macOS)

```sh
version=0.1.0
asset=horn-assistant-$version-linux-x86_64        # or -linux-aarch64, -macos-aarch64
curl -LO "https://github.com/nicola-gentile/horn-assistant/releases/download/v$version/$asset"
curl -LO "https://github.com/nicola-gentile/horn-assistant/releases/download/v$version/SHA256SUMS"
sha256sum --check --ignore-missing SHA256SUMS      # macOS: shasum -a 256 --check --ignore-missing SHA256SUMS
chmod +x "$asset"
mv "$asset" ~/.local/bin/horn-assistant             # or any directory on your PATH
```

**macOS:** the binaries are not signed or notarised. If you download one with a browser, Gatekeeper blocks it;
remove the quarantine attribute once:

```sh
xattr -d com.apple.quarantine horn-assistant
```

### Native executable (Windows)

Download `horn-assistant-<version>-windows-x86_64.exe`, rename it to `horn-assistant.exe` and put it in a
folder on your `PATH`.

### Jar

The jar needs Java 25 or newer:

```sh
java -jar horn-assistant-0.1.0.jar --help
```

## Usage

```
horn-assistant [INPUT] [--in] [-o DIR | --output-dir DIR] [--out] [--opt NAMES]... [--version] [--help]
```

| Option | Meaning |
|---|---|
| `INPUT` | Input file. |
| `--in` | Read the input from standard input instead (the system is then named `stdin`). |
| `-o DIR`, `--output-dir DIR` | Write the result to `DIR/<name>.smt2`, where `<name>` is the input file name without its last extension. `DIR` is created if missing. |
| `--out` | Also write the result to standard output (the default when `-o` is not given). |
| `--opt NAMES` | Comma-separated transformations to apply, in order. May be repeated. Without `--opt` the default list (`norm`) is used; `--opt ""` applies none. |
| `--version`, `--help` | Print the version or the help and exit. |

Exit status: `0` on success; `1` when the input is not a CHC system or a transformation is unknown (with one
`error: …` line on standard error and nothing on standard output); `2` for usage errors.

Error messages, help and version text are coloured when the program runs in a terminal. `NO_COLOR` or
`TERM=dumb` turn this off. The SMT-LIB output is never coloured.

### Transformations

| Name | Effect |
|---|---|
| `norm` | Flattens the top-level conjunction of each constraint and makes every predicate argument a variable that occurs in no other argument position of its clause, adding equalities such as `(= v_0 (+ x 1))`. |

### Example

```sh
$ cat counter.smt2
(set-logic HORN)
(declare-fun inv (Int Int) Bool)
(assert (forall ((x Int) (y Int)) (=> (and (= x 0) (= y 0)) (inv x y))))
(assert (forall ((x Int) (y Int)) (=> (and (inv x y) (and (< x 10) (>= y 0))) (inv (+ x 1) (+ y x)))))
(assert (forall ((x Int) (y Int)) (=> (and (inv x y) (< y 0)) false)))
(check-sat)

$ horn-assistant counter.smt2
(set-logic HORN)
(declare-fun inv (Int Int) Bool)

(assert (forall ((x Int) (y Int)) (=> (and (= x 0) (= y 0)) (inv x y))))
(assert (forall ((v_0 Int) (v_1 Int) (x Int) (y Int)) (=> (and (inv x y) (< x 10) (>= y 0) (= v_0 (+ x 1)) (= v_1 (+ y x))) (inv v_0 v_1))))
(assert (forall ((x Int) (y Int)) (=> (and (inv x y) (< y 0)) false)))
(check-sat)

$ horn-assistant --in --opt "" -o out < counter.smt2   # no transformation, writes out/stdin.smt2
```

### Supported input

SMT-LIB v2.6 scripts using the Core, Ints, Reals, Reals_Ints and ArraysEx theories. `declare-fun` declares
predicates, `declare-const` declares variables, `define-fun` and `let` are inlined, and `assert` states the
clauses. Datatypes, user sorts, recursive definitions, bit-vectors and strings are rejected.

## Build from source

Requirements: JDK 25. The Maven Wrapper downloads the right Maven version.

```sh
./mvnw verify                 # generate the parser, compile, run all tests, build target/horn-assistant.jar
./horn-assistant --help       # launcher script for the jar
```

### Native executable

Requirements: [GraalVM](https://www.graalvm.org/) for JDK 25 as `JAVA_HOME` (on Windows, also the Visual
Studio build tools).

```sh
./mvnw -Pnative package       # also runs the test suite as a native image; skip that with -DskipNativeTests
./target/horn-assistant --help
```

The golden, error and CLI tests can also run against any executable instead of the in-process entry point:

```sh
./mvnw test -Dtest='GoldenTest,ErrorCasesTest,CliTest' -Dhorn.exe=target/horn-assistant
./mvnw test -Dtest='GoldenTest,ErrorCasesTest,CliTest' -Dhorn.exe='java -jar target/horn-assistant.jar'
```

To refresh the reachability metadata, run the tests with the tracing agent (`./mvnw -Pnative -Dagent=true test`),
review the generated configuration and copy what is needed to
`src/main/resources/META-INF/native-image/dev.hornassistant/horn-assistant/`.

### Project layout

| Package | Contents |
|---|---|
| `smtlib.parser` | ANTLR grammar (`src/main/antlr4/.../SmtLib.g4`), parse tree → syntax AST |
| `smtlib.syntax` | syntax AST |
| `smtlib.sort`, `smtlib.term` | sorts, theory signatures, resolver, typed terms |
| `smtlib.print` | SMT-LIB printers |
| `chc` | predicates, clauses, clause extraction, loader |
| `system` | the CHC hypergraph (`SingleSystem`) and its output |
| `transform` | transformations and their registry |
| `cli`, `cli.ui` | command line, styled help and diagnostics |

Golden tests live in `src/test/resources/golden`: `<case>.smt2` is an input and `<case>.<opts>.expected` the
expected output, where `<opts>` is `default`, `none` (`--opt ""`) or the value of `--opt`.

## Releasing

1. Set the version in `pom.xml` (`./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false`) and commit.
2. Tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.

The `Release` workflow checks that the tag matches the pom version, builds and tests the jar and the four
native executables (each binary is run against the golden and CLI tests), and publishes them with
`SHA256SUMS` as a GitHub release. It can also be started by hand for an existing tag.

## License

[MIT](LICENSE)
