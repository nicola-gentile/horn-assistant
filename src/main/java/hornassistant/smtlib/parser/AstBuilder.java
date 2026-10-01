package hornassistant.smtlib.parser;

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
import java.util.function.Function;

/**
 * Converts the ANTLR parse tree into the syntax AST. This is the only class that reads parse-tree
 * nodes; each labelled alternative has its own visit method.
 */
final class AstBuilder extends SmtLibBaseVisitor<Object> {

    private static final AstBuilder INSTANCE = new AstBuilder();

    private AstBuilder() {}

    static Script script(SmtLibParser.ScriptContext ctx) {
        return new Script(map(ctx.command(), INSTANCE::command));
    }

    private static <C, T> List<T> map(List<C> contexts, Function<C, T> f) {
        return contexts.stream().map(f).toList();
    }

    // ------------------------------------------------------------ typed entry points

    private Command command(SmtLibParser.CommandContext ctx) {
        return (Command) visit(ctx);
    }

    private TermExpr term(SmtLibParser.TermContext ctx) {
        return (TermExpr) visit(ctx);
    }

    private SortExpr sort(SmtLibParser.SortContext ctx) {
        return (SortExpr) visit(ctx);
    }

    private Identifier identifier(SmtLibParser.IdentifierContext ctx) {
        return (Identifier) visit(ctx);
    }

    private TermExpr.QualId qualId(SmtLibParser.QualIdentifierContext ctx) {
        return (TermExpr.QualId) visit(ctx);
    }

    private SExpr sExpr(SmtLibParser.SExprContext ctx) {
        return (SExpr) visit(ctx);
    }

    // ------------------------------------------------------------ commands

    @Override
    public Command visitSetLogic(SmtLibParser.SetLogicContext ctx) {
        return new Command.SetLogic(symbol(ctx.symbol()));
    }

    @Override
    public Command visitDeclareFun(SmtLibParser.DeclareFunContext ctx) {
        List<SortExpr> sorts = map(ctx.sort(), this::sort);
        return new Command.DeclareFun(
                symbol(ctx.symbol()), sorts.subList(0, sorts.size() - 1), sorts.getLast());
    }

    @Override
    public Command visitDeclareConst(SmtLibParser.DeclareConstContext ctx) {
        return new Command.DeclareConst(symbol(ctx.symbol()), sort(ctx.sort()));
    }

    @Override
    public Command visitDefineFun(SmtLibParser.DefineFunContext ctx) {
        return new Command.DefineFun(
                symbol(ctx.symbol()), map(ctx.sortedVar(), this::sortedVar), sort(ctx.sort()), term(ctx.term()));
    }

    @Override
    public Command visitAssertCommand(SmtLibParser.AssertCommandContext ctx) {
        return new Command.Assert(term(ctx.term()));
    }

    @Override
    public Command visitCheckSat(SmtLibParser.CheckSatContext ctx) {
        return new Command.CheckSat();
    }

    @Override
    public Command visitUnsupportedCommand(SmtLibParser.UnsupportedCommandContext ctx) {
        return new Command.Unsupported(ctx.unsupportedCommandName().getText(), map(ctx.sExpr(), this::sExpr));
    }

    @Override
    public Command visitOtherCommand(SmtLibParser.OtherCommandContext ctx) {
        return new Command.Other(ctx.otherCommandName().getText(), map(ctx.sExpr(), this::sExpr));
    }

    // ------------------------------------------------------------ symbols, sorts, identifiers

    private static String symbol(SmtLibParser.SymbolContext ctx) {
        String text = ctx.getText();
        return ctx.QUOTED_SYMBOL() != null ? text.substring(1, text.length() - 1) : text;
    }

    private Index index(SmtLibParser.IndexContext ctx) {
        return ctx.NUMERAL() != null
                ? new Index.Num(new BigInteger(ctx.NUMERAL().getText()))
                : new Index.Sym(symbol(ctx.symbol()));
    }

    @Override
    public Identifier visitSimpleIdentifier(SmtLibParser.SimpleIdentifierContext ctx) {
        return Identifier.of(symbol(ctx.symbol()));
    }

    @Override
    public Identifier visitIndexedIdentifier(SmtLibParser.IndexedIdentifierContext ctx) {
        return new Identifier(symbol(ctx.symbol()), map(ctx.index(), this::index));
    }

    @Override
    public SortExpr visitSimpleSort(SmtLibParser.SimpleSortContext ctx) {
        return new SortExpr(identifier(ctx.identifier()), List.of());
    }

    @Override
    public SortExpr visitParametricSort(SmtLibParser.ParametricSortContext ctx) {
        return new SortExpr(identifier(ctx.identifier()), map(ctx.sort(), this::sort));
    }

