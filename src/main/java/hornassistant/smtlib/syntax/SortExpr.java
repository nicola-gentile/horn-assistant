package hornassistant.smtlib.syntax;

import java.util.List;

/** A sort as written: an identifier applied to zero or more parameter sorts. */
public record SortExpr(Identifier id, List<SortExpr> params) {
    public SortExpr {
        params = List.copyOf(params);
    }

    public static SortExpr of(String symbol) {
        return new SortExpr(Identifier.of(symbol), List.of());
    }
}
