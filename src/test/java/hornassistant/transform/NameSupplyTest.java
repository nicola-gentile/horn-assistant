package hornassistant.transform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NameSupplyTest {

    @Test
    void startsCountingAtZero() {
        var names = new NameSupply(List.of());
        assertThat(names.fresh("v")).isEqualTo("v_0");
        assertThat(names.fresh("v")).isEqualTo("v_1");
        assertThat(names.fresh("x")).isEqualTo("x_0");
    }

    @Test
    void skipsNamesAlreadyInUse() {
        var names = new NameSupply(List.of("x", "x_0", "x_2"));
        assertThat(names.fresh("x")).isEqualTo("x_1");
        assertThat(names.fresh("x")).isEqualTo("x_3");
    }

    @Test
    void namesMarkedAsUsedAreSkipped() {
        var names = new NameSupply(List.of());
        names.use("v_0");
        assertThat(names.fresh("v")).isEqualTo("v_1");
    }

    @Test
    void freshNamesNeverClashWithEachOther() {
        var names = new NameSupply(List.of("a_0"));
        // "a" + "_0" from prefix "a" and prefix "a_0" + "_0" are distinct; each must be new.
        assertThat(names.fresh("a")).isEqualTo("a_1");
        assertThat(names.fresh("a_0")).isEqualTo("a_0_0");
        assertThat(names.fresh("a_0")).isEqualTo("a_0_1");
    }
}
