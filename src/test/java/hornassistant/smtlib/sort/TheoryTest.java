package hornassistant.smtlib.sort;

import static hornassistant.smtlib.sort.Sort.BOOL;
import static hornassistant.smtlib.sort.Sort.INT;
import static hornassistant.smtlib.sort.Sort.REAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hornassistant.smtlib.term.Op;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TheoryTest {

    private static final Sort INT_ARRAY = new Sort.Array(INT, BOOL);

    private static Sort check(Op op, Sort... args) {
        return Theory.check(op, List.of(), List.of(args), Optional.empty());
    }

    private static void rejects(Op op, Sort... args) {
        assertThatThrownBy(() -> check(op, args)).isInstanceOf(IllSortedException.class);
    }

    @Test
    void not() {
        assertThat(check(Op.NOT, BOOL)).isEqualTo(BOOL);
        rejects(Op.NOT, INT);
        rejects(Op.NOT, BOOL, BOOL);
    }

    @Test
    void connectivesAcceptAnyNumberOfBooleans() {
        for (Op op : List.of(Op.AND, Op.OR, Op.XOR)) {
            assertThat(check(op)).isEqualTo(BOOL);
            assertThat(check(op, BOOL)).isEqualTo(BOOL);
            assertThat(check(op, BOOL, BOOL, BOOL)).isEqualTo(BOOL);
            rejects(op, BOOL, INT);
        }
    }

    @Test
    void impliesIsBinaryAfterDesugaring() {
        assertThat(check(Op.IMPLIES, BOOL, BOOL)).isEqualTo(BOOL);
        rejects(Op.IMPLIES, BOOL);
        rejects(Op.IMPLIES, BOOL, BOOL, BOOL);
        rejects(Op.IMPLIES, INT, BOOL);
    }

    @Test
    void equalityAndDistinctNeedTwoArgumentsOfOneSort() {
        for (Op op : List.of(Op.EQ, Op.DISTINCT)) {
            assertThat(check(op, INT, INT)).isEqualTo(BOOL);
            assertThat(check(op, INT_ARRAY, INT_ARRAY, INT_ARRAY)).isEqualTo(BOOL);
            rejects(op, INT);
            rejects(op, INT, REAL);
        }
    }

    @Test
    void ite() {
        assertThat(check(Op.ITE, BOOL, REAL, REAL)).isEqualTo(REAL);
        rejects(Op.ITE, INT, REAL, REAL);
        rejects(Op.ITE, BOOL, INT, REAL);
        rejects(Op.ITE, BOOL, INT);
    }

    @Test
    void arithmetic() {
        for (Op op : List.of(Op.ADD, Op.SUB, Op.MUL)) {
            assertThat(check(op, INT, INT, INT)).isEqualTo(INT);
            assertThat(check(op, REAL, REAL)).isEqualTo(REAL);
            rejects(op, INT, REAL);
            rejects(op, BOOL, BOOL);
        }
        assertThat(check(Op.SUB, INT)).isEqualTo(INT);
        assertThat(check(Op.SUB, REAL)).isEqualTo(REAL);
        rejects(Op.ADD, INT);
        rejects(Op.MUL, INT);
        rejects(Op.SUB);
    }

    @Test
    void realDivision() {
        assertThat(check(Op.DIV_REAL, REAL, REAL, REAL)).isEqualTo(REAL);
        rejects(Op.DIV_REAL, REAL);
        rejects(Op.DIV_REAL, INT, INT);
    }

    @Test
    void integerDivisionModAndAbs() {
        assertThat(check(Op.IDIV, INT, INT)).isEqualTo(INT);
        assertThat(check(Op.IDIV, INT, INT, INT)).isEqualTo(INT);
        rejects(Op.IDIV, REAL, REAL);
        assertThat(check(Op.MOD, INT, INT)).isEqualTo(INT);
        rejects(Op.MOD, INT, INT, INT);
        assertThat(check(Op.ABS, INT)).isEqualTo(INT);
        rejects(Op.ABS, REAL);
    }

    @Test
    void comparisonsAreChainable() {
        for (Op op : List.of(Op.LE, Op.LT, Op.GE, Op.GT)) {
            assertThat(check(op, INT, INT)).isEqualTo(BOOL);
            assertThat(check(op, REAL, REAL, REAL)).isEqualTo(BOOL);
            rejects(op, INT);
            rejects(op, INT, REAL);
            rejects(op, BOOL, BOOL);
        }
    }

    @Test
    void conversions() {
        assertThat(check(Op.TO_REAL, INT)).isEqualTo(REAL);
        assertThat(check(Op.TO_INT, REAL)).isEqualTo(INT);
        assertThat(check(Op.IS_INT, REAL)).isEqualTo(BOOL);
        rejects(Op.TO_REAL, REAL);
        rejects(Op.TO_INT, INT);
        rejects(Op.IS_INT, INT);
    }

    @Test
    void arrays() {
        assertThat(check(Op.SELECT, INT_ARRAY, INT)).isEqualTo(BOOL);
        assertThat(check(Op.STORE, INT_ARRAY, INT, BOOL)).isEqualTo(INT_ARRAY);
        rejects(Op.SELECT, INT_ARRAY, REAL);
        rejects(Op.SELECT, INT, INT);
        rejects(Op.STORE, INT_ARRAY, INT, INT);
    }

    @Test
    void constantArrayTakesItsSortFromTheAnnotation() {
        assertThat(Theory.check(Op.CONST_ARRAY, List.of(), List.of(BOOL), Optional.of(INT_ARRAY))).isEqualTo(INT_ARRAY);
        assertThatThrownBy(() -> Theory.check(Op.CONST_ARRAY, List.of(), List.of(INT), Optional.of(INT_ARRAY)))
                .isInstanceOf(IllSortedException.class);
        assertThatThrownBy(() -> Theory.check(Op.CONST_ARRAY, List.of(), List.of(INT), Optional.of(INT)))
                .isInstanceOf(IllSortedException.class);
        assertThatThrownBy(() -> Theory.check(Op.CONST_ARRAY, List.of(), List.of(INT), Optional.empty()))
                .isInstanceOf(IllSortedException.class);
    }

    @Test
    void theoryOperatorsTakeNoIndices() {
        assertThatThrownBy(() -> Theory.check(Op.ADD, List.of(BigInteger.ONE), List.of(INT, INT), Optional.empty()))
                .isInstanceOf(IllSortedException.class);
    }
}
