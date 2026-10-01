package hornassistant.smtlib.syntax;

import java.math.BigInteger;

/** An index of an indexed identifier. */
public sealed interface Index {

    record Num(BigInteger value) implements Index {}

    record Sym(String name) implements Index {}
}
