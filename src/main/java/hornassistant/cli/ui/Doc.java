package hornassistant.cli.ui;

import java.util.List;

/** A styled text document: help, version and diagnostics are built as {@code Doc} values. */
public sealed interface Doc {

    /** Text without line breaks. */
    record Text(String text) implements Doc {
        public Text {
            if (text.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("use Doc.Line for line breaks");
            }
        }
    }

    record Styled(Role role, Doc doc) implements Doc {}

    record Concat(List<Doc> parts) implements Doc {
        public Concat {
            parts = List.copyOf(parts);
        }
    }

    /** A line break. */
    record Line() implements Doc {}

    static Doc text(String text) {
        return new Text(text);
    }

    static Doc styled(Role role, String text) {
        return new Styled(role, new Text(text));
    }

    static Doc concat(Doc... parts) {
        return new Concat(List.of(parts));
    }

    static Doc concat(List<Doc> parts) {
        return new Concat(parts);
    }

    static Doc line() {
        return new Line();
    }
}
