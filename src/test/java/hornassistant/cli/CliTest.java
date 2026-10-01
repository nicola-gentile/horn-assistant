package hornassistant.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hornassistant.cli.ui.TerminalCapabilities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    private static final String G1 = Golden.DIR.resolve("g1.smt2").toString();
    private static final String ESC = "\u001b";

    @TempDir
    Path tmp;

    private static String golden(String file) throws IOException {
        return Files.readString(Golden.DIR.resolve(file));
    }

    private static CliRunner.Result run(String... args) {
        return CliRunner.current().run(args);
    }

    private static void assertUsageError(CliRunner.Result result) {
        assertThat(result.status()).isEqualTo(2);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).startsWith("error: ").contains("Usage: horn-assistant");
    }

    // ------------------------------------------------------------ input source

    @Test
    void bothInputAndStdinIsAUsageError() {
        assertUsageError(run(G1, "--in"));
    }

    @Test
    void neitherInputNorStdinIsAUsageError() {
        assertUsageError(run());
    }

    @Test
    void missingInputFileIsAUsageError() {
        assertUsageError(run(tmp.resolve("nope.smt2").toString()));
    }

    @Test
    void directoryAsInputIsAUsageError() {
        assertUsageError(run(tmp.toString()));
    }

    @Test
    void unknownOptionIsAUsageError() {
        assertUsageError(run(G1, "--frobnicate"));
    }

    @Test
    void missingOptionValueIsAUsageError() {
        assertUsageError(run(G1, "-o"));
    }

    @Test
    void readsStandardInput() throws IOException {
        var result = CliRunner.current().run(List.of("--in"), golden("g1.smt2"), Map.of());
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo(golden("g1.default.expected"));
    }

    @Test
    void standardInputSystemIsNamedStdin() throws IOException {
        Path dir = tmp.resolve("d");
        var result = CliRunner.current().run(List.of("--in", "-o", dir.toString()), golden("g1.smt2"), Map.of());
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEmpty();
        assertThat(dir.resolve("stdin.smt2")).hasContent(golden("g1.default.expected"));
    }

    // ------------------------------------------------------------ output

    @Test
    void outputDirIsCreatedWithParentsAndStdoutStaysEmpty() throws IOException {
        Path dir = tmp.resolve("a/b/c");
        var result = run(G1, "--output-dir", dir.toString());
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).isEmpty();
        assertThat(Files.readString(dir.resolve("g1.smt2"), StandardCharsets.UTF_8)).isEqualTo(golden("g1.default.expected"));
    }

    @Test
    void outputDirAndOutWriteTheSameText() throws IOException {
        Path dir = tmp.resolve("o");
        var result = run(G1, "-o", dir.toString(), "--out");
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo(golden("g1.default.expected"));
        assertThat(Files.readString(dir.resolve("g1.smt2"), StandardCharsets.UTF_8)).isEqualTo(result.stdout());
    }

    @Test
    void outWithoutOutputDirWritesStdout() throws IOException {
        assertThat(run(G1, "--out").stdout()).isEqualTo(golden("g1.default.expected"));
    }

    @Test
    void systemNameDropsOnlyTheLastExtension() throws IOException {
        Path input = Files.writeString(tmp.resolve("bar.chc.smt2"), golden("g3.smt2"));
        Path noExt = Files.writeString(tmp.resolve("noext"), golden("g3.smt2"));
        Path dir = tmp.resolve("names");
        assertThat(run(input.toString(), "-o", dir.toString()).status()).isZero();
        assertThat(run(noExt.toString(), "-o", dir.toString()).status()).isZero();
        assertThat(dir.resolve("bar.chc.smt2")).exists();
        assertThat(dir.resolve("noext.smt2")).exists();
    }

    @Test
    void unwritableOutputDirIsAnError() throws IOException {
        Path file = Files.writeString(tmp.resolve("file"), "x");
        var result = run(G1, "-o", file.resolve("sub").toString(), "--out");
        assertThat(result.status()).isEqualTo(1);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).startsWith("error: ").endsWith("\n");
        assertThat(result.stderr().lines()).hasSize(1);
    }

    // ------------------------------------------------------------ transformation list

    @Test
    void namesAreTrimmedAndEmptyEntriesDropped() throws IOException {
        assertThat(run(G1, "--opt", " norm , ").stdout()).isEqualTo(golden("g1.default.expected"));
    }

    @Test
    void repeatedOptValuesAreJoined() throws IOException {
        assertThat(run(G1, "--opt", "norm", "--opt", "norm").stdout()).isEqualTo(golden("g1.default.expected"));
        assertThat(run(G1, "--opt", "", "--opt", "norm").stdout()).isEqualTo(golden("g1.default.expected"));
    }

    @Test
    void onlyEmptyValuesMeanNoTransformation() throws IOException {
        assertThat(run(G1, "--opt", "").stdout()).isEqualTo(golden("g1.none.expected"));
        assertThat(run(G1, "--opt", " , ,", "--opt", "").stdout()).isEqualTo(golden("g1.none.expected"));
    }

    // ------------------------------------------------------------ help and version

    @Test
    void version() {
        var result = run("--version");
        assertThat(result.status()).isZero();
        assertThat(result.stdout()).isEqualTo("horn-assistant 0.1.0\n");
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void help() {
        var result = run("--help");
        assertThat(result.status()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout())
                .startsWith("Usage: horn-assistant [INPUT] [--in] [-o DIR | --output-dir DIR] [--out] [--opt NAMES]... [--version] [--help]\n")
                .contains("--output-dir", "--opt", "--version", "Transformations:", "norm", "Default: norm");
    }

    @Test
    void helpWinsOverOtherArguments() {
        assertThat(run(G1, "--in", "--help").status()).isZero();
        assertThat(run("--frob", "--help").status()).isZero();
    }

    // ------------------------------------------------------------ styling

    @Test
    void plainStreamsCarryNoEscapeSequences() {
        assertThat(run("--help").stdout()).doesNotContain(ESC);
        assertThat(run("--version").stdout()).doesNotContain(ESC);
        assertThat(run(G1, "--opt", "foo").stderr()).doesNotContain(ESC);
        assertThat(run("--nope").stderr()).doesNotContain(ESC);
    }

    @Test
    void noColorDisablesStylingOfExternalBinary() {
        var env = Map.of("NO_COLOR", "1", "TERM", "xterm-256color");
        assertThat(CliRunner.current().run(List.of(G1, "--opt", "foo"), "", env).stderr()).doesNotContain(ESC);
    }

    @Test
    void forcedStylingColorsDiagnosticsWithoutChangingTheirText() {
        assumeFalse(CliRunner.isExternal(), "styling can only be forced in-process");
        var styled = new CliRunner.InProcess(new TerminalCapabilities(true, true));
        var plain = new CliRunner.InProcess(TerminalCapabilities.NONE);

        var styledError = styled.run(List.of(G1, "--opt", "foo"), "", Map.of());
        var plainError = plain.run(List.of(G1, "--opt", "foo"), "", Map.of());
        assertThat(styledError.status()).isEqualTo(plainError.status()).isEqualTo(1);
        assertThat(styledError.stderr()).contains(ESC);
        assertThat(stripAnsi(styledError.stderr())).isEqualTo(plainError.stderr());

        var styledHelp = styled.run(List.of("--help"), "", Map.of());
        assertThat(styledHelp.stdout()).contains(ESC);
        assertThat(stripAnsi(styledHelp.stdout())).isEqualTo(plain.run(List.of("--help"), "", Map.of()).stdout());

        var styledUsage = styled.run(List.of("--nope"), "", Map.of());
        assertThat(styledUsage.status()).isEqualTo(2);
        assertThat(stripAnsi(styledUsage.stderr())).isEqualTo(plain.run(List.of("--nope"), "", Map.of()).stderr());
    }

    @Test
    void smtLibOutputIsNeverStyled() throws IOException {
        assumeFalse(CliRunner.isExternal(), "styling can only be forced in-process");
        var styled = new CliRunner.InProcess(new TerminalCapabilities(true, true));
        Path dir = tmp.resolve("styled");
        var result = styled.run(List.of(G1, "-o", dir.toString(), "--out"), "", Map.of());
        assertThat(result.stdout()).isEqualTo(golden("g1.default.expected"));
        assertThat(dir.resolve("g1.smt2")).hasContent(golden("g1.default.expected"));
    }

    static String stripAnsi(String text) {
        return text.replaceAll("\u001b\\[[0-9;]*m", "");
    }
}
