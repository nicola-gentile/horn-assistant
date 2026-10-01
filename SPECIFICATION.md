# horn-assistant — Specification

Version 0.1 of this document. It describes the behaviour of horn-assistant without reference to the
language it is written in. A conforming implementation must behave as described here.
The key words MUST, MUST NOT, SHOULD and MAY are used as in RFC 2119.

## 1. Purpose

horn-assistant is a preprocessor for Constrained Horn Clause (CHC) systems that works with any
solver. It reads one CHC system written in SMT-LIB v2, applies an ordered sequence of
**transformations**, and emits one or more CHC systems written in SMT-LIB v2. Every transformation
preserves satisfiability: each emitted system is satisfiable exactly when the input system is.

## 2. Terminology

- **Predicate**: an uninterpreted function symbol whose result sort is `Bool`, declared with
  `declare-fun`. A predicate with zero arguments is a **Boolean predicate**.
- **Predicate application**: a term `(P t1 … tn)` where `P` is a predicate of arity `n` and each
  `ti` has the i-th argument sort of `P`. For a Boolean predicate, the application is the symbol `P`
  alone.
- **Constraint**: a formula that contains no predicate symbol. It may use any theory supported by the
  SMT-LIB parser, including `div` and `mod`.
- **Clause**: an implication `∀ V. (A1 ∧ … ∧ Ak ∧ φ) → H`, where:
  - each `Ai` is a predicate application; together they form the **body**, a sequence that may repeat
    predicates;
  - `φ` is a constraint;
  - `H` is a predicate application (the **head**) or `false`;
  - `V` holds every free variable of the clause that is not a predicate.
- **Fact**: a clause whose body is empty.
- **Query**: a clause whose head is `false`.
- **CHC system**: a set of predicates together with a finite, ordered list of clauses over them.

## 3. Data model

### 3.1 System

Every value that transformations consume or produce is a **System**. A System provides these
operations:

| Operation | Result |
|-----------|--------|
| `name` | A string used to name output files. |
| `predicates()` | The predicates of the system. |
| `clauses()` | The clauses of the system, each as a formula in the form of §2. |
| `copy()` | An independent System equal to this one. Changing either one MUST NOT affect the other. |
| `write(stream)` | Writes the system to a text stream in the output format of §5. |
| `dump(folder)` | Creates `folder` and any missing parent folders, writes the system as files into it, and returns the paths it wrote. |

At present there is exactly one kind of System, the **single system** of §3.2.
A **multi-system**, which holds several systems produced by a splitting transformation, is reserved
for the future. When it is added:
- its `dump` MUST write `<name>.<i>.smt2` for `i = 0, 1, …`;
- its `write` MUST separate consecutive systems with a `(reset)` command.

### 3.2 Single system: the CHC hypergraph

A single system is a directed hypergraph:

- **Nodes** are the predicates of the system.
- **Hyperedges** are the clauses. Each hyperedge has a stable integer identifier and connects:
  - the body predicate applications, an ordered multiset, as its sources;
  - the head predicate application, or `false` for a query, as its target.
- The **value** of a hyperedge is its constraint `φ`.

The hypergraph MUST support the following operations:

| Operation | Behaviour |
|-----------|-----------|
| `add_predicate(P)` | Adds a node. |
| `remove_predicate(P)` | Removes a node. Fails if some hyperedge still uses `P`. |
| `add_hyperedge(body, φ, head)` | Adds a clause and returns its identifier. Fails without changing anything if an application in `body` or `head` refers to an undeclared predicate, or if `head` is neither an application nor `false`. |
| `replace_hyperedge(e, body, φ, head)` | Replaces clause `e`. Fails under the same conditions, leaving `e` unchanged. |
| `remove_hyperedge(e)` | Removes clause `e`. |
| `body(e)`, `constraint(e)`, `head(e)` | Return the parts of clause `e`. `head(e)` is `false` for a query. |
| `clause(e)` | Returns clause `e` as a formula (§5.2). |
| `incoming(P)` | The clauses whose head applies `P`. |
| `outgoing(P)` | The clauses whose body applies `P`. |
| `facts()` | The clauses with an empty body. |
| `queries()` | The clauses whose head is `false`. |
| `dependency_graph()` | A directed multigraph over the predicates plus two sentinel nodes, `true` and `false`. See below. |

`dependency_graph()` has one edge `B → H`, labelled with the clause identifier, for each body
predicate `B` and the head predicate `H` of each clause. Facts use `true` as their source, and queries
use `false` as their target.

