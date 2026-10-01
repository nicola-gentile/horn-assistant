package hornassistant.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void versionComesFromThePom() {
        assertThat(Version.current()).isEqualTo("0.1.0");
    }
}
