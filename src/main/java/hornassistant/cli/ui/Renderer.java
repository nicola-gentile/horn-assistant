package hornassistant.cli.ui;

/** Turns a {@link Doc} into the text written to a stream. */
public sealed interface Renderer permits PlainRenderer, TamboRenderer {

    String render(Doc doc);

    /** The renderer for a stream: styled when {@code styled} is set, plain otherwise. */
    static Renderer forStream(boolean styled) {
        return styled ? new TamboRenderer() : new PlainRenderer();
    }
}
