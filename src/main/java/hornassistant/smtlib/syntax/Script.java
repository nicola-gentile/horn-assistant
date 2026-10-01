package hornassistant.smtlib.syntax;

import java.util.List;

/** A parsed SMT-LIB script: its commands in source order. */
public record Script(List<Command> commands) {
    public Script {
        commands = List.copyOf(commands);
    }
}
