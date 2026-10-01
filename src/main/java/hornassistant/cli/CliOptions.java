package hornassistant.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The command-line options of specification §6, as picocli reads them. Picocli fills these fields; the
 * rest of the program only sees the immutable {@link Request} built from them.
 */
@Command(
        name = "horn-assistant",
        customSynopsis = "horn-assistant [INPUT] [--in] [-o DIR | --output-dir DIR] [--out] [--opt NAMES]... [--version] [--help]",
        description = "Preprocess a Constrained Horn Clause system written in SMT-LIB v2.",
        sortOptions = false)
final class CliOptions {

    @Parameters(arity = "0..1", paramLabel = "INPUT", description = "Path of the input file.")
    Path input;

    @Option(names = "--in", description = "Read the input from standard input instead of a file.")
    boolean stdin;

    @Option(names = {"-o", "--output-dir"}, paramLabel = "DIR",
            description = "Write the result into directory DIR, which is created if missing.")
    Path outputDir;

    @Option(names = "--out", description = "Write the result to standard output (also the default without -o).")
    boolean stdout;

    @Option(names = "--opt", paramLabel = "NAMES",
            description = "Comma-separated transformations to apply, in order. May be repeated.")
    List<String> opt = new ArrayList<>();

    @Option(names = "--version", versionHelp = true, description = "Print the version and exit.")
    boolean version;

    @Option(names = "--help", usageHelp = true, description = "Print this help and exit.")
    boolean help;
}