Identifiers are never reused within one system. A copy MUST keep the existing identifiers and MUST
give new clauses identifiers that are not in use.

## 4. Input

### 4.1 Format

The input is an SMT-LIB v2 script.
- `declare-fun` commands declare predicates, and `assert` commands state clauses.
- `set-logic HORN` and any other standard logic are accepted.
- Other commands, such as `check-sat`, `set-info` and `exit`, have no effect on the result.
- An asserted formula MAY be wrapped in `forall`. Every free symbol that is not a predicate, including
  symbols declared with `declare-const`, is treated as universally quantified in its clause.

### 4.2 Turning an assertion into a clause

Let `M` be the asserted formula with any outer `forall` removed. `M` is split into a premise `B` and a
conclusion `C`:

| Shape of `M` | `B` | `C` |
|--------------|-----|-----|
| `(=> B C)` | `B` | `C` |
| `(not B)` | `B` | `false` |
| anything else | `true` | `M` |

**Body.** `B` is treated as a tree of `and` nodes. Every leaf that is a predicate application is
moved, in left-to-right order, into the body. The leaves that remain keep their `and` structure and
form the constraint `φ`:
- an `and` node left with no children disappears;
- an `and` node left with one child is replaced by that child;
- if nothing remains, `φ` is `true`.

Every remaining leaf MUST be a constraint.

**Head.**
- If `C` is a predicate application, it is the head.
- Otherwise `C` MUST be a constraint, and the clause becomes a query. If `C` is not `false`, `(not C)`
  is added as one more conjunct of `φ`.

Flattening of nested conjunctions in `φ` is left to the `norm` transformation (§7.1).

### 4.3 Errors

The input is rejected with an error when any of the following holds:

1. It is not syntactically valid SMT-LIB, or it isn't well sorted.
2. It contains no `assert` command.
3. A `declare-fun` declares a symbol whose result sort is not `Bool`.
4. A predicate occurs anywhere other than a body leaf or the head of §4.2. Examples: under `or`,
   under a `not` that isn't the outer one, inside a quantifier within a clause, or inside the arguments
   of a predicate application.
5. More than one predicate occurs in the head.

## 5. Output

### 5.1 File format

A single system is written as follows:

```
(set-logic HORN)
(declare-fun <P> (<argument sorts>) Bool)      ; one line per predicate, sorted by name
                                               ; an empty line
(assert <clause>)                              ; one line per clause, in system order
(check-sat)
```

Every predicate of the system is declared, including predicates that no clause uses. The output for a
given system MUST be the same on every run.

### 5.2 Clause formula

The formula of a clause with body `A1 … Ak`, constraint `φ` and head `H` is built as follows:

- The premise `P` is:
  - `φ` when `k = 0`;
  - `(and A1 … Ak)` when `φ` is `true`;
  - otherwise `(and A1 … Ak c1 … cm)`, where `c1 … cm` are the top-level conjuncts of `φ`. If `φ` is
    not an `and`, it is its only conjunct.
- The formula is `(=> P H)`, with `H` written as `false` for a query.
  - Exception: when `k = 0` and `φ` is `true`, the formula is `H` alone.
- If the formula has free variables other than predicates, it is wrapped in
  `(forall ((v1 S1) … (vn Sn)) …)`, with the variables sorted by name.

## 6. Command-line interface

### 6.1 Synopsis

```
horn-assistant [INPUT] [--in] [-o DIR | --output-dir DIR] [--out] [--opt NAMES]... [--version] [--help]
```

### 6.2 Options

| Option | Meaning |
|--------|---------|
| `INPUT` | Path of the input file (§4). It must exist and be a regular file. |
| `--in` | Read the input from standard input instead of a file. |
| `-o DIR`, `--output-dir DIR` | Write the result into directory `DIR` by calling `dump(DIR)`. The directory is created if missing. |
| `--out` | Write the result to standard output by calling `write`. |
| `--opt NAMES` | Choose the transformations to apply. `NAMES` is a comma-separated list of transformation names. The option may be repeated. |
| `--version` | Print the version and exit with status 0. |
| `--help` | Print usage and exit with status 0. The help lists the available transformations and the default list. |

### 6.3 Behaviour

1. **Input source.** Exactly one of `INPUT` and `--in` MUST be given. Giving both, or neither, is a
   usage error.
2. **System name.** The name is the file name of `INPUT` without its extension, or `stdin` when
   `--in` is used.
