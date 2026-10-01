package hornassistant.smtlib.syntax;

import java.util.List;

/** A top-level SMT-LIB command. */
public sealed interface Command {

    record SetLogic(String logic) implements Command {}

    record DeclareFun(String name, List<SortExpr> argSorts, SortExpr resultSort) implements Command {
        public DeclareFun {
            argSorts = List.copyOf(argSorts);
        }
    }

    record DeclareConst(String name, SortExpr sort) implements Command {}

    record DefineFun(String name, List<SortedVar> params, SortExpr resultSort, TermExpr body) implements Command {
        public DefineFun {
            params = List.copyOf(params);
        }
    }

    record Assert(TermExpr term) implements Command {}

    record CheckSat() implements Command {}

    /** A command that is parsed but cannot be expressed in the output, such as {@code declare-datatypes}. */
    record Unsupported(String commandName, List<SExpr> args) implements Command {
        public Unsupported {
            args = List.copyOf(args);
        }
    }

    /** A command without effect on the result, such as {@code set-info}, {@code push} or {@code exit}. */
    record Other(String commandName, List<SExpr> args) implements Command {
        public Other {
            args = List.copyOf(args);
        }
    }
}
