package hornassistant.system;

import hornassistant.chc.Predicate;
import hornassistant.smtlib.term.Term;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/** A value that transformations consume and produce (specification §3.1). */
public sealed interface ChcSystem permits SingleSystem {

    /** Used to name output files. */
    String name();

    /** The predicates, sorted by name. */
    Collection<Predicate> predicates();

    /** The clause formulas (§5.2) in system order. */
    List<Term> clauses();

    /** An independent copy: changing either system does not affect the other. */
    ChcSystem copy();

    /** Writes the system in the output format of §5. */
    void write(PrintStream out);

    /** Creates {@code folder} and its missing parents, writes the system into it and returns the files written. */
    List<Path> dump(Path folder) throws IOException;
}