3. **Transformation list.** The values of every `--opt` are joined in command-line order, split at
   commas, trimmed of surrounding spaces, and stripped of empty entries. The list keeps its order and
   may contain repetitions.
   - Without any `--opt`, the default list is used (§7).
   - `--opt` given only empty values, such as `--opt ""`, means that no transformation is applied.
4. **Name check.** Every name is checked before any transformation runs. One unknown name is an error.
5. **Pipeline.** Starting from the loaded system, each transformation is applied in turn with its
   non-mutating form (§7): `S ← t.transformed(S)`.
6. **Output.**
   - If `-o` is given, the final system is dumped into `DIR`. For a single system, this writes
     `DIR/<name>.smt2`.
   - If `--out` is given, or `-o` is not given, the final system is also written to standard
     output.
   - `-o` and `--out` may be combined, in which case both outputs are identical.

### 6.4 Exit status and diagnostics

| Status | Condition |
|--------|-----------|
| `0` | Success. |
| `1` | The input is not a CHC system (§4.3), or a transformation name is unknown. One line `error: <message>` is written to standard error. Nothing is written to standard output. |
| `2` | Usage error: an invalid option, a missing or unreadable `INPUT`, or both or neither of `INPUT` and `--in`. |

## 7. Transformations

### 7.1 Interface

A transformation has:
- a unique **name**, used by `--opt`;
- a one-line **help** text;
- two operations:
  - `transform(S)` changes System `S` in place;
  - `transformed(S)` returns the transformed System and leaves `S` unchanged. By default it is
    `copy(S)` followed by `transform` on the copy.

A transformation that splits a system into several systems MUST override `transformed` to return a
multi-system (§3.1), because `transform` works in place and cannot change the kind of System.

Transformations are kept in a registry keyed by name, and registering the same name twice is an
error. **Default list:** `norm`.

### 7.2 `norm` — normalization

`norm` applies to single systems. It rewrites every clause `(body, φ, head)` independently, as
follows.

**Step 1 — flatten the constraint.** `φ` is turned into a list of conjuncts:
- `flatten((and a1 … an)) = flatten(a1) ++ … ++ flatten(an)`;
- `flatten(true) = []`;
- `flatten(ψ) = [ψ]` for any other `ψ`.

Only conjunctions at the top level are flattened. A conjunction under any other connective, such as
`(or a (and b c))`, is left unchanged.

**Step 2 — make arguments distinct variables.**
- Let `Apps` be the body applications, in order, followed by the head application if the clause is
  not a query.
- Let `occ(t)` be the number of argument positions, across all of `Apps`, that hold the term `t`.
  Repeated positions inside one application count separately.
- Each argument `t` of each application in `Apps` is handled from left to right:
  - If `t` is a variable and `occ(t) = 1`, it is kept.
  - Otherwise, a fresh variable `v` of the sort of `t` replaces `t` in that position, and the
    equality `v = t` is added to the end of the conjunct list. For sort `Bool`, the equality is
    written `(= v t)`, which means "if and only if".
- Constants and compound terms always count as "otherwise".
- Boolean predicates, which have no arguments, are not changed.

**Fresh variables.**
- A fresh variable is named `<x>_<n>` when it replaces a variable `x`, and `v_<n>` otherwise.
- `<n>` is the smallest non-negative integer for which the name is not used in the current session.
- A fresh name MUST NOT clash with any symbol already in use.

**Step 3 — rebuild.** The new constraint is the conjunction of the list: `true` if the list is
empty, its only element if there is one, and `(and …)` otherwise. The head stays `false` for a
query.

**Properties.**
- After `norm`, every argument of every predicate application in a clause is a variable, and no
  variable appears in two argument positions of that clause.
- `norm` is idempotent: applying it to its own output changes nothing.
- Each clause is logically equivalent to the original, because the fresh variables are universally
  quantified and fixed by equalities. The system therefore has the same models over the predicates.

**Example.** Quantifiers are omitted below. An implementation MAY print equivalent theory atoms in
a normalized form, for example `(>= y 0)` as `(<= 0 y)`.

```
input:   (=> (and (inv x y) (and (< x 10) (>= y 0))) (inv (+ x 1) (+ y x)))
output:  (=> (and (inv x y) (< x 10) (>= y 0) (= v_0 (+ x 1)) (= v_1 (+ y x))) (inv v_0 v_1))

input:   (=> (and (P x) (> x 0)) (Q x y))
output:  (=> (and (P x_0) (> x 0) (= x_0 x) (= x_1 x)) (Q x_1 y))
```
