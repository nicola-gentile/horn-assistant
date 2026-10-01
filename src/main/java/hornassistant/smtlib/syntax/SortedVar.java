package hornassistant.smtlib.syntax;

/** A variable declaration {@code (name sort)}. */
public record SortedVar(String name, SortExpr sort) {}