    @Override
    public TermExpr.QualId visitPlainQualId(SmtLibParser.PlainQualIdContext ctx) {
        return new TermExpr.QualId(identifier(ctx.identifier()), Optional.empty());
    }

    @Override
    public TermExpr.QualId visitAsQualId(SmtLibParser.AsQualIdContext ctx) {
        return new TermExpr.QualId(identifier(ctx.identifier()), Optional.of(sort(ctx.sort())));
    }

    private SortedVar sortedVar(SmtLibParser.SortedVarContext ctx) {
        return new SortedVar(symbol(ctx.symbol()), sort(ctx.sort()));
    }

    private Binding binding(SmtLibParser.VarBindingContext ctx) {
        return new Binding(symbol(ctx.symbol()), term(ctx.term()));
    }

    // ------------------------------------------------------------ terms

    private static TermExpr.Constant constant(SmtLibParser.SpecConstantContext ctx) {
        String text = ctx.getText();
        if (ctx.NUMERAL() != null) {
            return new TermExpr.Numeral(new BigInteger(text));
        } else if (ctx.DECIMAL() != null) {
            return new TermExpr.Decimal(new BigDecimal(text));
        } else if (ctx.HEXADECIMAL() != null) {
            return new TermExpr.Hex(text.substring(2));
        } else if (ctx.BINARY() != null) {
            return new TermExpr.Bin(text.substring(2));
        } else {
            return new TermExpr.Str(text.substring(1, text.length() - 1).replace("\"\"", "\""));
        }
    }

    @Override
    public TermExpr visitConstantTerm(SmtLibParser.ConstantTermContext ctx) {
        return constant(ctx.specConstant());
    }

    @Override
    public TermExpr visitQualIdTerm(SmtLibParser.QualIdTermContext ctx) {
        return qualId(ctx.qualIdentifier());
    }

    @Override
    public TermExpr visitApplicationTerm(SmtLibParser.ApplicationTermContext ctx) {
        return new TermExpr.App(qualId(ctx.qualIdentifier()), map(ctx.term(), this::term));
    }

    @Override
    public TermExpr visitLetTerm(SmtLibParser.LetTermContext ctx) {
        return new TermExpr.Let(map(ctx.varBinding(), this::binding), term(ctx.term()));
    }

    @Override
    public TermExpr visitForallTerm(SmtLibParser.ForallTermContext ctx) {
        return new TermExpr.Quantified(Quantifier.FORALL, map(ctx.sortedVar(), this::sortedVar), term(ctx.term()));
    }

    @Override
    public TermExpr visitExistsTerm(SmtLibParser.ExistsTermContext ctx) {
        return new TermExpr.Quantified(Quantifier.EXISTS, map(ctx.sortedVar(), this::sortedVar), term(ctx.term()));
    }

    @Override
    public TermExpr visitMatchTerm(SmtLibParser.MatchTermContext ctx) {
        return new TermExpr.Match(term(ctx.term()));
    }

    @Override
    public TermExpr visitAnnotatedTerm(SmtLibParser.AnnotatedTermContext ctx) {
        return new TermExpr.Annotated(term(ctx.term()), map(ctx.attribute(), this::attribute));
    }

    // ------------------------------------------------------------ attributes and s-expressions

    private Attribute attribute(SmtLibParser.AttributeContext ctx) {
        return new Attribute(ctx.KEYWORD().getText().substring(1), Optional.ofNullable(ctx.attributeValue()).map(this::attributeValue));
    }

    private SExpr attributeValue(SmtLibParser.AttributeValueContext ctx) {
        if (ctx.specConstant() != null) {
            return new SExpr.Literal(constant(ctx.specConstant()));
        } else if (ctx.symbol() != null) {
            return new SExpr.Symbol(symbol(ctx.symbol()));
        } else {
            return new SExpr.ListExpr(map(ctx.sExpr(), this::sExpr));
        }
    }

    @Override
    public SExpr visitConstantSExpr(SmtLibParser.ConstantSExprContext ctx) {
        return new SExpr.Literal(constant(ctx.specConstant()));
    }

    @Override
    public SExpr visitSymbolSExpr(SmtLibParser.SymbolSExprContext ctx) {
        return new SExpr.Symbol(symbol(ctx.symbol()));
    }

    @Override
    public SExpr visitReservedSExpr(SmtLibParser.ReservedSExprContext ctx) {
        return new SExpr.Reserved(ctx.getText());
    }

    @Override
    public SExpr visitKeywordSExpr(SmtLibParser.KeywordSExprContext ctx) {
        return new SExpr.Keyword(ctx.getText().substring(1));
    }

    @Override
    public SExpr visitListSExpr(SmtLibParser.ListSExprContext ctx) {
        return new SExpr.ListExpr(map(ctx.sExpr(), this::sExpr));
    }
}
