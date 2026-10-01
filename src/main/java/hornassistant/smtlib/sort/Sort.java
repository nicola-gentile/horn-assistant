package hornassistant.smtlib.sort;

/** The sorts supported by horn-assistant. */
public sealed interface Sort {

    Sort BOOL = new Bool();
    Sort INT = new Int();
    Sort REAL = new Real();

    record Bool() implements Sort {}

    record Int() implements Sort {}

    record Real() implements Sort {}

    record Array(Sort index, Sort element) implements Sort {}
}
