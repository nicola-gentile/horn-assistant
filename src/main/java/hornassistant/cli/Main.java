package hornassistant.cli;

import hornassistant.chc.ChcInputException;
import hornassistant.chc.ChcLoader;
import hornassistant.cli.ui.Renderer;
import hornassistant.cli.ui.TerminalCapabilities;
import hornassistant.system.ChcSystem;
import hornassistant.transform.Transformation;
import hornassistant.transform.TransformationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Command-line entry point (specification §6). {@link #main} is the only place that exits the JVM;
 * everything else goes through {@link #run}, which tests call directly.
 */
public final class Main {

    /**
     * Terms are processed recursively, so the work runs on a thread with a large stack. The size is only
     * reserved address space; memory is committed as the stack actually grows.
     */
    private static final long STACK_SIZE = 1L << 30;

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err, TerminalCapabilities.detect()));
    }

    /** Runs with plain (unstyled) output on both streams. */
    public static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        return run(args, in, out, err, TerminalCapabilities.NONE);
    }

    public static int run(String[] args, InputStream in, PrintStream out, PrintStream err, TerminalCapabilities terminal) {
        var invocation = new Invocation(in, out, err, Renderer.forStream(terminal.styleStdout()), Renderer.forStream(terminal.styleStderr()));
        var task = new FutureTask<>(() -> invocation.run(args).status);
        var thread = new Thread(null, task, "horn-assistant", STACK_SIZE);
        thread.start();
        try {
            return task.get();
        } catch (ExecutionException e) {
            return invocation.fail("internal error: " + e.getCause()).status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return invocation.fail("interrupted").status;
        }
    }

    /** One run of the program with its streams. */
    private record Invocation(InputStream in, PrintStream out, PrintStream err, Renderer outRenderer, Renderer errRenderer) {

        /** Thrown to stop with a usage error (status 2). */
        private static final class UsageError extends Exception {
            private static final long serialVersionUID = 1L;

            UsageError(String message) {
                super(message, null, false, false);
            }
        }

        ExitCode run(String[] args) {
            var options = new CliOptions();
            var commandLine = new CommandLine(options);
            commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
            CommandSpec spec = commandLine.getCommandSpec();
            TransformationRegistry registry = TransformationRegistry.standard();
            try {
                commandLine.parseArgs(args);
                if (options.help) {
                    emit(out, outRenderer.render(HelpDocs.help(spec, registry, TransformationRegistry.DEFAULT_LIST)));
                    return ExitCode.SUCCESS;
                }
                if (options.version) {
                    emit(out, outRenderer.render(HelpDocs.version(Version.current())));
                    return ExitCode.SUCCESS;
                }
                Request request = request(options);
                return execute(request, registry);
            } catch (CommandLine.ParameterException | UsageError e) {
                emit(err, errRenderer.render(HelpDocs.usageError(oneLine(e.getMessage()), spec)));
                return ExitCode.USAGE;
            } catch (ChcInputException e) {
                return fail(e.getMessage());
            } catch (StackOverflowError e) {
                return fail("the input is nested too deeply");
            } catch (RuntimeException e) {
                return fail("internal error: " + e);
            }
        }

        ExitCode fail(String message) {
            emit(err, errRenderer.render(HelpDocs.error(oneLine(message))));
            return ExitCode.FAILURE;
        }

        /** Checks the input source (spec §6.3 item 1) and builds the request. */
        private static Request request(CliOptions options) throws UsageError {
            Request.Source source;
            if (options.input != null && options.stdin) {
                throw new UsageError("INPUT and --in cannot be used together");
            } else if (options.input != null) {
                Path path = options.input;
                if (!Files.exists(path)) {
                    throw new UsageError("INPUT does not exist: " + path);
                } else if (!Files.isRegularFile(path)) {
                    throw new UsageError("INPUT is not a regular file: " + path);
                } else if (!Files.isReadable(path)) {
                    throw new UsageError("INPUT is not readable: " + path);
                }
                source = new Request.Source.File(path);
            } else if (options.stdin) {
                source = new Request.Source.Stdin();
            } else {
                throw new UsageError("no input: give INPUT or --in");
            }
            List<String> names = Request.transformationList(options.opt, TransformationRegistry.DEFAULT_LIST);
            return new Request(source, Optional.ofNullable(options.outputDir), options.stdout, names);
        }

        private ExitCode execute(Request request, TransformationRegistry registry) throws UsageError {
            var pipeline = new ArrayList<Transformation>();
            for (String name : request.transformations()) {
                pipeline.add(registry.lookup(name).orElseThrow(() -> new ChcInputException("unknown transformation: " + name)));
            }

            ChcSystem system = ChcLoader.load(readInput(request.source()), request.systemName());
            for (Transformation t : pipeline) {
                system = t.transformed(system);
            }

            // Nothing is written until the whole pipeline has succeeded.
            var text = new ByteArrayOutputStream();
            system.write(new PrintStream(text, true, StandardCharsets.UTF_8));
            if (request.outputDir().isPresent()) {
                Path dir = request.outputDir().get();
                try {
                    system.dump(dir);
                } catch (IOException e) {
                    return fail("cannot write to " + dir + ": " + describe(e));
                }
            }
            if (request.stdout() || request.outputDir().isEmpty()) {
                out.writeBytes(text.toByteArray());
                out.flush();
            }
            return ExitCode.SUCCESS;
        }

        private String readInput(Request.Source source) throws UsageError {
            byte[] bytes;
            switch (source) {
                case Request.Source.File(var path) -> {
                    try {
                        bytes = Files.readAllBytes(path);
                    } catch (IOException e) {
                        throw new UsageError("cannot read INPUT " + path + ": " + describe(e));
                    }
                }
                case Request.Source.Stdin() -> {
                    try {
                        bytes = in.readAllBytes();
                    } catch (IOException e) {
                        throw new ChcInputException("cannot read standard input: " + describe(e));
                    }
                }
            }
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException e) {
                throw new ChcInputException("input is not valid UTF-8");
            }
        }

        private static String describe(IOException e) {
            String type = e.getClass().getSimpleName();
            return e.getMessage() == null ? type : type + ": " + e.getMessage();
        }

        private static String oneLine(String message) {
            return String.valueOf(message).replace("\r", "\\r").replace("\n", "\\n");
        }

        private static void emit(PrintStream stream, String text) {
            stream.writeBytes(text.getBytes(StandardCharsets.UTF_8));
            stream.flush();
        }
    }
}
