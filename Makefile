include Makefile.git

ANTLRPATH = $(shell find /usr/local/lib -name "antlr-*-complete.jar")
LIBPATH = /usr/local/lib/*

DOMAINNAME = oj.compilers.cpl.icu
ANTLR = java -jar /usr/local/lib/antlr-*-complete.jar -listener -visitor -long-messages
JAVAC = javac -g
JAVA = java

FILEPATH = ./tests/test1.sysy
OUTPATH = ./out.ll

PFILE = $(shell find . -name "SysYParser.g4")
LFILE = $(shell find . -name "SysYLexer.g4")
JAVAFILE = $(shell find . -name "*.java")


compile: antlr
	$(call git_commit,"make")
	mkdir -p classes
	$(JAVAC) -classpath ".:$(LIBPATH)" $(JAVAFILE) -d classes

run: compile
	java -classpath "./classes:$(LIBPATH)" Main $(FILEPATH) $(OUTPATH)


antlr: $(LFILE) $(PFILE) 
	$(ANTLR) $(PFILE) $(LFILE)


test: compile
	$(call git_commit, "test")
	nohup java -classpath "./classes:$(LIBPATH)" Main ./tests/test1.sysy &


clean:
	rm -f src/*.tokens
	rm -f src/*.interp
	rm -f src/SysYLexer.java src/SysYParser.java src/SysYParserBaseListener.java src/SysYParserBaseVisitor.java src/SysYParserListener.java src/SysYParserVisitor.java
	rm -rf classes
	rm -rf out
	rm -rf src/.antlr


submit: clean
	git gc
	bash submit.sh


.PHONY: compile antlr test run clean submit

