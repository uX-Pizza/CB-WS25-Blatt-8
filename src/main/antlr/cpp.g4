grammar cpp;

@header {
package cpp;
}

//Parser
program
    : topLevelDecl* EOF
    ;

replInput
    : (topLevelDecl | stmt | expr) EOF
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
      '}' ';'?
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
