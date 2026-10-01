package hornassistant.smtlib.syntax;

/** A {@code let} binding {@code (name value)}. */
public record Binding(String name, TermExpr value) {}
