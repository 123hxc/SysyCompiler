lexer grammar SysYLexer;

// Keywords
CONST: 'const';
INT: 'int';
VOID: 'void';
IF: 'if';
ELSE: 'else';
WHILE: 'while';
BREAK: 'break';
CONTINUE: 'continue';
RETURN: 'return';

// Operators
PLUS: '+';
MINUS: '-';
MUL: '*';
DIV: '/';
MOD: '%';
ASSIGN: '=';
EQ: '==';
NEQ: '!=';
LT: '<';
GT: '>';
LE: '<=';
GE: '>=';
NOT: '!';
AND: '&&';
OR: '||';

// Delimiters
L_PAREN: '(';
R_PAREN: ')';
L_BRACE: '{';
R_BRACE: '}';
L_BRACKT: '[';
R_BRACKT: ']';
COMMA: ',';
SEMICOLON: ';';

// Identifier: letter or underscore, followed by letters, digits, or underscores
IDENT: [a-zA-Z_] [a-zA-Z0-9_]*;

// Integer constants: decimal, octal, or hexadecimal
INTEGER_CONST: 
    DECIMAL_CONST
    | OCTAL_CONST
    | HEXADECIMAL_CONST
;

fragment DECIMAL_CONST: [1-9] [0-9]* | '0';
fragment OCTAL_CONST: '0' [0-7]+;
fragment HEXADECIMAL_CONST: ('0x' | '0X') [0-9a-fA-F]+;

// Whitespace: skip
WS: [ \r\n\t]+ -> skip;

// Line comment: // until newline
LINE_COMMENT: '//' ~[\r\n]* -> skip;

// Multi-line comment: /* ... */
MULTILINE_COMMENT: '/*' .*? '*/' -> skip;