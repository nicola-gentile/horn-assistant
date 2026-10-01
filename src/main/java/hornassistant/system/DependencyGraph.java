package hornassistant.system;

import hornassistant.chc.Predicate;
import java.util.List;

/**
 * The dependency multigraph of a system: one edge per body application and clause, from the body
 * predicate to the head predicate. Facts start at {@link Sentinel#TRUE}, queries end at {@link Sentinel#FALSE}.
 */
public record DependencyGraph(List<Edge> edges) {
    public DependencyGraph {
        edges = List.copyOf(edges);
    }

    public sealed interface Node {}

    public record PredNode(Predicate predicate) implements Node {}

    public enum Sentinel implements Node {
        TRUE,
        FALSE
    }

    public record Edge(Node from, Node to, int clauseId) {}
}
