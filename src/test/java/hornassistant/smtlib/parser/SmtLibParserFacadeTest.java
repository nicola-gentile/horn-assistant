package hornassistant.smtlib.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hornassistant.chc.ChcInputException;
import hornassistant.smtlib.print.SyntaxPrinter;
import hornassistant.smtlib.syntax.Attribute;
import hornassistant.smtlib.syntax.Binding;
import hornassistant.smtlib.syntax.Command;
import hornassistant.smtlib.syntax.Identifier;
import hornassistant.smtlib.syntax.Index;
import hornassistant.smtlib.syntax.Quantifier;
import hornassistant.smtlib.syntax.SExpr;
import hornassistant.smtlib.syntax.Script;
import hornassistant.smtlib.syntax.SortExpr;
import hornassistant.smtlib.syntax.SortedVar;
import hornassistant.smtlib.syntax.TermExpr;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class SmtLibParserFacadeTest {

    private static final SortExpr INT = SortExpr.of("Int");
    private static final SortExpr BOOL = SortExpr.of("Bool");

    private static Command single(String text) {
        Script script = SmtLibParserFacade.parse(text);
        assertThat(script.commands()).hasSize(1);
        return script.commands().getFirst();
    }

    private static TermExpr assertedTerm(String term) {
        return ((Command.Assert) single("(assert " + term + ")")).term();
    }

    private static TermExpr.QualId sym(String name) {
        return TermExpr.QualId.of(name);
    }

    private static TermExpr.App app(String f, TermExpr... args) {
        return new TermExpr.App(sym(f), List.of(args));
    }

    private static TermExpr.Numeral num(long n) {
        return new TermExpr.Numeral(BigInteger.valueOf(n));
    }

    @Test
    void parsesSetLogic() {
        assertThat(single("(set-logic HORN)")).isEqualTo(new Command.SetLogic("HORN"));
    }

    @Test
    void parsesDeclareFun() {
        assertThat(single("(declare-fun inv (Int (Array Int Bool)) Bool)"))
                .isEqualTo(new Command.DeclareFun(
                        "inv",
                        List.of(INT, new SortExpr(Identifier.of("Array"), List.of(INT, BOOL))),
                        BOOL));
    }

    @Test
    void parsesIndexedSort() {
        var bv = new SortExpr(new Identifier("BitVec", List.of(new Index.Num(BigInteger.valueOf(32)))), List.of());
        assertThat(single("(declare-fun P ((_ BitVec 32)) Bool)"))
                .isEqualTo(new Command.DeclareFun("P", List.of(bv), BOOL));
    }

    @Test
    void parsesDeclareConst() {
        assertThat(single("(declare-const n Int)")).isEqualTo(new Command.DeclareConst("n", INT));
    }

    @Test
    void parsesDefineFun() {
        assertThat(single("(define-fun f ((x Int) (y Int)) Bool (< x y))"))
                .isEqualTo(new Command.DefineFun(
                        "f",
                        List.of(new SortedVar("x", INT), new SortedVar("y", INT)),
                        BOOL,
                        app("<", sym("x"), sym("y"))));
    }

    @Test
    void parsesCheckSat() {
        assertThat(single("(check-sat)")).isEqualTo(new Command.CheckSat());
    }

    @Test
    void parsesCommandsWithoutEffectGenerically() {
        assertThat(single("(set-info :status sat)"))
                .isEqualTo(new Command.Other("set-info", List.of(new SExpr.Keyword("status"), new SExpr.Symbol("sat"))));
        assertThat(single("(push 1)"))
                .isEqualTo(new Command.Other("push", List.of(new SExpr.Literal(num(1)))));
        assertThat(single("(exit)")).isEqualTo(new Command.Other("exit", List.of()));
        assertThat(single("(echo \"a\"\"b\")"))
                .isEqualTo(new Command.Other("echo", List.of(new SExpr.Literal(new TermExpr.Str("a\"b")))));
    }

    @Test
    void parsesUnsupportedCommandsGenerically() {
        assertThat(single("(declare-datatypes ((L 0)) (((nil) (cons (hd Int) (tl L)))))"))
                .isInstanceOf(Command.Unsupported.class)
                .extracting(c -> ((Command.Unsupported) c).commandName())
                .isEqualTo("declare-datatypes");
        assertThat(single("(declare-sort U 0)"))
                .isEqualTo(new Command.Unsupported("declare-sort", List.of(new SExpr.Symbol("U"), new SExpr.Literal(num(0)))));
    }

    @Test
    void parsesConstants() {
        assertThat(assertedTerm("42")).isEqualTo(num(42));
        assertThat(assertedTerm("0")).isEqualTo(num(0));
        assertThat(assertedTerm("2.50")).isEqualTo(new TermExpr.Decimal(new BigDecimal("2.50")));
        assertThat(assertedTerm("#x1F")).isEqualTo(new TermExpr.Hex("1F"));
        assertThat(assertedTerm("#b0101")).isEqualTo(new TermExpr.Bin("0101"));
        assertThat(assertedTerm("\"say \"\"hi\"\"\"")).isEqualTo(new TermExpr.Str("say \"hi\""));
    }

    @Test
    void quotedAndSimpleSymbolsAreTheSame() {
        assertThat(assertedTerm("|x|")).isEqualTo(sym("x"));
        assertThat(assertedTerm("|a b|")).isEqualTo(sym("a b"));
        assertThat(assertedTerm("<=+.?/x")).isEqualTo(sym("<=+.?/x"));
    }

    @Test
    void parsesQualifiedIdentifiers() {
        var indexed = new TermExpr.QualId(
                new Identifier("extract", List.of(new Index.Num(BigInteger.valueOf(7)), new Index.Sym("lo"))),
                Optional.empty());
        assertThat(assertedTerm("((_ extract 7 lo) x)")).isEqualTo(new TermExpr.App(indexed, List.of(sym("x"))));

        var array = new SortExpr(Identifier.of("Array"), List.of(INT, INT));
        var asConst = new TermExpr.QualId(Identifier.of("const"), Optional.of(array));
        assertThat(assertedTerm("((as const (Array Int Int)) 0)"))
                .isEqualTo(new TermExpr.App(asConst, List.of(num(0))));
        assertThat(assertedTerm("(as nil Int)"))
                .isEqualTo(new TermExpr.QualId(Identifier.of("nil"), Optional.of(INT)));
    }

    @Test
    void parsesLet() {
        assertThat(assertedTerm("(let ((a 1) (b x)) (= a b))"))
                .isEqualTo(new TermExpr.Let(
                        List.of(new Binding("a", num(1)), new Binding("b", sym("x"))),
                        app("=", sym("a"), sym("b"))));
    }

    @Test
    void parsesQuantifiers() {
        assertThat(assertedTerm("(forall ((x Int) (|y z| Bool)) (P x))"))
                .isEqualTo(new TermExpr.Quantified(
                        Quantifier.FORALL,
                        List.of(new SortedVar("x", INT), new SortedVar("y z", BOOL)),
                        app("P", sym("x"))));
        assertThat(assertedTerm("(exists ((x Int)) true)"))
                .isEqualTo(new TermExpr.Quantified(Quantifier.EXISTS, List.of(new SortedVar("x", INT)), sym("true")));
    }

    @Test
    void parsesAnnotations() {
        assertThat(assertedTerm("(! (P x) :named a1 :weight 2 :pattern ((P x)) :flag)"))
                .isEqualTo(new TermExpr.Annotated(
                        app("P", sym("x")),
                        List.of(
                                new Attribute("named", Optional.of(new SExpr.Symbol("a1"))),
                                new Attribute("weight", Optional.of(new SExpr.Literal(num(2)))),
                                new Attribute("pattern", Optional.of(new SExpr.ListExpr(List.of(
                                        new SExpr.ListExpr(List.of(new SExpr.Symbol("P"), new SExpr.Symbol("x"))))))),
                                new Attribute("flag", Optional.empty()))));
    }

    @Test
    void parsesMatch() {
        assertThat(assertedTerm("(match l ((nil 0) ((cons h t) h)))")).isEqualTo(new TermExpr.Match(sym("l")));
    }

    @Test
    void commandNamesMayBeUsedAsSymbolsInTerms() {
        assertThat(assertedTerm("(P reset)")).isEqualTo(app("P", sym("reset")));
    }

    @Test
    void skipsCommentsAndWhitespace() {
        Script script = SmtLibParserFacade.parse("; header\n(check-sat) ; trailing\n\t(exit)\r\n");
        assertThat(script.commands()).containsExactly(new Command.CheckSat(), new Command.Other("exit", List.of()));
    }

    @Test
    void emptyInputIsAnEmptyScript() {
        assertThat(SmtLibParserFacade.parse("  ; nothing\n").commands()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "(assert (P x)",           // unbalanced
        "(assert (P x)))",         // extra closing parenthesis
        "(frobnicate x)",          // unknown command
        "(assert #xZZ)",           // bad literal
        "(assert |a|b|)",          // bad quoted symbol
        "(check-sat 1)",           // wrong arity
        "(declare-fun 1x () Bool)" // symbols do not start with a digit
    })
    void reportsSyntaxErrorsWithPosition(String text) {
        assertThatThrownBy(() -> SmtLibParserFacade.parse(text))
                .isInstanceOf(ChcInputException.class)
                .hasMessageMatching("syntax error at \\d+:\\d+: .+")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\n"));
    }

    @Test
    void reportsLineAndOneBasedColumn() {
        assertThatThrownBy(() -> SmtLibParserFacade.parse("(check-sat)\n  (frob)"))
                .hasMessageStartingWith("syntax error at 2:4: ");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "(set-logic HORN)",
        "(declare-fun inv (Int (Array Int Bool) (_ BitVec 8)) Bool)",
        "(declare-const |a b| Real)",
        "(define-fun f ((x Int)) Bool (and (< x 1) (! (> x 0) :named n)))",
        "(assert (forall ((x Int)) (=> (let ((y (+ x 1))) (inv y)) (exists ((z Int)) (= z x)))))",
        "(assert ((_ extract 7 0) #xFF #b01 \"s\"\"t\" 1.5 0))",
        "(assert ((as const (Array Int Int)) 0))",
        "(assert (match l ((nil 0))))",
        "(set-info :source |multi\nline|)",
        "(set-option :produce-models true)",
        "(declare-datatypes ((L 0)) (((nil) (cons (hd Int) (tl L)))))",
        "(declare-datatypes (par (T) ((L 0))) ())",
        "(check-sat)",
        "(get-model)",
        "(exit)",
    })
    void printedSyntaxParsesBackToTheSameTree(String text) {
        Script parsed = SmtLibParserFacade.parse(text);
        Script reparsed = SmtLibParserFacade.parse(SyntaxPrinter.print(parsed));
        assertThat(reparsed).isEqualTo(parsed);
    }
}
