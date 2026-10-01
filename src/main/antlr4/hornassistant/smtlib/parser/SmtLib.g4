// A focused grammar for the SMT-LIB v2.6 subset read by horn-assistant.
grammar SmtLib;

// ---------------------------------------------------------------- parser

script
    : command* EOF
    ;

command
    : LPAR CMD_SET_LOGIC symbol RPAR                                        # setLogic
    | LPAR CMD_DECLARE_FUN symbol LPAR sort* RPAR sort RPAR                 # declareFun
    | LPAR CMD_DECLARE_CONST symbol sort RPAR                               # declareConst
    | LPAR CMD_DEFINE_FUN symbol LPAR sortedVar* RPAR sort term RPAR        # defineFun
    | LPAR CMD_ASSERT term RPAR                                             # assertCommand
    | LPAR CMD_CHECK_SAT RPAR                                               # checkSat
    | LPAR unsupportedCommandName sExpr* RPAR                               # unsupportedCommand
    | LPAR otherCommandName sExpr* RPAR                                     # otherCommand
    ;

unsupportedCommandName
    : CMD_DECLARE_SORT | CMD_DEFINE_SORT | CMD_DECLARE_DATATYPE | CMD_DECLARE_DATATYPES
    | CMD_DEFINE_FUN_REC | CMD_DEFINE_FUNS_REC
    ;

otherCommandName
    : CMD_SET_INFO | CMD_SET_OPTION | CMD_CHECK_SAT_ASSUMING | CMD_GET_MODEL | CMD_GET_INFO
    | CMD_GET_OPTION | CMD_GET_VALUE | CMD_GET_ASSERTIONS | CMD_GET_PROOF | CMD_GET_UNSAT_CORE
    | CMD_PUSH | CMD_POP | CMD_RESET | CMD_RESET_ASSERTIONS | CMD_ECHO | CMD_EXIT
    ;

commandName
    : CMD_SET_LOGIC | CMD_DECLARE_FUN | CMD_DECLARE_CONST | CMD_DEFINE_FUN | CMD_ASSERT
    | CMD_CHECK_SAT | unsupportedCommandName | otherCommandName
    ;

// Command names are reserved only in command position; inside terms they are ordinary symbols.
symbol
    : SIMPLE_SYMBOL
    | QUOTED_SYMBOL
    | commandName
    ;

specConstant
    : NUMERAL
    | DECIMAL
    | HEXADECIMAL
    | BINARY
    | STRING
    ;

index
    : NUMERAL
    | symbol
    ;

identifier
    : symbol                                    # simpleIdentifier
    | LPAR UNDERSCORE symbol index+ RPAR        # indexedIdentifier
    ;

sort
    : identifier                                # simpleSort
    | LPAR identifier sort+ RPAR                # parametricSort
    ;

qualIdentifier
    : identifier                                # plainQualId
    | LPAR AS identifier sort RPAR              # asQualId
    ;

sortedVar
    : LPAR symbol sort RPAR
    ;

varBinding
    : LPAR symbol term RPAR
    ;

pattern
    : symbol
    | LPAR symbol symbol+ RPAR
    ;

matchCase
    : LPAR pattern term RPAR
    ;

term
    : specConstant                                          # constantTerm
    | qualIdentifier                                        # qualIdTerm
    | LPAR qualIdentifier term+ RPAR                        # applicationTerm
    | LPAR LET LPAR varBinding+ RPAR term RPAR              # letTerm
    | LPAR FORALL LPAR sortedVar+ RPAR term RPAR            # forallTerm
    | LPAR EXISTS LPAR sortedVar+ RPAR term RPAR            # existsTerm
    | LPAR MATCH term LPAR matchCase+ RPAR RPAR             # matchTerm
    | LPAR BANG term attribute+ RPAR                        # annotatedTerm
    ;

attribute
    : KEYWORD attributeValue?
    ;

attributeValue
    : specConstant
    | symbol
    | LPAR sExpr* RPAR
    ;

reservedWord
    : UNDERSCORE | BANG | AS | LET | FORALL | EXISTS | MATCH | PAR
    ;

sExpr
    : specConstant                              # constantSExpr
    | symbol                                    # symbolSExpr
    | reservedWord                              # reservedSExpr
    | KEYWORD                                   # keywordSExpr
    | LPAR sExpr* RPAR                          # listSExpr
    ;

// ---------------------------------------------------------------- lexer

LPAR : '(' ;
RPAR : ')' ;

// Reserved words come before SIMPLE_SYMBOL so that they win on equal length.
UNDERSCORE : '_' ;
BANG       : '!' ;
AS         : 'as' ;
LET        : 'let' ;
FORALL     : 'forall' ;
EXISTS     : 'exists' ;
MATCH      : 'match' ;
PAR        : 'par' ;

CMD_SET_LOGIC          : 'set-logic' ;
CMD_SET_INFO           : 'set-info' ;
CMD_SET_OPTION         : 'set-option' ;
CMD_DECLARE_FUN        : 'declare-fun' ;
CMD_DECLARE_CONST      : 'declare-const' ;
CMD_DEFINE_FUN         : 'define-fun' ;
CMD_DEFINE_FUN_REC     : 'define-fun-rec' ;
CMD_DEFINE_FUNS_REC    : 'define-funs-rec' ;
CMD_DECLARE_SORT       : 'declare-sort' ;
CMD_DEFINE_SORT        : 'define-sort' ;
CMD_DECLARE_DATATYPE   : 'declare-datatype' ;
CMD_DECLARE_DATATYPES  : 'declare-datatypes' ;
CMD_ASSERT             : 'assert' ;
CMD_CHECK_SAT          : 'check-sat' ;
CMD_CHECK_SAT_ASSUMING : 'check-sat-assuming' ;
CMD_GET_MODEL          : 'get-model' ;
CMD_GET_INFO           : 'get-info' ;
CMD_GET_OPTION         : 'get-option' ;
CMD_GET_VALUE          : 'get-value' ;
CMD_GET_ASSERTIONS     : 'get-assertions' ;
CMD_GET_PROOF          : 'get-proof' ;
CMD_GET_UNSAT_CORE     : 'get-unsat-core' ;
CMD_PUSH               : 'push' ;
CMD_POP                : 'pop' ;
CMD_RESET              : 'reset' ;
CMD_RESET_ASSERTIONS   : 'reset-assertions' ;
CMD_ECHO               : 'echo' ;
CMD_EXIT               : 'exit' ;

NUMERAL     : '0' | [1-9] DIGIT* ;
DECIMAL     : NUMERAL '.' DIGIT+ ;
HEXADECIMAL : '#x' [0-9a-fA-F]+ ;
BINARY      : '#b' [01]+ ;
STRING      : '"' ( ~'"' | '""' )* '"' ;

SIMPLE_SYMBOL : SYMBOL_START SYMBOL_CHAR* ;
QUOTED_SYMBOL : '|' ~[|\\]* '|' ;
KEYWORD       : ':' SYMBOL_CHAR+ ;

COMMENT    : ';' ~[\r\n]* -> skip ;
WHITESPACE : [ \t\r\n\f]+ -> skip ;

// Anything else is reported as a lexer error instead of being dropped silently.
ERROR_CHAR : . ;

fragment DIGIT        : [0-9] ;
fragment SYMBOL_START : [a-zA-Z~!@$%^&*_\-+=<>.?/] ;
fragment SYMBOL_CHAR  : [a-zA-Z0-9~!@$%^&*_\-+=<>.?/] ;
