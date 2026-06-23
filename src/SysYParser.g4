parser grammar SysYParser;

options {
    tokenVocab = SysYLexer;
}

// 程序入口
program
    : compUnit
    ;

// 编译单元
compUnit
    : (decl | funcDef)* EOF
    ;

// 声明
decl
    : constDecl
    | varDecl
    ;

// 常量声明
constDecl
    : CONST bType constDef (COMMA constDef)* SEMICOLON
    ;

// 基本类型
bType
    : INT
    ;

// 常量定义
constDef
    : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal
    ;

// 常量初值
constInitVal
    : constExp
    | L_BRACE (constInitVal (COMMA constInitVal)*)? R_BRACE
    ;

// 变量声明
varDecl
    : bType varDef (COMMA varDef)* SEMICOLON
    ;

// 变量定义
varDef
    : IDENT (L_BRACKT constExp R_BRACKT)* (ASSIGN initVal)?
    ;

// 变量初值
initVal
    : exp
    | L_BRACE (initVal (COMMA initVal)*)? R_BRACE
    ;

// 函数定义
funcDef
    : funcType IDENT L_PAREN (funcFParams)? R_PAREN block
    ;

// 函数类型
funcType
    : INT
    | VOID
    ;

// 函数形参表
funcFParams
    : funcFParam (COMMA funcFParam)*
    ;

// 函数形参
funcFParam
    : bType IDENT (L_BRACKT R_BRACKT (L_BRACKT exp R_BRACKT)*)?
    ;

// 语句块
block
    : L_BRACE blockItem* R_BRACE
    ;

// 语句块项
blockItem
    : decl
    | stmt
    ;

// 语句
stmt
    : lVal ASSIGN exp SEMICOLON                    // 赋值语句
    | (exp)? SEMICOLON                             // 表达式语句
    | block                                        // 复合语句
    | IF L_PAREN cond R_PAREN stmt (ELSE stmt)?    // if语句
    | WHILE L_PAREN cond R_PAREN stmt              // while语句
    | BREAK SEMICOLON                              // break语句
    | CONTINUE SEMICOLON                           // continue语句
    | RETURN (exp)? SEMICOLON                      // return语句
    ;

// 左值表达式
lVal
    : IDENT (L_BRACKT exp R_BRACKT)*
    ;

// 表达式（左递归形式，支持优先级）
exp
    : L_PAREN exp R_PAREN                          // 括号表达式
    | lVal                                         // 左值
    | number                                       // 数值常量
    | IDENT L_PAREN funcRParams? R_PAREN           // 函数调用
    | unaryOp exp                                  // 单目运算
    | exp (MUL | DIV | MOD) exp                    // 乘除模运算（左结合）
    | exp (PLUS | MINUS) exp                       // 加减运算（左结合）
    ;

// 条件表达式（左递归形式，支持优先级）
cond
    : exp                                          // 表达式
    | cond (LT | GT | LE | GE) cond                // 关系运算
    | cond (EQ | NEQ) cond                         // 相等性运算
    | cond AND cond                                // 逻辑与
    | cond OR cond                                 // 逻辑或
    ;

// 单目运算符
unaryOp
    : PLUS
    | MINUS
    | NOT
    ;

// 函数实参表
funcRParams
    : param (COMMA param)*
    ;

// 实参
param
    : exp
    ;

// 数值常量
number
    : INTEGER_CONST
    ;

// 常量表达式
constExp
    : exp
    ;