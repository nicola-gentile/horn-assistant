package hornassistant.smtlib.syntax;

import java.util.List;

/** A plain symbol ({@code indices} empty) or an indexed identifier {@code (_ symbol index+)}. */
public record Identifier(String symbol, List<Index> indices) {
    public Identifier {
        indices = List.copyOf(indices);
    }

    public static Identifier of(String symbol) {
        return new Identifier(symbol, List.of());
    }
}
