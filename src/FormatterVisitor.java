import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.Token;

public class FormatterVisitor extends SysYParserBaseVisitor<Void> {
    private StringBuilder output = new StringBuilder();
    private int indentLevel = 0;
    private boolean lastWasNewLine = true;
    private boolean needIndent = true;

    public String getFormattedCode() {
        
        return output.toString();
    }

    private void addIndent() {
        for (int i = 0; i < 4 * indentLevel; i++) {
            output.append(' ');
        }
    }

    private void addText(String text) {
        if (lastWasNewLine && needIndent) {
            addIndent();
            needIndent = false;
        }
        output.append(text);
        lastWasNewLine = false;
    }

    private void addNewLine() {
        output.append('\n');
        needIndent = true;
        lastWasNewLine = true;

    }

    private void addSpace() {
        output.append(' ');
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        Token token = node.getSymbol();
        int type = token.getType();
        String text = token.getText();
        // 没东西
        if (type == SysYLexer.WS ||
                type == SysYLexer.LINE_COMMENT ||
                type == SysYLexer.MULTILINE_COMMENT ||
                type == SysYLexer.EOF) {
            return null;
        }
        switch (type) {
            // 关键字
            case SysYLexer.SEMICOLON:
                addText(text);
                addNewLine();
                break;
            case SysYLexer.L_BRACE:
                addSpace();
                addText(text);
                break;
            case SysYLexer.R_BRACE:
                addNewLine();
                addText(text);
                break;
            case SysYLexer.COMMA:
                addText(text);
                addSpace();
                break;
            case SysYLexer.CONST:
            case SysYLexer.INT:
            case SysYLexer.VOID:
            case SysYLexer.IF:
            case SysYLexer.ELSE:
            case SysYLexer.WHILE:
            case SysYLexer.RETURN:
                addText(text);
                addSpace();
                break;
            case SysYLexer.PLUS:
            case SysYLexer.MINUS:
            case SysYLexer.MUL:
            case SysYLexer.DIV:
            case SysYLexer.MOD:
            case SysYLexer.ASSIGN:
            case SysYLexer.EQ:
            case SysYLexer.NEQ:
            case SysYLexer.LT:
            case SysYLexer.GT:
            case SysYLexer.LE:
            case SysYLexer.GE:
            case SysYLexer.AND:
            case SysYLexer.OR:
                addSpace();
                addText(text);
                addSpace();
                break;
            default:
                addText(text);
                break;
        }
        return null;
    }

    @Override
    public Void visitCompUnit(SysYParser.CompUnitContext ctx) {
        boolean firstFunc = true;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof SysYParser.FuncDefContext) {
                if (!firstFunc) {
                    addNewLine();
                }
                visit(child);
                firstFunc = false;
            } else {
                visit(child);
            }
        }
        return null;
    }

    @Override
    public Void visitBlock(SysYParser.BlockContext ctx) {
        addText("{");
        addNewLine();
        indentLevel++;
        for (SysYParser.BlockItemContext item : ctx.blockItem()) {
            visit(item);
        }
        indentLevel--;
        addText("}");
        addNewLine();
        return null;
    }

    @Override
    public Void visitStmt(SysYParser.StmtContext ctx) {
        if (ctx.IF() != null) {
            // if语句
            visit(ctx.IF());
            visit(ctx.L_PAREN());
            visit(ctx.cond());
            visit(ctx.R_PAREN());
            SysYParser.StmtContext thenStmt = ctx.stmt(0);
            if (thenStmt != null) {
                if (thenStmt.block() == null) {
                    addNewLine();
                    indentLevel++;
                    visit(thenStmt);
                    indentLevel--;
                } else {
                    addSpace();
                    visit(thenStmt);
                }

            }
            if (ctx.ELSE() != null) {
                visit(ctx.ELSE());
                SysYParser.StmtContext elseStmt = ctx.stmt(1);
                if (elseStmt != null) {
                    if (elseStmt.IF() != null) {
                        // else if
                        visit(elseStmt);
                    } else {
                        addNewLine();
                        indentLevel++;
                        visit(elseStmt);
                        indentLevel--;
                    }
                }
            }
        } else if (ctx.WHILE() != null) {
            visit(ctx.WHILE());
            visit(ctx.L_PAREN());
            visit(ctx.cond());
            visit(ctx.R_PAREN());
            SysYParser.StmtContext whileStmt = ctx.stmt(0);
            if (whileStmt != null) {
                if (whileStmt.block() == null) {
                    addNewLine();
                    indentLevel++;
                    visit(whileStmt);
                    indentLevel--;
                } else {
                    addSpace();
                    visit(whileStmt);
                }
            }

        } else if (ctx.RETURN() != null) {
            // return
            addText("return");
            if (ctx.exp() != null) {
                addSpace();
                visit(ctx.exp());
                visit(ctx.SEMICOLON());
            } else {
                visit(ctx.SEMICOLON());
            }
        } else {
            visitChildren(ctx);
        }
        return null;
    }

    @Override
    public Void visitVarDecl(SysYParser.VarDeclContext ctx) {
        addText(ctx.bType().getText());
        addSpace();
        visit(ctx.varDef(0));
        for (int i = 1; i < ctx.varDef().size(); i++) {
            addText(",");
            addSpace();
            visit(ctx.varDef(i));
        }
        visit(ctx.SEMICOLON());
        return null;
    }

    @Override
    public Void visitVarDef(SysYParser.VarDefContext ctx) {
        addText(ctx.IDENT().getText());
        for (int i = 0; i < ctx.constExp().size(); i++) {
            addText("[");
            visit(ctx.constExp(i));
            addText("]");
        }
        if (ctx.ASSIGN() != null) {
            addSpace();
            addText("=");
            addSpace();
            visit(ctx.initVal());
        }
        return null;

    }

    @Override
    public Void visitInitVal(SysYParser.InitValContext ctx) {
        if (ctx.L_BRACE() != null) {
            addText(ctx.L_BRACE().getText());
            for (int i = 0; i < ctx.initVal().size(); i++) {
                if (i > 0) {
                    addText(",");
                    addSpace();
                }
                visit(ctx.initVal(i));
            }
            addText(ctx.R_BRACE().getText());
        } else {
            visitChildren(ctx);
        }
        return null;
    }

    @Override
    public Void visitFuncDef(SysYParser.FuncDefContext ctx) {
        addNewLine();
        visit(ctx.funcType());
        addText(ctx.IDENT().getText());
        addText("(");
        if (ctx.funcFParams() != null) {
            visit(ctx.funcFParams());
        }
        addText(")");
        addSpace();
        visit(ctx.block());
        return null;
    }

    @Override
    public Void visitConstInitVal(SysYParser.ConstInitValContext ctx) {
        if (ctx.L_BRACE() != null) {
            addText(ctx.L_BRACE().getText());
            for (int i = 0; i < ctx.constInitVal().size(); i++) {
                if (i > 0) {
                    addText(",");
                    addSpace();
                }
                visit(ctx.constInitVal(i));
            }
            addText(ctx.R_BRACE().getText());

        } else {
            visitChildren(ctx);
        }
        return null;
    }

    @Override
    public Void visitExp(SysYParser.ExpContext ctx) {
        if (ctx.unaryOp() != null && ctx.exp().size() == 1) {
            addText(ctx.unaryOp().getText());
            visit(ctx.exp(0));
        } else {
            visitChildren(ctx);
        }
        return null;
    }

}
