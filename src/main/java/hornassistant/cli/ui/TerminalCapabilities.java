package hornassistant.cli.ui;

import java.io.Console;
import java.util.Map;

/** Whether styled output may be written to standard output and to standard error. */
public record TerminalCapabilities(boolean styleStdout, boolean styleStderr) {

    /** Plain output on both streams. */
    public static final TerminalCapabilities NONE = new TerminalCapabilities(false, false);

    /**
     * Styling is used for a stream only if it is a terminal, {@code NO_COLOR} is unset or empty, and
     * {@code TERM} is not {@code dumb}.
     */
    public static TerminalCapabilities detect(Map<String, String> env, boolean stdoutTerminal, boolean stderrTerminal) {
        String noColor = env.getOrDefault("NO_COLOR", "");
        boolean allowed = noColor.isEmpty() && !"dumb".equals(env.get("TERM"));
        return new TerminalCapabilities(allowed && stdoutTerminal, allowed && stderrTerminal);
    }

    /**
     * Detects the capabilities of this process. The JDK only tells whether the process has an interactive
     * terminal ({@link Console#isTerminal()}), not which streams are redirected, so both streams follow it.
     */
    public static TerminalCapabilities detect() {
        Console console = System.console();
        boolean terminal = console != null && console.isTerminal();
        return detect(System.getenv(), terminal, terminal);
    }
}
