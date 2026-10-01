package hornassistant.transform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

/** The available transformations, keyed by name, in registration order. Immutable. */
public final class TransformationRegistry {

    /** Transformations applied when no {@code --opt} is given. */
    public static final List<String> DEFAULT_LIST = List.of("norm");

    private final SequencedMap<String, Transformation> byName;

    private TransformationRegistry(SequencedMap<String, Transformation> byName) {
        this.byName = byName;
    }

    public static TransformationRegistry empty() {
        return new TransformationRegistry(new LinkedHashMap<>());
    }

    /** Every transformation horn-assistant provides. Registered explicitly, without reflection. */
    public static TransformationRegistry standard() {
        return empty().register(new Norm());
    }

    /**
     * Returns a registry that also holds {@code t}.
     *
     * @throws IllegalStateException if a transformation with the same name is already registered
     */
    public TransformationRegistry register(Transformation t) {
        if (byName.containsKey(t.name())) {
            throw new IllegalStateException("transformation " + t.name() + " is already registered");
        }
        var map = new LinkedHashMap<>(byName);
        map.put(t.name(), t);
        return new TransformationRegistry(map);
    }

    public Optional<Transformation> lookup(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<Transformation> all() {
        return List.copyOf(byName.values());
    }
}
