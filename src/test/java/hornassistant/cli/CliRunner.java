package hornassistant.cli;

import hornassistant.cli.ui.TerminalCapabilities;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs horn-assistant for the CLI and golden tests: in-process through {@link Main#run}, or, when the
 * system property {@code horn.exe} is set, as a subprocess. {@code horn.exe} is a command line split at
 * whitespace, e.g. {@code target/horn-assistant} or {@code java -jar target/horn-assistant.jar}.
 */
interface CliRunner {

    record Result(int status, String stdout, String stderr) {}

    Result run(List<String> args, String stdin, Map<String, String> env);

    default Result run(String... args) {
        return run(List.of(args), "", Map.of());
    }

    static CliRunner current() {
        String exe = System.getProperty("horn.exe", "").strip();
        return exe.isEmpty() ? new InProcess(TerminalCapabilities.NONE) : new External(List.of(exe.split("\\s+")));
    }

    static boolean isExternal() {
        return !System.getProperty("horn.exe", "").isBlank();
    }

    record InProcess(TerminalCapabilities terminal) implements CliRunner {
        @Override
        public Result run(List<String> args, String stdin, Map<String, String> env) {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            int status = Main.run(
                    args.toArray(String[]::new),
                    new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                    new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8),
                    terminal);
            return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        }
    }

    record External(List<String> command) implements CliRunner {
        @Override
        public Result run(List<String> args, String stdin, Map<String, String> env) {
            try {
                Path dir = Files.createTempDirectory("horn-run");
                Path in = Files.writeString(dir.resolve("stdin"), stdin, StandardCharsets.UTF_8);
                Path out = dir.resolve("stdout");
                Path err = dir.resolve("stderr");
                var cmd = new ArrayList<>(command);
                cmd.addAll(args);
                var builder = new ProcessBuilder(cmd).redirectInput(in.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile());
                builder.environment().putAll(env);
                Process process = builder.start();
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalStateException("timeout running " + cmd);
                }
                return new Result(process.exitValue(), Files.readString(out, StandardCharsets.UTF_8), Files.readString(err, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String toString() {
            return "External" + Arrays.toString(command.toArray());
        }
    }
}
