package hornassistant.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The golden cases in {@code src/test/resources/golden}: {@code <case>.smt2} is an input,
 * {@code <case>.<opts>.expected} the expected standard output, where {@code opts} is {@code default}
 * (no {@code --opt}), {@code none} ({@code --opt ""}) or the value given to {@code --opt}.
 */
record Golden(String name, Path input, String opts, Path expected) {

    static final Path DIR = Path.of("src/test/resources/golden");

    static List<Golden> all() {
        try (Stream<Path> files = Files.list(DIR)) {
            return files.map(Path::getFileName).map(Path::toString).filter(f -> f.endsWith(".expected")).sorted()
                    .map(f -> {
                        String stem = f.substring(0, f.length() - ".expected".length());
                        int dot = stem.indexOf('.');
                        String name = stem.substring(0, dot);
                        return new Golden(name, DIR.resolve(name + ".smt2"), stem.substring(dot + 1), DIR.resolve(f));
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Path> inputs() {
        try (Stream<Path> files = Files.list(DIR)) {
            return files.filter(f -> f.toString().endsWith(".smt2")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    List<String> args() {
        var args = new ArrayList<String>();
        args.add(input.toString());
        switch (opts) {
            case "default" -> {}
            case "none" -> args.addAll(List.of("--opt", ""));
            default -> args.addAll(List.of("--opt", opts));
        }
        return args;
    }

    String expectedText() {
        try {
            return Files.readString(expected);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return name + " [" + opts + "]";
    }
}
