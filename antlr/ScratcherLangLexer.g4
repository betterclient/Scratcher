lexer grammar ScratcherLangLexer;

IMPORT : 'import';
AS : 'as';
CONST  : 'const';
STRUCT : 'struct';
RETURN : 'return';
IF     : 'if';
ELSE   : 'else';
WHILE  : 'while';
REPEAT : 'repeat';
FOR    : 'for';
IN     : 'in';
ON     : 'on';
WARP   : 'warp';
EXPORT : 'export';
ENUM   : 'enum';

INT_TYPE   : 'int';
FLOAT_TYPE : 'float';
STR_TYPE   : 'str';
VOID_TYPE  : 'void';
BOOL_TYPE  : 'bool';
LIST       : 'List';

TRUE  : 'true';
FALSE : 'false';
NULL  : 'null';
NULLABLE : '?';

LPAREN : '(';
RPAREN : ')';
LBRACE : '{' -> pushMode(DEFAULT_MODE);
RBRACE : '}' -> popMode;
LBRACK : '[';
RBRACK : ']';
COMMA  : ',';
SEMI   : ';';
COLONCOLON : '::';
DOT    : '.';

ADD_ASSIGN : '+=';
SUB_ASSIGN : '-=';
MUL_ASSIGN : '*=';
DIV_ASSIGN : '/=';
ASSIGN : '=';

PLUS  : '+';
MINUS : '-';
DOUBLE_BANG : '!!';
BANG  : '!';
STAR  : '*';
SLASH : '/';
MOD   : '%';
LT    : '<';
GT    : '>';
LE    : '<=';
GE    : '>=';
EQ    : '==';
NE    : '!=';
AND   : '&&';
OR    : '||';

INT        : [0-9]+ ;
FLOAT      : [0-9]+ '.' [0-9]+ ;
IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* ;

OPEN_QUOTE : '"' -> pushMode(StringMode);

WS : [ \t\r\n]+ -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;

mode StringMode;

STR_TEXT     : ~["\\$]+ ;
STR_ESC      : '\\' . ;
DOLLAR       : '$' ;
INTERP_START : '${' -> pushMode(DEFAULT_MODE) ;
CLOSE_QUOTE  : '"' -> popMode ;