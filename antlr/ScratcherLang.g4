grammar ScratcherLang;

program
    : importDecl* topLevelElement* EOF
    ;

importDecl
    : 'import' (IDENTIFIER | PLAIN_STRING) ';'
    ;

topLevelElement
    : tlVarDecl
    | funcDecl
    | structDecl
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
    : (isConst='const')? type IDENTIFIER ('=' expression)? ';'
    ;

varDecl
    : type IDENTIFIER '=' expression ';'
    ;

funcDecl
    : type IDENTIFIER '(' paramList? ')' block
    ;

paramList
    : param (',' param)*
    ;

param
    : type IDENTIFIER
    ;

structDecl
    : 'struct' IDENTIFIER '(' structField (',' structField)* ')' ';'
    ;

structField
    : type IDENTIFIER
    ;

block
    : '{' statement* returnStmt? '}'
    ;

exprStmt
    : expression ';'
    ;

assignStmt
    : expression '=' expression ';'
    ;

returnStmt
    : 'return' expression ';'
    ;

ifStmt
    : 'if' '(' expression ')' block ('else' (ifStmt | block))?
    ;

whileStmt
    : 'while' '(' expression ')' block
    ;

repeatStmt
    : 'repeat' '(' expression ')' block
    ;

expression
    : '(' expression ')'                          # parensExpr
    | expression '::' IDENTIFIER                  # scopeExpr
    | expression '.' IDENTIFIER                   # memberExpr
    | expression '(' argList? ')'                 # callExpr
    | ('+' | '-' | '!') expression                # unaryExpr
    | expression ('*' | '/' | '%') expression     # multExpr
    | expression ('+' | '-') expression           # addExpr
    | expression ('<' | '>' | '<=' | '>=') expression # relExpr
    | expression ('==' | '!=') expression         # eqExpr
    | expression '&&' expression                  # andExpr
    | expression '||' expression                  # orExpr
    | literal                                     # literalExpr
    | IDENTIFIER                                  # idExpr
    ;

argList
    : expression (',' expression)*
    ;

type
    : typePath
    | primitiveType
    ;

typePath
    : IDENTIFIER ('::' IDENTIFIER)*
    ;

primitiveType
    : 'int'
    | 'float'
    | 'str'
    | 'void'
    | 'bool'
    ;

literal
    : INT
    | FLOAT
    | PLAIN_STRING
    | 'true'
    | 'false'
    ;

INT     : [0-9]+ ;
FLOAT   : [0-9]+ '.' [0-9]+ ;

//TODO: interpolation
PLAIN_STRING
    : '"' ( ~["\\$] | '$' ~'{' | '\\' . )* '"'
    ;

IDENTIFIER
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;