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
    | sealedEnumDecl
    | enumDecl
    ;

statement
    : varDecl
    | returnIfStmt
    | assignIndexStmt
    | assignStmt
    | postIncStmt
    | ifStmt
    | whileStmt
    | repeatStmt
    | forStmt
    | exprStmt
    ;

tlVarDecl
    : (isConst=CONST)? (AUTO | type) IDENTIFIER (ASSIGN expression)? SEMI
    ;

varDecl
    : (AUTO | type) IDENTIFIER ASSIGN expression SEMI
    ;

funcDecl
    : modifier* typeParameters? type (type DOT)? IDENTIFIER LPAREN paramList? RPAREN block
    ;

typeParameters
    : LT IDENTIFIER (COMMA IDENTIFIER)* GT
    ;

modifier
    : EXPORT
    | WARP
    ;

paramList
    : param (COMMA param)*
    ;

param
    : type IDENTIFIER
    ;

enumDecl
    : ENUM IDENTIFIER LPAREN IDENTIFIER (COMMA IDENTIFIER)* RPAREN SEMI
    ;

sealedEnumDecl
    : SEALED ENUM IDENTIFIER LBRACE (sealedEnumArg (COMMA sealedEnumArg)*)? RBRACE
    ;

sealedEnumArg
    : IDENTIFIER (LPAREN (type IDENTIFIER (COMMA type IDENTIFIER)*) RPAREN)?
    ;

structDecl
    : STRUCT IDENTIFIER typeParameters? LPAREN structField (COMMA structField)* RPAREN SEMI
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
    : expression assignOp expression SEMI
    ;

assignIndexStmt
    : expression LBRACK expression RBRACK ASSIGN expression SEMI
    ;

assignOp
    : ASSIGN
    | ADD_ASSIGN
    | SUB_ASSIGN
    | MUL_ASSIGN
    | DIV_ASSIGN
    ;

returnIfStmt
    : RETURN expression? IF LPAREN? expression RPAREN? SEMI?
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

forStmt
    : FOR LPAREN (AUTO | type) IDENTIFIER IN expression RPAREN block
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
    | expression DOUBLE_BANG                            # assertNonNull
    | expression DOT IDENTIFIER                         # memberExpr
    | expression SAFE_DOT IDENTIFIER                    # safeDotExpr
    | expression LBRACK expression RBRACK               # indexExpr
    | LIST LPAREN type RPAREN                           # listCreationExpr
    | functionIdentifier LPAREN argList? RPAREN         # callExpr
    | expression LPAREN argList? RPAREN                 # dynamicCallExpr
    | (PLUS | MINUS | BANG) expression                  # unaryExpr
    | expression (STAR | SLASH | MOD) expression        # multExpr
    | expression (PLUS | MINUS) expression              # addExpr
    | expression (LT | GT | LE | GE) expression         # relExpr
    | expression (EQ | NE) expression                   # eqExpr
    | expression AND expression                         # andExpr
    | expression OR expression                          # orExpr
    | <assoc=right> expression ELVIS expression         # nonNullOrElse
    | literal                                           # literalExpr
    | NULL                                              # nullExpr
    | IDENTIFIER                                        # idExpr
    | whenExpression                                    # whenExpr
    | ifExpression                                      # ifExpr
    | AMPERSAND functionIdentifier                      # funcRefExpr
    | lambdaDecl ARROW lambdaBlock                      # lambdaExpr
    | THIS                                              # thisExpr
    ;

lambdaDecl
    : type IDENTIFIER
    | LPAREN (type IDENTIFIER (COMMA type IDENTIFIER)*)? RPAREN
    ;

functionIdentifier
    : IDENTIFIER
    | typePath
    ;

argList
    : expression (COMMA expression)*
    ;

type
    : typePath (LT type (COMMA type)* GT)?          # pathType
    | primitiveType                                 # primType
    | type LBRACK RBRACK                            # arrayType
    | type NULLABLE                                 # nullableType
    | LPAREN (type (COMMA type)*)? RPAREN ARROW type# funcRefType
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
    | CHAR_TYPE
    ;

literal
    : INT
    | FLOAT
    | stringLiteral
    | TRUE
    | FALSE
    | TICK IDENTIFIER TICK
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

whenExpression
    : WHEN (LPAREN expression RPAREN)? LBRACE whenEntry* RBRACE
    ;

whenEntry
    : whenCondition ARROW (expression | codeBlock)
    ;

whenCondition
    : expression
    | ELSE
    ;

ifExpression
    : IF LPAREN expression RPAREN exprBlock (ELSE (ifExpression | exprBlock))
    ;

exprBlock
    : expression
    | LBRACE statement* expression RBRACE
    ;

lambdaBlock
    : exprBlock                            # exprLambda
    | block                                 # blockLambda
    ;

codeBlock
    : (block | statement)
    ;

postIncStmt
    : expression PLUS PLUS SEMI   # plusPlus
    | expression MINUS MINUS SEMI # minusMinus
    ;