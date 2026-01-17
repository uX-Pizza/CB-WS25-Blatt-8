grammar cpp;

<<<<<<< HEAD
//Parser
program
    : topLevelDecl* EOF
    ;

topLevelDecl
    : functionDef
    | classDef
    ;


type
    : baseType ref?
    ;

baseType
    : 'int'
    | 'bool'
    | 'char'
    | 'string'
    | 'void'
    | ID            // class type
    ;

ref
    : '&'
    ;


functionDef
    : type ID '(' paramList? ')' block
    ;

paramList
    : param (',' param)*
    ;

param
    : type ID
    ;


classDef
    : 'class' ID (':' 'public' ID)? '{'
        'public:' classMember*
      '}'
    ;

classMember
    : fieldDecl
    | methodDef
    | constructorDef
    ;

fieldDecl
    : type ID ';'
    ;

methodDef
    : ('virtual')? type ID '(' paramList? ')' block
    ;

constructorDef
    : ID '(' paramList? ')' block
    ;


block
    : '{' stmt* '}'
    ;

stmt
    : varDecl
    | exprStmt
    | ifStmt
    | whileStmt
    | returnStmt
    | block
    ;

varDecl
    : type ID ('=' expr)? ';'
    ;

exprStmt
    : expr ';'
    ;

ifStmt
    : 'if' '(' expr ')' block ('else' block)?
    ;

whileStmt
    : 'while' '(' expr ')' block
    ;

returnStmt
    : 'return' expr? ';'
    ;


expr
    : assignment
    ;

assignment
    : logicalOr ('=' assignment)?
    ;

logicalOr
    : logicalAnd ('||' logicalAnd)*
    ;

logicalAnd
    : equality ('&&' equality)*
    ;

equality
    : relational (('==' | '!=') relational)*
    ;

relational
    : additive (('<' | '<=' | '>' | '>=') additive)*
    ;

additive
    : multiplicative (('+' | '-') multiplicative)*
    ;

multiplicative
    : unary (('*' | '/' | '%') unary)*
    ;

unary
    : ('!' | '-' | '+') unary
    | postfix
    ;

postfix
    : primary ('.' ID ('(' argList? ')')?)*
    ;

primary
    : literal
    | ID
    | ID '(' argList? ')'
    | '(' expr ')'
    ;

argList
    : expr (',' expr)*
    ;

literal
    : INT
    | CHAR
    | STRING
    | BOOL
    ;

//LEXER
BOOL
    : 'true' | 'false'
    ;

INT
    : [0-9]+
    ;

CHAR
    : '\'' (ESC | ~['\\\r\n]) '\''
    ;

STRING
    : '"' (ESC | ~["\\\r\n])* '"'
    ;

ESC
    : '\\' .
    ;

ID
    : [_a-zA-Z][_a-zA-Z0-9]*
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

PREPROCESSOR
    : '#' ~[\r\n]* -> skip
    ;
=======
programm : stmt* EOF ;

//Alles wie funktions decleration, variabelen, main, fucktionscalls, while if return
stmt:

;

//Variabelen wie int i = 0 oder
// int i;
// i = 0
var_decl: 'int' ID (';' | ASSIGN_OP INT ';')
        | ID ASSIGN_OP INT ';'
        | 'char' ID ASSIGN_OP
        ;

decl_inc: ;

fn_delc: ;

abstract_fn : ;

params : ;

return : 'return' expr? ';' ;

block : '{' stmt* '}' ;

while : 'while' '(' expr ')' block ;

if : 'if' '(' expr ')' block ('else if' '(' expr ')')* ('else' expr)?  ;

fn_call : ;

agrs: ;

//funktionscalls und vergleiche
expr: fn_call
    |

    ;

constructor: ;

copy_constructor: ;

destructor : ;

operator : ;

class : ;

main: ;

array : ;

array_item: ;

obj_usage: ;

//Lexer_Regeln
NULL: 'NULL' ;
INT : [0-9]+ ;
CHAR : ~[\r\n]; //Nur zeichen
BOOL : ('true' | 'false') ;
DOUBLE : [0-9]+'.'[0-9]+;
ESC : '\\' . ;
STRING: '"'( ESC | ~[\r\n])*'"' ;
NEG: '-' ;
REF : '&' ;
ID: [_a-zA-Z][_a-zA-Z0-9]* ;

DEC_INC_OP: '++' | '--' ;
ASSIGN_OP : '=' | '*=' | '+=' | '-=';
MULTI_LINE_COMMENT :  '/*' .*? ('*/' | EOF ) -> skip;
COMMENT: '//' ( ~('\r'|'\n')* ) '\r'? '\n' -> skip ;
INCLUDE: '#include ' ( ~('\r'|'\n')* ) '\r'? '\n' -> skip ;





>>>>>>> origin/HEAD
