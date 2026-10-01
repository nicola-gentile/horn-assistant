package hornassistant.chc;

import hornassistant.smtlib.parser.SmtLibParserFacade;
import hornassistant.smtlib.sort.Resolver;
import hornassistant.smtlib.term.Term;
import hornassistant.system.SingleSystem;

/** Reads an SMT-LIB script into a single system (specification §4). */
public final class ChcLoader {

    private ChcLoader() {}

    /**
     * Parses, resolves and extracts the clauses of {@code text}. The system holds every declared predicate
     * and one clause per assertion, in assertion order.
     *
     * @throws ChcInputException if the input is not a CHC system (§4.3)
     */
    public static SingleSystem load(String text, String name) {
        Resolver.Resolved resolved = Resolver.resolve(SmtLibParserFacade.parse(text));
        if (resolved.assertions().isEmpty()) {
            throw new ChcInputException("no assert command");
        }
        var system = new SingleSystem(name);
        resolved.predicates().forEach(system::addPredicate);
        for (Term assertion : resolved.assertions()) {
            Clause c = ClauseExtractor.extract(assertion);
            system.addHyperedge(c.body(), c.constraint(), c.head());
        }
        return system;
    }
}
