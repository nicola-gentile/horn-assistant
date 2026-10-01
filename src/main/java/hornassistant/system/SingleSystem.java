package hornassistant.system;

import hornassistant.chc.Clause;
import hornassistant.chc.Head;
import hornassistant.chc.Predicate;
import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.print.Symbols;
import hornassistant.smtlib.term.Term;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A CHC system as a directed hypergraph over its predicates (specification §3.2). This is the only mutable
 * structure: transformations change it in place. The clauses and terms it holds are immutable values.
 * Every failing operation throws {@link IllegalArgumentException} and leaves the system unchanged.
 */
public final class SingleSystem implements ChcSystem {

    private final String name;
    /** Nodes, by name; names are unique. */
    private final TreeMap<String, Predicate> predicates;
    /** Hyperedges in system order; replacing one keeps its position. */
    private final LinkedHashMap<Integer, Clause> clauses;
    /** The next identifier to hand out. Identifiers are never reused. */
    private int nextId;

    public SingleSystem(String name) {
        this(name, new TreeMap<>(), new LinkedHashMap<>(), 0);
    }

    private SingleSystem(String name, TreeMap<String, Predicate> predicates, LinkedHashMap<Integer, Clause> clauses, int nextId) {
        this.name = name;
        this.predicates = predicates;
        this.clauses = clauses;
        this.nextId = nextId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Collection<Predicate> predicates() {
        return List.copyOf(predicates.values());
    }

    @Override
    public List<Term> clauses() {
        return clauses.values().stream().map(ClauseFormula::of).toList();
    }

    @Override
    public SingleSystem copy() {
        return new SingleSystem(name, new TreeMap<>(predicates), new LinkedHashMap<>(clauses), nextId);
    }

    @Override
    public void write(PrintStream out) {
        out.print(text());
    }

    @Override
    public List<Path> dump(Path folder) throws IOException {
        Files.createDirectories(folder);
        Path file = folder.resolve(name + ".smt2");
        Files.writeString(file, text(), StandardCharsets.UTF_8);
        return List.of(file);
    }

    /** The system in the output format of §5. */
    public String text() {
        var out = new StringBuilder("(set-logic HORN)\n");
        for (Predicate p : predicates.values()) {
            out.append("(declare-fun ").append(Symbols.print(p.name())).append(' ')
                    .append(p.argSorts().stream().map(Printer::sort).collect(Collectors.joining(" ", "(", ")")))
                    .append(" Bool)\n");
        }
        out.append('\n');
        for (Clause c : clauses.values()) {
            out.append("(assert ").append(Printer.term(ClauseFormula.of(c))).append(")\n");
        }
        return out.append("(check-sat)\n").toString();
    }

    // ------------------------------------------------------------ nodes

    public void addPredicate(Predicate p) {
        Predicate existing = predicates.get(p.name());
        if (existing != null && !existing.equals(p)) {
            throw new IllegalArgumentException("another predicate is named " + p.name());
        }
        predicates.put(p.name(), p);
    }

    public void removePredicate(Predicate p) {
        if (!p.equals(predicates.get(p.name()))) {
            throw new IllegalArgumentException("unknown predicate " + p.name());
        }
        if (clauses.values().stream().anyMatch(c -> c.applications().stream().anyMatch(a -> a.predicate().equals(p)))) {
            throw new IllegalArgumentException("predicate " + p.name() + " is still used");
        }
        predicates.remove(p.name());
    }

    // ------------------------------------------------------------ hyperedges

    public int addHyperedge(List<Term.PredApp> body, Term constraint, Head head) {
        Clause clause = validated(body, constraint, head);
        int id = nextId++;
        clauses.put(id, clause);
        return id;
    }

    public void replaceHyperedge(int e, List<Term.PredApp> body, Term constraint, Head head) {
        existing(e);
        clauses.put(e, validated(body, constraint, head));
    }

    public void removeHyperedge(int e) {
        existing(e);
        clauses.remove(e);
    }

    /** The hyperedge identifiers in system order. */
    public List<Integer> hyperedges() {
        return List.copyOf(clauses.keySet());
    }

    public Clause hyperedge(int e) {
        return existing(e);
    }

    public List<Term.PredApp> body(int e) {
        return existing(e).body();
    }

    public Term constraint(int e) {
        return existing(e).constraint();
    }

    public Head head(int e) {
        return existing(e).head();
    }

    /** Clause {@code e} as a formula (§5.2). */
    public Term clause(int e) {
        return ClauseFormula.of(existing(e));
    }

    public List<Integer> incoming(Predicate p) {
        return select(c -> switch (c.head()) {
            case Head.Pred(var app) -> app.predicate().equals(p);
            case Head.False _ -> false;
        });
    }

    public List<Integer> outgoing(Predicate p) {
        return select(c -> c.body().stream().anyMatch(a -> a.predicate().equals(p)));
    }

    public List<Integer> facts() {
        return select(Clause::isFact);
    }

    public List<Integer> queries() {
        return select(Clause::isQuery);
    }

    public DependencyGraph dependencyGraph() {
        var edges = new ArrayList<DependencyGraph.Edge>();
        for (Map.Entry<Integer, Clause> entry : clauses.entrySet()) {
            Clause c = entry.getValue();
            DependencyGraph.Node target = switch (c.head()) {
                case Head.Pred(var app) -> new DependencyGraph.PredNode(app.predicate());
                case Head.False _ -> DependencyGraph.Sentinel.FALSE;
            };
            if (c.isFact()) {
                edges.add(new DependencyGraph.Edge(DependencyGraph.Sentinel.TRUE, target, entry.getKey()));
            }
            for (Term.PredApp a : c.body()) {
                edges.add(new DependencyGraph.Edge(new DependencyGraph.PredNode(a.predicate()), target, entry.getKey()));
            }
        }
        return new DependencyGraph(edges);
    }

    private List<Integer> select(java.util.function.Predicate<Clause> filter) {
        return clauses.entrySet().stream().filter(e -> filter.test(e.getValue())).map(Map.Entry::getKey).toList();
    }

    private Clause existing(int e) {
        Clause c = clauses.get(e);
        if (c == null) {
            throw new IllegalArgumentException("unknown hyperedge " + e);
        }
        return c;
    }

    /** Builds the clause, checking that it only applies declared predicates. Changes nothing. */
    private Clause validated(List<Term.PredApp> body, Term constraint, Head head) {
        Clause clause = new Clause(body, constraint, head);
        for (Term.PredApp a : clause.applications()) {
            if (!a.predicate().equals(predicates.get(a.predicate().name()))) {
                throw new IllegalArgumentException("undeclared predicate " + a.predicate().name());
            }
        }
        return clause;
    }
}
