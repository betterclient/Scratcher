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
    | enumDecl
    ;

statement
    : varDecl
    | returnIfStmt
    | assignIndexStmt
    | assignStmt
    | ifStmt
    | whileStmt
    | repeatStmt
    | forStmt
    | exprStmt
    ;

tlVarDecl
    : (isConst=CONST)? type IDENTIFIER (ASSIGN expression)? SEMI
    ;

varDecl
    : type IDENTIFIER ASSIGN expression SEMI
    ;

funcDecl
    : modifier* type IDENTIFIER LPAREN paramList? RPAREN block
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

structDecl
    : STRUCT IDENTIFIER LPAREN structField (COMMA structField)* RPAREN SEMI
    ;

structField
    : type NULLABLE? IDENTIFIER
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
    : FOR LPAREN type IDENTIFIER IN expression RPAREN block
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
    | expression LBRACK expression RBRACK               # indexExpr
    | LIST LPAREN type RPAREN                           # listCreationExpr
    | functionIdentifier LPAREN argList? RPAREN         # callExpr
    | expression DOUBLE_BANG                            # assertNonNull
    | (PLUS | MINUS | BANG) expression                  # unaryExpr
    | expression (STAR | SLASH | MOD) expression        # multExpr
    | expression (PLUS | MINUS) expression              # addExpr
    | expression (LT | GT | LE | GE) expression         # relExpr
    | expression (EQ | NE) expression                   # eqExpr
    | expression AND expression                         # andExpr
    | expression OR expression                          # orExpr
    | literal                                           # literalExpr
    | NULL                                              # nullExpr
    | IDENTIFIER                                        # idExpr
    | whenExpression                                    # whenExpr
    | ifExpression                                      # ifExpr
    ;

functionIdentifier
    : IDENTIFIER
    | typePath
    ;

argList
    : expression (COMMA expression)*
    ;

type
    : typePath                                      # pathType
    | primitiveType                                 # primType
    | type LBRACK RBRACK                            # arrayType
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

codeBlock
    : (block | statement)
    ;