grammar cpp;

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





