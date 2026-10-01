package hornassistant.transform;

import static org.assertj.core.api.Assertions.assertThat;

import hornassistant.chc.ChcLoader;
import hornassistant.chc.Clause;
import hornassistant.smtlib.term.Term;
import hornassistant.system.SingleSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The properties of §7.2, checked on every golden input. */
class NormPropertiesTest {

    static List<Path> inputs() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/test/resources/golden"))) {
            return files.filter(f -> f.toString().endsWith(".smt2")).sorted().toList();
        }
    }

    private static SingleSystem normalized(Path input) throws IOException {
        SingleSystem system = ChcLoader.load(Files.readString(input), "t");
        return (SingleSystem) new Norm().transformed(system);
    }

    @ParameterizedTest
    @MethodSource("inputs")
    void everyArgumentIsADistinctVariable(Path input) throws IOException {
        SingleSystem system = normalized(input);
        for (int e : system.hyperedges()) {
            Clause clause = system.hyperedge(e);
            var seen = new HashSet<Term>();
            for (Term.PredApp app : clause.applications()) {
                for (Term arg : app.args()) {
                    assertThat(arg).as("argument of %s", app).isInstanceOf(Term.Var.class);
                    assertThat(seen.add(arg)).as("%s appears twice in clause %s", arg, e).isTrue();
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("inputs")
    void isIdempotent(Path input) throws IOException {
        SingleSystem once = normalized(input);
        SingleSystem twice = (SingleSystem) new Norm().transformed(once);
        assertThat(twice.text()).isEqualTo(once.text());
    }
}
