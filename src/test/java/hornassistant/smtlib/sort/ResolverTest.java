package hornassistant.smtlib.sort;

import static hornassistant.smtlib.sort.Sort.BOOL;
import static hornassistant.smtlib.sort.Sort.INT;
import static hornassistant.smtlib.sort.Sort.REAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hornassistant.chc.ChcInputException;
import hornassistant.chc.Predicate;
import hornassistant.smtlib.parser.SmtLibParserFacade;
import hornassistant.smtlib.print.Printer;
import hornassistant.smtlib.term.Term;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResolverTest {

    private static final String DECLS = """
            (declare-fun P (Int) Bool)
            (declare-fun Q (Int Int) Bool)
            (declare-fun R (Real) Bool)
            (declare-fun B () Bool)
            (declare-const n Int)
            (declare-const r Real)
            """;

    private static Resolver.Resolved resolve(String text) {
        return Resolver.resolve(SmtLibParserFacade.parse(text));
    }

    /** Resolves one assertion in the context of {@link #DECLS} and prints it. */
    private static String resolved(String assertion) {
        List<Term> assertions = resolve(DECLS + "(assert " + assertion + ")").assertions();
        assertThat(assertions).hasSize(1);
        return Printer.term(assertions.getFirst());
    }

    private static void rejected(String text, String messageStart) {
        assertThatThrownBy(() -> resolve(text))
                .isInstanceOf(ChcInputException.class)
                .hasMessageStartingWith(messageStart);
    }

    @Test
    void collectsPredicatesInDeclarationOrder() {
        var resolved = resolve("(declare-fun Z (Int (Array Int Bool)) Bool)(declare-fun A () Bool)(declare-fun M (Real) Bool)");
        assertThat(resolved.predicates()).containsExactly(
                new Predicate("Z", List.of(INT, new Sort.Array(INT, BOOL))),
                new Predicate("A", List.of()),
                new Predicate("M", List.of(REAL)));
    }

    @Test
    void keepsAssertionsInOrder() {
        assertThat(resolve(DECLS + "(assert B)(check-sat)(assert (P 1))").assertions())
                .extracting(Printer::term)
                .containsExactly("B", "(P 1)");
    }

    @Test
    void resolvesQuantifiedClauses() {
        assertThat(resolved("(forall ((x Int) (y Int)) (=> (and (Q x y) (> x 0)) (P y)))"))
                .isEqualTo("(forall ((x Int) (y Int)) (=> (and (Q x y) (> x 0)) (P y)))");
    }

    @Test
    void declaredConstantsAreVariables() {
        var assertion = resolve(DECLS + "(assert (> n 0))").assertions().getFirst();
        assertThat(assertion).isEqualTo(Term.app(hornassistant.smtlib.term.Op.GT, new Term.Var("n", INT), Term.intLit(0)));
    }

    @Test
    void booleanPredicateAsBareSymbol() {
        assertThat(resolve(DECLS + "(assert B)").assertions().getFirst())
                .isEqualTo(new Term.PredApp(new Predicate("B", List.of()), List.of()));
    }

    @Test
    void localsShadowGlobals() {
        assertThat(resolved("(forall ((P Int) (n Bool)) (=> n (> P 0)))")).isEqualTo("(forall ((P Int) (n Bool)) (=> n (> P 0)))");
    }

    @Test
    void inlinesLet() {
        assertThat(resolved("(forall ((x Int)) (let ((y (+ x 1))) (P y)))")).isEqualTo("(forall ((x Int)) (P (+ x 1)))");
    }

    @Test
    void letBindsInParallel() {
        assertThat(resolved("(forall ((x Int)) (let ((x 1) (y x)) (Q x y)))")).isEqualTo("(forall ((x Int)) (Q 1 x))");
    }

    @Test
    void nestedLetsSeeOuterBindings() {
        assertThat(resolved("(let ((a 1)) (let ((b (+ a 1))) (Q a b)))")).isEqualTo("(Q 1 (+ 1 1))");
    }

    @Test
    void letDoesNotCaptureUnderAQuantifier() {
        assertThat(resolved("(forall ((x Int)) (let ((y x)) (exists ((x Int)) (< x y))))"))
                .isEqualTo("(forall ((x Int)) (exists ((x_0 Int)) (< x_0 x)))");
    }

    @Test
    void inlinesDefineFun() {
        assertThat(resolved0("(define-fun lt ((a Int) (b Int)) Bool (< a b))", "(forall ((x Int)) (lt x 1))"))
                .isEqualTo("(forall ((x Int)) (< x 1))");
    }

    @Test
    void inlinesNullaryDefineFunUsedAsSymbol() {
        assertThat(resolved0("(define-fun ten () Int 10)", "(P ten)")).isEqualTo("(P 10)");
    }

    @Test
    void defineFunMayUseEarlierDefinitionsAndPredicates() {
        assertThat(resolved0("(define-fun inc ((a Int)) Int (+ a 1))(define-fun pinc ((a Int)) Bool (P (inc a)))", "(forall ((x Int)) (pinc x))"))
                .isEqualTo("(forall ((x Int)) (P (+ x 1)))");
    }

    @Test
    void defineFunBodyBindersDoNotCaptureArguments() {
        assertThat(resolved0("(define-fun g ((a Int)) Bool (exists ((x Int)) (< a x)))", "(forall ((x Int)) (g x))"))
                .isEqualTo("(forall ((x Int)) (exists ((x_0 Int)) (< x x_0)))");
    }

    @Test
    void clauseBinderDoesNotCaptureConstantOfADefinition() {
        assertThat(resolved0("(define-fun pos () Bool (> n 0))", "(forall ((n Int)) (=> pos (P n)))"))
                .isEqualTo("(forall ((n_0 Int)) (=> (> n 0) (P n_0)))");
    }

    private static String resolved0(String definitions, String assertion) {
        return Printer.term(resolve(DECLS + definitions + "(assert " + assertion + ")").assertions().getFirst());
    }

    @Test
    void dropsAnnotations() {
        assertThat(resolved("(! (=> (! (P n) :named a) B) :weight 1)")).isEqualTo("(=> (P n) B)");
    }

    @Test
    void desugarsNaryImplication() {
        assertThat(resolved("(=> (P n) (> n 0) B)")).isEqualTo("(=> (and (P n) (> n 0)) B)");
        assertThat(resolved("(=> (P n) B)")).isEqualTo("(=> (P n) B)");
    }

    @Test
    void promotesIntegerNumeralsWhereARealIsNeeded() {
        assertThat(resolved("(> (+ r 1) (- 2))")).isEqualTo("(> (+ r 1.0) (- 2.0))");
        assertThat(resolved("(= r (/ 1 2))")).isEqualTo("(= r (/ 1.0 2.0))");
        assertThat(resolved("(R 0)")).isEqualTo("(R 0.0)");
        assertThat(resolved("(= r (ite B 1 r))")).isEqualTo("(= r (ite B 1.0 r))");
        assertThat(resolved("(> 1.5 n)".replace("n", "(to_real n)"))).isEqualTo("(> 1.5 (to_real n))");
    }

    @Test
    void doesNotCoerceIntegerVariables() {
        rejected(DECLS + "(assert (> r n))", "not well sorted: (> r n): ");
    }

    @Test
    void resolvesArrays() {
        assertThat(resolved0("(declare-const a (Array Int Real))", "(= a (store ((as const (Array Int Real)) 0) 1 2))"))
                .isEqualTo("(= a (store ((as const (Array Int Real)) 0.0) 1 2.0))");
        assertThat(resolved0("(declare-const a (Array Int Int))", "(> (select a n) 0)")).isEqualTo("(> (select a n) 0)");
    }

    @Test
    void resolvesAsAnnotationOnAnOrdinarySymbol() {
        assertThat(resolved("(> (as n Int) 0)")).isEqualTo("(> n 0)");
        rejected(DECLS + "(assert (> (as n Real) 0.0))", "not well sorted: (as n Real): ");
    }

    @Test
    void rejectsNonPredicateDeclarations() {
        rejected("(declare-fun f (Int) Int)", "f is declared with result sort Int; only predicates (result sort Bool) can be declared");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "(assert (+ 1 2))                   | not well sorted: (+ 1 2): asserted term has sort Int, expected Bool",
        "(assert (> x 0))                   | undeclared symbol: x",
        "(assert (P n n))                   | not well sorted: (P n n): ",
        "(assert (P r))                     | not well sorted: (P r): ",
        "(assert (and n))                   | not well sorted: (and n): ",
        "(assert (n 1))                     | not well sorted: (n 1): n is not a function",
        "(assert (forall ((x Int)) (x 1)))  | not well sorted: (x 1): x is not a function",
        "(assert P)                         | not well sorted: P: ",
        "(assert (true 1))                  | not well sorted: (true 1): ",
        "(assert (=> B))                    | not well sorted: (=> B): ",
        "(assert (frob 1))                  | undeclared symbol: frob",
        "(assert ((_ extract 1 0) n))       | unsupported identifier: (_ extract 1 0)",
        "(assert (= #x0F #x0F))             | unsupported literal: #x0F",
        "(assert (= \"a\" \"a\"))           | unsupported literal: \"a\"",
        "(assert (match n ((x true))))      | unsupported term: match",
        "(declare-const s Foo)              | unknown sort: Foo",
        "(declare-const s (Array Int))      | unknown sort: (Array Int)",
        "(declare-fun P (Int) Bool)         | P is already declared",
        "(declare-const and Bool)           | and is already declared",
        "(define-fun f () Int true)         | not well sorted: true: f is declared with sort Int, but its body has sort Bool",
        "(assert (forall ((x Int) (x Int)) true)) | variable x is bound twice",
        "(assert (let ((a 1) (a 2)) true))  | variable a is bound twice",
        "(declare-datatypes ((L 0)) (((nil)))) | unsupported command: declare-datatypes",
        "(define-sort S () Int)             | unsupported command: define-sort",
        "(define-fun-rec f () Int 1)        | unsupported command: define-fun-rec",
    })
    void rejectsIllFormedInput(String command, String messageStart) {
        rejected(DECLS + command, messageStart.strip());
    }
}
