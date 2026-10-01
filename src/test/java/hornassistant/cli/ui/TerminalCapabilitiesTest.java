package hornassistant.cli.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TerminalCapabilitiesTest {

    private static final Map<String, String> XTERM = Map.of("TERM", "xterm-256color");

    @Test
    void stylesTerminalsOnly() {
        assertThat(TerminalCapabilities.detect(XTERM, true, false)).isEqualTo(new TerminalCapabilities(true, false));
        assertThat(TerminalCapabilities.detect(XTERM, false, true)).isEqualTo(new TerminalCapabilities(false, true));
        assertThat(TerminalCapabilities.detect(Map.of(), true, true)).isEqualTo(new TerminalCapabilities(true, true));
    }

    @Test
    void noColorDisablesStyling() {
        assertThat(TerminalCapabilities.detect(Map.of("NO_COLOR", "1", "TERM", "xterm"), true, true)).isEqualTo(TerminalCapabilities.NONE);
    }

    @Test
    void emptyNoColorIsIgnored() {
        assertThat(TerminalCapabilities.detect(Map.of("NO_COLOR", ""), true, true)).isEqualTo(new TerminalCapabilities(true, true));
    }

    @Test
    void dumbTerminalDisablesStyling() {
        assertThat(TerminalCapabilities.detect(Map.of("TERM", "dumb"), true, true)).isEqualTo(TerminalCapabilities.NONE);
    }
}
