package hornassistant.transform;

import static org.assertj.core.api.Assertions.assertThat;

import hornassistant.chc.ChcLoader;
import hornassistant.system.ChcSystem;
import hornassistant.system.SingleSystem;
import org.junit.jupiter.api.Test;

class NormTest {

    private static final Norm NORM = new Norm();

    private static String norm(String input) {
        SingleSystem system = ChcLoader.load(input, "t");
        NORM.transform(system);
        return system.text();
    }

    /** The assert lines only. */
    private static String clauses(String input) {
        String text = norm(input);
        return text.substring(text.indexOf("\n\n") + 2, text.indexOf("(check-sat)"));
    }

    @Test
    void g1() {
        assertThat(norm("""
                (set-logic HORN)
                (declare-fun inv (Int Int) Bool)
                (assert (forall ((x Int) (y Int)) (=> (and (= x 0) (= y 0)) (inv x y))))
                (assert (forall ((x Int) (y Int)) (=> (and (inv x y) (and (< x 10) (>= y 0))) (inv (+ x 1) (+ y x)))))
                (assert (forall ((x Int) (y Int)) (=> (and (inv x y) (< y 0)) false)))
                (check-sat)
                """)).isEqualTo("""
                (set-logic HORN)
                (declare-fun inv (Int Int) Bool)

                (assert (forall ((x Int) (y Int)) (=> (and (= x 0) (= y 0)) (inv x y))))
                (assert (forall ((v_0 Int) (v_1 Int) (x Int) (y Int)) (=> (and (inv x y) (< x 10) (>= y 0) (= v_0 (+ x 1)) (= v_1 (+ y x))) (inv v_0 v_1))))
                (assert (forall ((x Int) (y Int)) (=> (and (inv x y) (< y 0)) false)))
                (check-sat)
                """);
    }

    @Test
    void g2() {
        assertThat(clauses("""
                (declare-fun Q (Int Int) Bool)
                (declare-fun P (Int) Bool)
                (assert (forall ((x Int) (y Int)) (=> (and (P x) (> x 0)) (Q x y))))
                """)).isEqualTo(
                "(assert (forall ((x Int) (x_0 Int) (x_1 Int) (y Int)) (=> (and (P x_0) (> x 0) (= x_0 x) (= x_1 x)) (Q x_1 y))))\n");
    }

    @Test
    void repeatedVariableInOneApplication() {
        assertThat(clauses("(declare-fun P (Int Int) Bool)(assert (forall ((x Int)) (P x x)))"))
                .isEqualTo("(assert (forall ((x Int) (x_0 Int) (x_1 Int)) (=> (and (= x_0 x) (= x_1 x)) (P x_0 x_1))))\n");
    }

    @Test
    void constantArgument() {
        assertThat(clauses("(declare-fun P (Int) Bool)(assert (P 0))"))
                .isEqualTo("(assert (forall ((v_0 Int)) (=> (= v_0 0) (P v_0))))\n");
    }

    @Test
    void booleanArgumentUsesEquality() {
        assertThat(clauses("(declare-fun W (Bool) Bool)(declare-fun P (Int) Bool)(assert (forall ((x Int)) (=> (P x) (W (> x 0)))))"))
                .isEqualTo("(assert (forall ((v_0 Bool) (x Int)) (=> (and (P x) (= v_0 (> x 0))) (W v_0))))\n");
    }

    @Test
    void booleanPredicatesAreUnchanged() {
        assertThat(clauses("(declare-fun A () Bool)(declare-fun B () Bool)(assert A)(assert (=> A B))"))
                .isEqualTo("(assert A)\n(assert (=> (and A) B))\n");
    }

    @Test
    void flattensTopLevelConjunctionsOnly() {
        assertThat(clauses("""
                (declare-fun P (Int) Bool)
                (assert (forall ((x Int)) (=> (and (P x) (and (> x 0) (and (< x 9) (or (= x 1) (and (= x 2) (= x 3)))))) false)))
                """)).isEqualTo("(assert (forall ((x Int)) (=> (and (P x) (> x 0) (< x 9) (or (= x 1) (and (= x 2) (= x 3)))) false)))\n");
    }

    @Test
    void dropsTrueConjuncts() {
        assertThat(clauses("(declare-fun P (Int) Bool)(assert (forall ((x Int)) (=> (and (P x) true (and true)) false)))"))
                .isEqualTo("(assert (forall ((x Int)) (=> (and (P x)) false)))\n");
    }

    @Test
    void freshNamesAvoidEveryNameOfTheSystem() {
        assertThat(clauses("""
                (declare-fun P (Int) Bool)
                (declare-fun v_0 (Int) Bool)
                (assert (forall ((x Int) (x_0 Int)) (=> (and (P x) (v_0 x_0) (exists ((v_1 Int)) (< v_1 x))) (P 1))))
                """)).isEqualTo("(assert (forall ((v_2 Int) (x Int) (x_0 Int)) (=> (and (P x) (v_0 x_0) (exists ((v_1 Int)) (< v_1 x)) (= v_2 1)) (P v_2))))\n");
    }

    @Test
    void freshNamesAreUniqueAcrossClauses() {
        assertThat(clauses("(declare-fun P (Int) Bool)(assert (P 0))(assert (P 1))"))
                .isEqualTo("(assert (forall ((v_0 Int)) (=> (= v_0 0) (P v_0))))\n(assert (forall ((v_1 Int)) (=> (= v_1 1) (P v_1))))\n");
    }

    @Test
    void transformedLeavesTheInputUnchanged() {
        SingleSystem system = ChcLoader.load("(declare-fun P (Int) Bool)(assert (P 0))", "t");
        String before = system.text();
        ChcSystem result = NORM.transformed(system);
        assertThat(system.text()).isEqualTo(before);
        assertThat(((SingleSystem) result).text()).contains("v_0");
    }
}
