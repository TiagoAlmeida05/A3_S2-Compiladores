grammar Javamm;

@header {
    package pt.up.fe.comp2026;
}

CLASS   : 'class';
INT     : 'int';
BOOLEAN : 'boolean';
STATIC  : 'static';
RETURN  : 'return';
PACKAGE : 'package';
IMPORT  : 'import';
PUBLIC  : 'public';
PRIVATE   : 'private';
PROTECTED : 'protected';
EXTENDS : 'extends';
NEW  : 'new';
THIS : 'this';

IF      : 'if';
ELSE    : 'else';
FOR     : 'for';
WHILE   : 'while';
DO      : 'do';

INTEGER : '0' | [1-9][0-9]*;
BOOL    : 'true' | 'false';
VOID    : 'void';

ID      : [$_a-zA-Z][$_a-zA-Z0-9]*;

WS             : [ \t\n\r\f]+  -> skip;
SINGLE_COMMENT : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT  : '/*' .*? '*/' -> skip;

// Program Structure

program
    : packageDecl importDecl* classNode=classDecl EOF
    ;

importDecl
    : IMPORT path=qualifiedName ';'
    ;

packageDecl
    : PACKAGE path=qualifiedName ';'
    ;

qualifiedName
    : parts+=ID ('.' parts+=ID)*
    ;

// Class Declaration

classDecl
    : CLASS name=ID (EXTENDS superclass=ID)? '{'
        (varDecl | methodDecl)*
      '}'
    ;

// Types
type
    : baseType ('[' ']')*                      #ArrayType
    ;

baseType
    : INT                                      #IntType
    | BOOLEAN                                  #BooleanType
    | ID                                       #ClassType
    ;

methodType
    : type
    | VOID
    ;

visibility
    : PUBLIC
    | PRIVATE
    | PROTECTED
    ;

// Variables and Parameters

varDecl
    : typeNode=type name=ID ';'
    | typeNode=type name=ID '=' expr ';'
    ;

param
    : typeNode=type name=ID
    ;

paramList
    : params+=param (',' params+=param)*
    ;

argList
    : args+=expr (',' args+=expr)*
    ;

forInit
    : typeNode=type name=ID '=' value=expr      #ForVarInit
    | name=ID '=' value=expr                    #ForAssignInit
    ;

forIter
    : name=ID op=('+=' | '-=' | '*='
    | '/=' | '%=') value=expr                   #ForCompoundIter
    | name=ID '=' value=expr                    #ForAssignIter
    | expr                                      #ForExprIter
    ;

// Methods

methodDecl locals [boolean isStatic=false]
    : visibility?
      (STATIC {$isStatic=true;})?
      returnType=methodType
      name=ID
      '(' paramList? ')'
      '{'
          (varDecl | stmt)*
      '}'
    ;

// Statements

stmt
    // Block
    : '{' stmt* '}'                                  #BlockStmt

    // Control Flow
    | IF '(' cond=expr ')' thenStmt=stmt ELSE elseStmt=stmt  #IfElseStmt
    | IF '(' cond=expr ')' thenStmt=stmt                     #IfStmt

    | FOR '(' init=forInit ';' cond=expr ';' iter=forIter ')'
     body=stmt                                              #ForStmt

    | WHILE '(' cond=expr ')' body=stmt                     #WhileStmt

    | DO body=stmt WHILE '(' cond=expr ')' ';'              #DoWhileStmt

    // Assignment
    | var=ID '=' value=expr ';'                             #AssignStmt
    | var=ID op=('+=' | '-=' | '*=' | '/=' | '%=')
    value=expr ';'                                          #CompoundAssignStmt
    | target=expr '[' index=expr ']' '=' value=expr ';'     #ArrayAssignStmt
    | target=expr '[' index=expr ']'
      op=('+=' | '-=' | '*=' | '/=' | '%=') value=expr ';'  #ArrayCompoundAssignStmt

    // Expression
    | value=expr ';'                                        #ExprStmt

    // Return
    | RETURN value=expr ';'                                 #ReturnStmt
    | RETURN ';'                                            #ReturnVoidStmt
    ;

// Expressions

expr
    // Member access
    : target=expr '[' index=expr ']'                        #ArrayAccessExpr
    | target=expr '.' 'length'                              #ArrayLengthExpr
    | target=expr '.' var=ID                                #VarAccess
    | target=expr '.' method=ID '(' argList? ')'            #MethodCallExpr

    // Unary
    | op=('!' | '+' | '-') operand=expr                     #UnaryOp
    | op=('++' | '--') operand=expr                         #PrefixOp

    // Arithmetic
    | left=expr op=('*' | '/' | '%') right=expr             #BinaryExpr
    | left=expr op=('+' | '-') right=expr                   #BinaryExpr

    // Relational
    | left=expr op=('==' | '!='
    | '<' | '>' | '<=' | '>=') right=expr                   #BinaryExpr

    // Logical
    | left=expr op='&&' right=expr                          #BinaryExpr
    | left=expr op='||' right=expr                          #BinaryExpr

    // New Object
    | NEW name=ID '(' argList? ')'                           #NewObjectExpr

    // New Array
    | NEW INT '[' size=expr ']'                             #NewIntArrayExpr
    | NEW name=ID '[' size=expr ']'                         #NewArrayExpr

    // Array Initializer
    | NEW INT '[' ']' '{'
    (elems+=expr (',' elems+=expr)*)? '}'                   #ArrayInitExpr

    // Implicit this
    | method=ID '(' argList? ')'                            #ImplicitThisCallExpr

    // Primary
    | '(' inner=expr ')'                                    #PriorityExpr
    | THIS                                                  #ThisExpr
    | value=INTEGER                                         #IntegerLiteral
    | value=BOOL                                            #BooleanLiteral
    | name=ID                                               #VarRefExpr
    ;