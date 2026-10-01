package hornassistant.transform;

import hornassistant.system.ChcSystem;

/** A satisfiability-preserving rewrite of a system (specification §7.1). */
public interface Transformation {

    /** The unique name used by {@code --opt}. */
    String name();

    /** One line of help text. */
    String help();

    /** Changes {@code system} in place. */
    void transform(ChcSystem system);

    /** Returns the transformed system and leaves {@code system} unchanged. */
    default ChcSystem transformed(ChcSystem system) {
        ChcSystem copy = system.copy();
        transform(copy);
        return copy;
    }
}
