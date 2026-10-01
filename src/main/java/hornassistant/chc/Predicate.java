package hornassistant.chc;

import hornassistant.smtlib.sort.Sort;
import java.util.List;

/** An uninterpreted function symbol with result sort {@code Bool}. */
public record Predicate(String name, List<Sort> argSorts) {
    public Predicate {
        argSorts = List.copyOf(argSorts);
    }

    public int arity() {
        return argSorts.size();
    }
}
