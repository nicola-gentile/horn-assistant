package hornassistant.system;

import static hornassistant.smtlib.sort.Sort.INT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hornassistant.chc.Head;
import hornassistant.chc.Predicate;
import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.term.Op;
import hornassistant.smtlib.term.Term;
import hornassistant.system.DependencyGraph.Edge;
import hornassistant.system.DependencyGraph.PredNode;
import hornassistant.system.DependencyGraph.Sentinel;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleSystemTest {

    private static final Term.Var X = new Term.Var("x", INT);
    private static final Predicate P = new Predicate("P", List.of(INT));
    private static final Predicate Q = new Predicate("Q", List.of(INT));
    private static final Predicate UNUSED = new Predicate("A", List.of());
    private static final Term POSITIVE = Term.app(Op.GT, X, Term.intLit(0));

    private static Term.PredApp p() {
        return new Term.PredApp(P, List.of(X));
    }

    private static Term.PredApp q() {
        return new Term.PredApp(Q, List.of(X));
    }

    private SingleSystem system;
    private int fact;
    private int step;
    private int query;

    @BeforeEach
    void setUp() {
        system = new SingleSystem("test");
        system.addPredicate(Q);
        system.addPredicate(P);
        system.addPredicate(UNUSED);
        fact = system.addHyperedge(List.of(), POSITIVE, new Head.Pred(p()));
        step = system.addHyperedge(List.of(p(), p()), Term.TRUE, new Head.Pred(q()));
        query = system.addHyperedge(List.of(q()), Term.TRUE, Head.FALSE);
    }

    private static String text(ChcSystem s) {
        var bytes = new ByteArrayOutputStream();
        s.write(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void predicatesAreSortedByName() {
        assertThat(system.predicates()).containsExactly(UNUSED, P, Q);
    }

    @Test
    void exposesTheClauseParts() {
        assertThat(system.hyperedges()).containsExactly(fact, step, query);
        assertThat(system.body(step)).containsExactly(p(), p());
        assertThat(system.constraint(fact)).isEqualTo(POSITIVE);
        assertThat(system.head(query)).isEqualTo(Head.FALSE);
        assertThat(system.head(fact)).isEqualTo(new Head.Pred(p()));
        assertThat(Printer.term(system.clause(fact))).isEqualTo("(forall ((x Int)) (=> (> x 0) (P x)))");
        assertThat(system.clauses()).hasSize(3);
    }

    @Test
    void incomingOutgoingFactsAndQueries() {
        assertThat(system.incoming(P)).containsExactly(fact);
        assertThat(system.incoming(Q)).containsExactly(step);
        assertThat(system.outgoing(P)).containsExactly(step);
        assertThat(system.outgoing(Q)).containsExactly(query);
        assertThat(system.incoming(UNUSED)).isEmpty();
        assertThat(system.facts()).containsExactly(fact);
        assertThat(system.queries()).containsExactly(query);
    }

    @Test
    void dependencyGraphUsesSentinels() {
        assertThat(system.dependencyGraph().edges()).containsExactly(
                new Edge(Sentinel.TRUE, new PredNode(P), fact),
                new Edge(new PredNode(P), new PredNode(Q), step),
                new Edge(new PredNode(P), new PredNode(Q), step),
                new Edge(new PredNode(Q), Sentinel.FALSE, query));
    }

    @Test
    void factThatIsAlsoAQueryGoesFromTrueToFalse() {
        int both = system.addHyperedge(List.of(), POSITIVE, Head.FALSE);
        assertThat(system.dependencyGraph().edges()).contains(new Edge(Sentinel.TRUE, Sentinel.FALSE, both));
        assertThat(system.facts()).contains(both);
        assertThat(system.queries()).contains(both);
    }

    @Test
    void idsAreNeverReused() {
        system.removeHyperedge(query);
        int again = system.addHyperedge(List.of(q()), Term.TRUE, Head.FALSE);
        assertThat(again).isNotIn(fact, step, query);
        assertThat(system.hyperedges()).containsExactly(fact, step, again);
    }

    @Test
    void replacingKeepsThePositionAndId() {
        system.replaceHyperedge(fact, List.of(), Term.TRUE, new Head.Pred(p()));
        assertThat(system.hyperedges()).containsExactly(fact, step, query);
        assertThat(system.constraint(fact)).isEqualTo(Term.TRUE);
    }

    @Test
    void failingAddLeavesTheSystemUnchanged() {
        String before = text(system);
        var undeclared = new Predicate("R", List.of(INT));
        assertThatThrownBy(() -> system.addHyperedge(List.of(new Term.PredApp(undeclared, List.of(X))), Term.TRUE, Head.FALSE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> system.addHyperedge(List.of(), Term.TRUE, new Head.Pred(new Term.PredApp(undeclared, List.of(X)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> system.addHyperedge(List.of(), p(), Head.FALSE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(text(system)).isEqualTo(before);
        assertThat(system.hyperedges()).containsExactly(fact, step, query);
    }

    @Test
    void failingReplaceLeavesTheClauseUnchanged() {
        String before = text(system);
        var undeclared = new Predicate("R", List.of(INT));
        assertThatThrownBy(() -> system.replaceHyperedge(fact, List.of(), Term.TRUE, new Head.Pred(new Term.PredApp(undeclared, List.of(X)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> system.replaceHyperedge(99, List.of(), Term.TRUE, Head.FALSE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(text(system)).isEqualTo(before);
    }

    @Test
    void removingAPredicateInUseFails() {
        assertThatThrownBy(() -> system.removePredicate(P)).isInstanceOf(IllegalArgumentException.class);
        assertThat(system.predicates()).contains(P);
        system.removePredicate(UNUSED);
        assertThat(system.predicates()).containsExactly(P, Q);
    }

    @Test
    void addingAnotherPredicateWithAKnownNameFails() {
        assertThatThrownBy(() -> system.addPredicate(new Predicate("P", List.of()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownHyperedgeIsAnError() {
        assertThatThrownBy(() -> system.removeHyperedge(42)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> system.body(42)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copyIsIndependent() {
        String before = text(system);
        SingleSystem copy = system.copy();
        assertThat(text(copy)).isEqualTo(before);
        assertThat(copy.name()).isEqualTo("test");

        copy.removeHyperedge(query);
        copy.addPredicate(new Predicate("Z", List.of()));
        copy.replaceHyperedge(fact, List.of(), Term.TRUE, new Head.Pred(p()));
        assertThat(text(system)).isEqualTo(before);

        system.removeHyperedge(step);
        assertThat(copy.hyperedges()).containsExactly(fact, step);
    }

    @Test
    void copyKeepsIdsAndNeverHandsOutUsedOnes() {
        system.removeHyperedge(query);
        SingleSystem copy = system.copy();
        assertThat(copy.hyperedges()).containsExactly(fact, step);
        int added = copy.addHyperedge(List.of(q()), Term.TRUE, Head.FALSE);
        assertThat(added).isNotIn(fact, step, query);
    }

    @Test
    void writesTheOutputFormat() {
        assertThat(text(system)).isEqualTo("""
                (set-logic HORN)
                (declare-fun A () Bool)
                (declare-fun P (Int) Bool)
                (declare-fun Q (Int) Bool)

                (assert (forall ((x Int)) (=> (> x 0) (P x))))
                (assert (forall ((x Int)) (=> (and (P x) (P x)) (Q x))))
                (assert (forall ((x Int)) (=> (and (Q x)) false)))
                (check-sat)
                """);
    }

    @Test
    void writesQuotedPredicateNames() {
        var s = new SingleSystem("q");
        var quoted = new Predicate("a b", List.of(INT));
        s.addPredicate(quoted);
        s.addHyperedge(List.of(), Term.TRUE, new Head.Pred(new Term.PredApp(quoted, List.of(Term.intLit(1)))));
        assertThat(text(s)).contains("(declare-fun |a b| (Int) Bool)\n").contains("(assert (|a b| 1))\n");
    }

    @Test
    void dumpWritesNameDotSmt2AndCreatesFolders(@TempDir Path tmp) throws Exception {
        Path folder = tmp.resolve("a/b");
        List<Path> written = system.dump(folder);
        assertThat(written).containsExactly(folder.resolve("test.smt2"));
        assertThat(Files.readString(folder.resolve("test.smt2"), StandardCharsets.UTF_8)).isEqualTo(text(system));
    }
}
