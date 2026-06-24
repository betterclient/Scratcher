parser grammar ScratcherLangParser;

options { tokenVocab = ScratcherLangLexer; }

program
    : importDecl* topLevelElement* EOF
    ;

importDecl
    : IMPORT (IDENTIFIER | plainStringLiteral) (AS IDENTIFIER)? SEMI
    ;

topLevelElement
    : tlVarDecl
    | funcDecl
    | structDecl
    | eventDecl
    ;

statement
    : varDecl
    | exprStmt
    | assignStmt
    | ifStmt
    | whileStmt
    | repeatStmt
    ;

tlVarDecl
    : (isConst=CONST)? type IDENTIFIER (ASSIGN expression)? SEMI
    ;

varDecl
    : type IDENTIFIER ASSIGN expression SEMI
    ;

funcDecl
    : type IDENTIFIER LPAREN paramList? RPAREN block
    ;

paramList
    : param (COMMA param)*
    ;

param
    : type IDENTIFIER
    ;

structDecl
    : STRUCT IDENTIFIER LPAREN structField (COMMA structField)* RPAREN SEMI
    ;

structField
    : type IDENTIFIER
    ;

block
    : LBRACE statement* returnStmt? RBRACE
    ;

exprStmt
    : expression SEMI
    ;

assignStmt
    : expression ASSIGN expression SEMI
    ;

returnStmt
    : RETURN expression? SEMI
    ;

ifStmt
    : IF LPAREN expression RPAREN block (ELSE (ifStmt | block))?
    ;

whileStmt
    : WHILE LPAREN expression RPAREN block
    ;

repeatStmt
    : REPEAT LPAREN expression RPAREN block
    ;

eventDecl
    : ON IDENTIFIER ( LPAREN eventArg? RPAREN )? block
    ;

eventArg
    : IDENTIFIER
    | literal
    ;

expression
    : LPAREN expression RPAREN                          # parensExpr
    | IDENTIFIER COLONCOLON IDENTIFIER                  # scopeExpr
    | expression DOT IDENTIFIER                         # memberExpr
    | functionIdentifier LPAREN argList? RPAREN         # callExpr
    | (PLUS | MINUS | BANG) expression                  # unaryExpr
    | expression (STAR | SLASH | MOD) expression        # multExpr
    | expression (PLUS | MINUS) expression              # addExpr
    | expression (LT | GT | LE | GE) expression         # relExpr
    | expression (EQ | NE) expression                   # eqExpr
    | expression AND expression                         # andExpr
    | expression OR expression                          # orExpr
    | literal                                           # literalExpr
    | IDENTIFIER                                        # idExpr
    ;

functionIdentifier
    : IDENTIFIER
    | typePath
    ;

argList
    : expression (COMMA expression)*
    ;

type
    : typePath
    | primitiveType
    ;

typePath
    : IDENTIFIER (COLONCOLON IDENTIFIER)*
    ;

primitiveType
    : INT_TYPE
    | FLOAT_TYPE
    | STR_TYPE
    | VOID_TYPE
    | BOOL_TYPE
    ;

literal
    : INT
    | FLOAT
    | stringLiteral
    | TRUE
    | FALSE
    ;

stringLiteral
    : OPEN_QUOTE stringPart* CLOSE_QUOTE
    ;

stringPart
    : STR_TEXT
    | STR_ESC
    | DOLLAR
    | interpolation
    ;

plainStringLiteral
    : OPEN_QUOTE plainStringPart* CLOSE_QUOTE
    ;

plainStringPart
    : STR_TEXT
    | STR_ESC
    | DOLLAR
    ;

interpolation
    : INTERP_START expression RBRACE
    ;