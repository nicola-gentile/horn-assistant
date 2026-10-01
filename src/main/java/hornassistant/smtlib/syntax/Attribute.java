package hornassistant.smtlib.syntax;

import java.util.Optional;

/** An attribute {@code :keyword value?}; the keyword is stored without the colon. */
public record Attribute(String keyword, Optional<SExpr> value) {}
