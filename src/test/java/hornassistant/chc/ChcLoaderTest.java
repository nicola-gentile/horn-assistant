package hornassistant.chc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hornassistant.system.SingleSystem;
import org.junit.jupiter.api.Test;

class ChcLoaderTest {

    private static final String G1 = """
            (set-logic HORN)
            (declare-fun inv (Int Int) Bool)
            (assert (forall ((x Int) (y Int)) (=> (and (= x 0) (= y 0)) (inv x y))))
            (assert (forall ((x Int) (y Int)) (=> (and (inv x y) (and (< x 10) (>= y 0))) (inv (+ x 1) (+ y x)))))
            (assert (forall ((x Int) (y Int)) (=> (and (inv x y) (< y 0)) false)))
            (check-sat)
            """;

    @Test
    void loadsClausesInAssertionOrder() {
        SingleSystem system = ChcLoader.load(G1, "g1");
        assertThat(system.name()).isEqualTo("g1");
        assertThat(system.hyperedges()).hasSize(3);
        assertThat(system.text()).isEqualTo("""
                (set-logic HORN)
                (declare-fun inv (Int Int) Bool)

                (assert (forall ((x Int) (y Int)) (=> (and (= x 0) (= y 0)) (inv x y))))
                (assert (forall ((x Int) (y Int)) (=> (and (inv x y) (< x 10) (>= y 0)) (inv (+ x 1) (+ y x)))))
                (assert (forall ((x Int) (y Int)) (=> (and (inv x y) (< y 0)) false)))
                (check-sat)
                """);
    }

    @Test
    void keepsUnusedPredicates() {
        SingleSystem system = ChcLoader.load("(declare-fun U (Int) Bool)(declare-fun B () Bool)(assert B)", "s");
        assertThat(system.predicates()).extracting(Predicate::name).containsExactly("B", "U");
    }

    @Test
    void rejectsInputWithoutAssertions() {
        assertThatThrownBy(() -> ChcLoader.load("(set-logic HORN)(declare-fun P () Bool)(check-sat)", "s"))
                .isInstanceOf(ChcInputException.class)
                .hasMessage("no assert command");
    }

    @Test
    void reportsSyntaxErrorsBeforeAMissingAssertion() {
        assertThatThrownBy(() -> ChcLoader.load("(set-logic HORN", "s")).hasMessageStartingWith("syntax error at ");
    }
}
