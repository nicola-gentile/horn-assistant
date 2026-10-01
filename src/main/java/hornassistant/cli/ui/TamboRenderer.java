package hornassistant.cli.ui;

import dev.tamboui.style.Style;
import dev.tamboui.terminal.AnsiStringBuilder;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a document with ANSI styles through TamboUI's text model: the document becomes TamboUI
 * {@link Line}s of styled {@link Span}s, and each span is written with TamboUI's ANSI style sequences.
 * Nothing touches the terminal itself (no raw mode, no alternate screen, no backend). If TamboUI fails,
 * the plain rendering is returned instead.
 */
public record TamboRenderer() implements Renderer {

    @Override
    public String render(Doc doc) {
        try {
            return ansi(lines(doc));
        } catch (RuntimeException | LinkageError e) {
            return new PlainRenderer().render(doc);
        }
    }

    /** The document as TamboUI lines; the last element is the text after the final line break. */
    private static List<Line> lines(Doc doc) {
        var lines = new ArrayList<Line>();
        var current = new ArrayList<Span>();
        collect(doc, Style.EMPTY, lines, current);
        lines.add(Line.from(current));
        return lines;
    }

    private static void collect(Doc doc, Style style, List<Line> lines, List<Span> current) {
        switch (doc) {
            case Doc.Text(var text) -> {
                if (!text.isEmpty()) {
                    current.add(Span.styled(text, style));
                }
            }
            case Doc.Styled(var role, var inner) -> collect(inner, style.patch(Styler.style(role)), lines, current);
            case Doc.Concat(var parts) -> parts.forEach(p -> collect(p, style, lines, current));
            case Doc.Line() -> {
                lines.add(Line.from(List.copyOf(current)));
                current.clear();
            }
        }
    }

    private static String ansi(List<Line> lines) {
        var out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            for (Span span : lines.get(i).spans()) {
                if (span.style().equals(Style.EMPTY)) {
                    out.append(span.content());
                } else {
                    out.append(AnsiStringBuilder.styleToAnsi(span.style())).append(span.content()).append(AnsiStringBuilder.RESET);
                }
            }
        }
        return out.toString();
    }
}
