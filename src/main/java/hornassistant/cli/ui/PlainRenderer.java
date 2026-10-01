package hornassistant.cli.ui;

/** Renders a document as plain text, dropping all roles. */
public record PlainRenderer() implements Renderer {

    @Override
    public String render(Doc doc) {
        var out = new StringBuilder();
        append(doc, out);
        return out.toString();
    }

    private static void append(Doc doc, StringBuilder out) {
        switch (doc) {
            case Doc.Text(var text) -> out.append(text);
            case Doc.Styled(var _, var inner) -> append(inner, out);
            case Doc.Concat(var parts) -> parts.forEach(p -> append(p, out));
            case Doc.Line() -> out.append('\n');
        }
    }
}
