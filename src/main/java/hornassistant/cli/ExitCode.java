package hornassistant.cli;

/** Exit statuses of specification §6.4. */
enum ExitCode {
    SUCCESS(0),
    /** The input is not a CHC system, a transformation name is unknown, or the output cannot be written. */
    FAILURE(1),
    USAGE(2);

    final int status;

    ExitCode(int status) {
        this.status = status;
    }
}
