`antlr4 -visitor src/SysYParser.g4`：生成语法词法分析器

`javac -cp "/usr/local/lib/antlr-4.9.1-complete.jar:src" src/*.java`：编译

`java -cp "/usr/local/lib/antlr-4.9.1-complete.jar:src" Main tests/test5.sysy`运行

`rm src/*.class`清除所有.class文件

`git checkout -b lab2`切换分支

`git add.`提交
`git commit -m "msg"`
`make submit`


`javac -cp "/usr/local/lib/antlr-4.9.1-complete.jar:lib/*:src" src/*.java -d bin`
`java -cp "/usr/local/lib/antlr-4.9.1-complete.jar:lib/*:bin" Main tests/test5.sysy out.ll`