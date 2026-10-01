package hornassistant.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoldenTest {

    static List<Golden> cases() {
        return Golden.all();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void matchesExpectedOutput(Golden golden) {
        var result = CliRunner.current().run(golden.args(), "", Map.of());
        assertThat(result.stderr()).isEmpty();
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo(golden.expectedText());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void outputIsTheSameOnEveryRun(Golden golden) {
        var first = CliRunner.current().run(golden.args(), "", Map.of());
        var second = CliRunner.current().run(golden.args(), "", Map.of());
        assertThat(second).isEqualTo(first);
    }
}
