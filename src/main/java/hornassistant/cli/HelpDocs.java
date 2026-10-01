package hornassistant.cli;

import hornassistant.cli.ui.Doc;
import hornassistant.cli.ui.Role;
import hornassistant.transform.Transformation;
import hornassistant.transform.TransformationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/** Builds help, version and diagnostic documents. Pure functions. */
final class HelpDocs {

    private static final int COLUMN = 26;

    private HelpDocs() {}

    static Doc error(String message) {
        return Doc.concat(Doc.styled(Role.ERROR_LABEL, "error:"), Doc.text(" "), Doc.styled(Role.ERROR_TEXT, message), Doc.line());
    }

    static Doc usageError(String message, CommandSpec spec) {
        return Doc.concat(
                Doc.styled(Role.ERROR_LABEL, "error:"), Doc.text(" "), Doc.styled(Role.USAGE_ERROR, message), Doc.line(),
                synopsis(spec),
                Doc.text("Try '"), Doc.styled(Role.COMMAND, spec.name()), Doc.text(" "), Doc.styled(Role.OPTION, "--help"),
                Doc.text("' for more information."), Doc.line());
    }

    static Doc version(String version) {
        return Doc.concat(Doc.styled(Role.COMMAND, "horn-assistant"), Doc.text(" "), Doc.styled(Role.VERSION, version), Doc.line());
    }

    static Doc help(CommandSpec spec, TransformationRegistry registry, List<String> defaults) {
        var parts = new ArrayList<Doc>();
        parts.add(synopsis(spec));
        parts.add(Doc.line());
        for (String line : spec.usageMessage().description()) {
            parts.add(Doc.text(line));
            parts.add(Doc.line());
        }
        parts.add(Doc.line());

        parts.add(heading("Arguments:"));
        for (PositionalParamSpec p : spec.positionalParameters()) {
            parts.add(row(Doc.styled(Role.PARAMETER, p.paramLabel()), p.paramLabel().length(), String.join(" ", p.description())));
        }
        parts.add(Doc.line());

        parts.add(heading("Options:"));
        for (OptionSpec o : spec.options()) {
            parts.add(optionRow(o));
        }
        parts.add(Doc.line());

        parts.add(heading("Transformations:"));
        int width = registry.all().stream().mapToInt(t -> t.name().length()).max().orElse(0);
        for (Transformation t : registry.all()) {
            parts.add(Doc.concat(Doc.text("  "), Doc.styled(Role.TRANSFORMATION, t.name()),
                    Doc.text(" ".repeat(width - t.name().length() + 2) + t.help()), Doc.line()));
        }
        parts.add(Doc.line());
        parts.add(Doc.concat(Doc.styled(Role.HEADING, "Default:"), Doc.text(" "),
                Doc.styled(Role.DEFAULT_VALUE, defaults.isEmpty() ? "(none)" : String.join(",", defaults)), Doc.line()));
        parts.add(Doc.line());

        parts.add(heading("Exit status:"));
        parts.add(Doc.text("  0  success"));
        parts.add(Doc.line());
        parts.add(Doc.text("  1  the input is not a CHC system, or a transformation name is unknown"));
        parts.add(Doc.line());
        parts.add(Doc.text("  2  usage error"));
        parts.add(Doc.line());
        return Doc.concat(parts);
    }

    private static Doc synopsis(CommandSpec spec) {
        String synopsis = String.join(" ", spec.usageMessage().customSynopsis());
        String rest = synopsis.substring(spec.name().length());
        return Doc.concat(Doc.styled(Role.HEADING, "Usage:"), Doc.text(" "), Doc.styled(Role.COMMAND, spec.name()), Doc.text(rest), Doc.line());
    }

    private static Doc heading(String text) {
        return Doc.concat(Doc.styled(Role.HEADING, text), Doc.line());
    }

    private static Doc optionRow(OptionSpec o) {
        // Short names in the first column, long names aligned after them: "  -o, --output-dir DIR".
        List<String> names = List.of(o.names());
        String shortName = names.stream().filter(n -> !n.startsWith("--")).findFirst().orElse("");
        String longNames = names.stream().filter(n -> n.startsWith("--")).collect(Collectors.joining(", "));
        var cell = new ArrayList<Doc>();
        int width = 0;
        if (shortName.isEmpty()) {
            cell.add(Doc.text("    "));
            width += 4;
        } else {
            cell.add(Doc.styled(Role.OPTION, shortName));
            cell.add(Doc.text(", "));
            width += shortName.length() + 2;
        }
        cell.add(Doc.styled(Role.OPTION, longNames));
        width += longNames.length();
        if (o.arity().max() > 0) {
            cell.add(Doc.text(" "));
            cell.add(Doc.styled(Role.PARAMETER, o.paramLabel()));
            width += 1 + o.paramLabel().length();
        }
        return row(Doc.concat(cell), width, String.join(" ", o.description()));
    }

    private static Doc row(Doc cell, int cellWidth, String description) {
        int pad = Math.max(2, COLUMN - 2 - cellWidth);
        return Doc.concat(Doc.text("  "), cell, Doc.text(" ".repeat(pad) + description), Doc.line());
    }
}
