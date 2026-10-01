package hornassistant.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** A validated command line. */
record Request(Source source, Optional<Path> outputDir, boolean stdout, List<String> transformations) {

    Request {
        transformations = List.copyOf(transformations);
    }

    sealed interface Source {
        record File(Path path) implements Source {}

        record Stdin() implements Source {}
    }

    /**
     * Spec §6.3 item 3: the {@code --opt} values joined in order, split at commas, trimmed, without empty
     * entries; the default list when there is no {@code --opt}.
     */
    static List<String> transformationList(List<String> optValues, List<String> defaults) {
        if (optValues.isEmpty()) {
            return defaults;
        }
        return optValues.stream()
                .flatMap(v -> List.of(v.split(",", -1)).stream())
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Spec §6.3 item 2 and DECISIONS.md: the file name without its last extension, or {@code stdin}. */
    String systemName() {
        return switch (source) {
            case Source.Stdin() -> "stdin";
            case Source.File(var path) -> {
                String file = path.getFileName().toString();
                int dot = file.lastIndexOf('.');
                yield dot > 0 ? file.substring(0, dot) : file;
            }
        };
    }
}
