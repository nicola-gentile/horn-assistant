package hornassistant.chc;

import hornassistant.smtlib.term.Term;

/** The head of a clause: a predicate application, or {@code false} for a query. */
public sealed interface Head {

    record Pred(Term.PredApp app) implements Head {}

    record False() implements Head {}

    Head FALSE = new False();
}
