package hornassistant.transform;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Hands out fresh symbol names {@code <prefix>_<n>}, with the smallest {@code n >= 0} that clashes with no
 * name in use. Every name handed out is marked as used. One instance serves one session (one load, or
 * one run of a transformation), so fresh names are unique within that session.
 */
public final class NameSupply {

    private final Set<String> used;

    /**
     * Where the search for each prefix resumes. Names are only ever added, so the smallest free number of
     * a prefix never decreases and the search need not restart at zero.
     */
    private final Map<String, Integer> next = new HashMap<>();

    public NameSupply(Collection<String> used) {
        this.used = new HashSet<>(used);
    }

    public void use(String name) {
        used.add(name);
    }

    public String fresh(String prefix) {
        for (int n = next.getOrDefault(prefix, 0); ; n++) {
            String candidate = prefix + "_" + n;
            if (used.add(candidate)) {
                next.put(prefix, n + 1);
                return candidate;
            }
        }
    }
}
