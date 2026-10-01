package hornassistant.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Inputs that are not CHC systems: status 1, one {@code error: } line on stderr, nothing on stdout. */
class ErrorCasesTest {

    private static final String DECLS = "(set-logic HORN)(declare-fun P (Int) Bool)(declare-fun Q (Int) Bool)(declare-fun R (Int) Bool)"
            + "(declare-fun W (Bool) Bool)";

    @TempDir
    Path tmp;

    private CliRunner.Result runOn(String input, String... options) throws IOException {
        Path file = Files.writeString(tmp.resolve("in.smt2"), input, StandardCharsets.UTF_8);
        var args = new java.util.ArrayList<String>();
        args.add(file.toString());
        args.addAll(List.of(options));
        return CliRunner.current().run(args, "", Map.of());
    }

    private static void assertInputError(CliRunner.Result result, String messageStart) {
        assertThat(result.status()).isEqualTo(1);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).startsWith("error: " + messageStart).endsWith("\n");
        assertThat(result.stderr().lines()).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "unbalanced parentheses       | (assert (forall ((x Int)) (P x))                         | syntax error at ",
        "unknown command              | (frobnicate)                                              | syntax error at ",
        "not Bool                     | (assert (+ 1 2))                                          | not well sorted: (+ 1 2): ",
        "undeclared symbol            | (assert (forall ((x Int)) (S x)))                         | undeclared symbol: S",
        "no assert                    | (check-sat)                                               | no assert command",
        "non-Bool declare-fun         | (declare-fun f (Int) Int)(assert true)                    | f is declared with result sort Int",
        "predicate under or           | (assert (forall ((x Int)) (=> (or (P x) (Q x)) (R x))))   | predicate P occurs outside a body leaf or the head",
        "predicate under inner not    | (assert (forall ((x Int)) (=> (and (not (P x))) (R x))))  | predicate P occurs outside a body leaf or the head",
        "predicate under quantifier   | (assert (forall ((x Int)) (=> (exists ((y Int)) (P y)) (R x)))) | predicate P occurs outside a body leaf or the head",
        "predicate in argument        | (assert (forall ((x Int)) (=> (W (P x)) (R x))))          | predicate P occurs outside a body leaf or the head",
        "two predicates in head       | (assert (forall ((x Int)) (=> (P x) (and (Q x) (R x)))))  | more than one predicate in the head",
        "unsupported datatypes        | (declare-datatypes ((L 0)) (((nil))))(assert true)        | unsupported command: declare-datatypes",
        "error in a later assertion   | (assert (forall ((x Int)) (=> (P x) (R x)))) (assert Z)   | undeclared symbol: Z",
    })
    void rejectsInput(String description, String body, String messageStart) throws IOException {
        assertInputError(runOn(DECLS + body.strip()), messageStart.strip());
    }

    @Test
    void unknownTransformation() throws IOException {
        assertInputError(runOn(DECLS + "(assert (P 0))", "--opt", "foo"), "unknown transformation: foo");
    }

    @Test
    void unknownTransformationAfterAValidOneProducesNoOutput() throws IOException {
        assertInputError(runOn(DECLS + "(assert (P 0))", "--opt", "norm,foo"), "unknown transformation: foo");
    }

    @Test
    void transformationNamesAreCheckedBeforeTheInputIsParsed() throws IOException {
        assertInputError(runOn("(this is not smt-lib", "--opt", "foo"), "unknown transformation: foo");
    }

    @Test
    void invalidUtf8() throws IOException {
        Path file = Files.write(tmp.resolve("bad.smt2"), new byte[] {'(', 'a', (byte) 0xFF, ')'});
        assertInputError(CliRunner.current().run(file.toString()), "input is not valid UTF-8");
    }

    @Test
    void writesNoFileOnError() throws IOException {
        Path out = tmp.resolve("out");
        assertInputError(runOn(DECLS, "-o", out.toString()), "no assert command");
        assertThat(out).doesNotExist();
    }
}
