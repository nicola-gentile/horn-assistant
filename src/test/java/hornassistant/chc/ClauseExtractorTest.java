package hornassistant.chc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hornassistant.smtlib.parser.SmtLibParserFacade;
import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.sort.Resolver;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClauseExtractorTest {

    private static final String DECLS = """
            (declare-fun P (Int) Bool)
            (declare-fun Q (Int Int) Bool)
            (declare-fun W (Bool) Bool)
            (declare-fun B () Bool)
            (declare-const x Int)
            (declare-const y Int)
            """;

    private static Clause extract(String assertion) {
        var resolved = Resolver.resolve(SmtLibParserFacade.parse(DECLS + "(assert " + assertion + ")"));
        return ClauseExtractor.extract(resolved.assertions().getFirst());
    }

    /** {@code body | constraint | head}. */
    private static String show(String assertion) {
        Clause c = extract(assertion);
        String body = c.body().stream().map(Printer::term).collect(Collectors.joining(", ", "[", "]"));
        String head = switch (c.head()) {
            case Head.Pred(var app) -> Printer.term(app);
            case Head.False _ -> "false";
        };
        return body + " | " + Printer.term(c.constraint()) + " | " + head;
    }

    @Test
    void implicationSplitsIntoPremiseAndConclusion() {
        assertThat(show("(forall ((a Int)) (=> (and (P a) (> a 0)) (Q a a)))")).isEqualTo("[(P a)] | (> a 0) | (Q a a)");
    }

    @Test
    void stripsEveryOuterForall() {
        assertThat(show("(forall ((a Int)) (forall ((b Int)) (=> (P a) (P b))))")).isEqualTo("[(P a)] | true | (P b)");
    }

    @Test
    void negationIsAQueryWithItsArgumentAsPremise() {
        assertThat(show("(not (and (P x) (< x 0)))")).isEqualTo("[(P x)] | (< x 0) | false");
    }

    @Test
    void anythingElseIsTheConclusionWithATruePremise() {
        assertThat(show("(P 0)")).isEqualTo("[] | true | (P 0)");
        assertThat(show("B")).isEqualTo("[] | true | B");
        assertThat(show("false")).isEqualTo("[] | true | false");
    }

    @Test
    void predicateLeavesMoveToTheBodyInLeftToRightOrder() {
        assertThat(show("(=> (and (Q x y) (> x 0) (P y) B) false)")).isEqualTo("[(Q x y), (P y), B] | (> x 0) | false");
    }

    @Test
    void premiseThatIsASinglePredicateLeavesTrue() {
        assertThat(show("(=> (P x) B)")).isEqualTo("[(P x)] | true | B");
        assertThat(show("(=> (and (P x) (and B)) B)")).isEqualTo("[(P x), B] | true | B");
    }

    @Test
    void premiseWithoutPredicatesIsTheConstraint() {
        assertThat(show("(=> (> x 0) (P x))")).isEqualTo("[] | (> x 0) | (P x)");
    }

    @Test
    void andNodeLeftWithoutChildrenDisappears() {
        assertThat(show("(=> (and (> x 0) (and (P x) B)) B)")).isEqualTo("[(P x), B] | (> x 0) | B");
    }

    @Test
    void andNodeLeftWithOneChildIsReplacedByIt() {
        assertThat(show("(=> (and (P x) (and (Q x y) (> x 0)) (< y 1)) B)")).isEqualTo("[(P x), (Q x y)] | (and (> x 0) (< y 1)) | B");
    }

    @Test
    void remainingAndStructureIsKept() {
        assertThat(show("(=> (and (> x 0) (and (P x) (< x 5) (< x 6))) B)"))
                .isEqualTo("[(P x)] | (and (> x 0) (and (< x 5) (< x 6))) | B");
    }

    @Test
    void constraintConclusionIsNegatedIntoAQuery() {
        assertThat(show("(=> (P x) (> x 0))")).isEqualTo("[(P x)] | (not (> x 0)) | false");
        assertThat(show("(=> (and (P x) (> x 1)) (> x 0))")).isEqualTo("[(P x)] | (and (> x 1) (not (> x 0))) | false");
        assertThat(show("(=> (and (P x) (> x 1) (< x 9)) (> x 0))"))
                .isEqualTo("[(P x)] | (and (> x 1) (< x 9) (not (> x 0))) | false");
        assertThat(show("(> x 0)")).isEqualTo("[] | (not (> x 0)) | false");
        assertThat(show("(=> (P x) false)")).isEqualTo("[(P x)] | true | false");
    }

    @Test
    void constraintsMayContainQuantifiers() {
        assertThat(show("(=> (and (P x) (exists ((z Int)) (< z x))) B)")).isEqualTo("[(P x)] | (exists ((z Int)) (< z x)) | B");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "(=> (or (P x) (> x 0)) B)                 | predicate P occurs outside a body leaf or the head: (or (P x) (> x 0))",
        "(=> (and (not (P x)) (> x 0)) B)          | predicate P occurs outside a body leaf or the head: (not (P x))",
        "(not (not (P x)))                         | predicate P occurs outside a body leaf or the head: (not (P x))",
        "(=> (exists ((z Int)) (P z)) B)           | predicate P occurs outside a body leaf or the head: (exists ((z Int)) (P z))",
        "(exists ((z Int)) (P z))                  | predicate P occurs outside a body leaf or the head: (exists ((z Int)) (P z))",
        "(=> (W (P x)) B)                          | predicate P occurs outside a body leaf or the head: (W (P x))",
        "(=> B (W (P x)))                          | predicate P occurs outside a body leaf or the head: (W (P x))",
        "(=> B (or (P x) (> x 0)))                 | predicate P occurs outside a body leaf or the head: (or (P x) (> x 0))",
        "(=> (ite B (> x 0) (< x 0)) false)        | predicate B occurs outside a body leaf or the head: (ite B (> x 0) (< x 0))",
        "(=> (P x) (and (Q x x) (P x)))            | more than one predicate in the head: (and (Q x x) (P x))",
        "(and (P x) B)                             | more than one predicate in the head: (and (P x) B)",
    })
    void rejectsPredicatesOutsideBodyLeavesAndHead(String assertion, String message) {
        assertThatThrownBy(() -> extract(assertion)).isInstanceOf(ChcInputException.class).hasMessage(message.strip());
    }
}
