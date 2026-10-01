package hornassistant.cli.ui;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;

/** The TamboUI style of each {@link Role}. */
final class Styler {

    private Styler() {}

    static Style style(Role role) {
        return switch (role) {
            case ERROR_LABEL -> Style.EMPTY.bold().fg(Color.RED);
            case ERROR_TEXT -> Style.EMPTY.bold();
            case USAGE_ERROR -> Style.EMPTY.fg(Color.RED);
            case HEADING -> Style.EMPTY.bold();
            case COMMAND -> Style.EMPTY.bold();
            case OPTION -> Style.EMPTY.fg(Color.CYAN);
            case PARAMETER -> Style.EMPTY.fg(Color.YELLOW);
            case DEFAULT_VALUE -> Style.EMPTY.fg(Color.MAGENTA);
            case TRANSFORMATION -> Style.EMPTY.fg(Color.GREEN);
            case VERSION -> Style.EMPTY.bold();
        };
    }
}
