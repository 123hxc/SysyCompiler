import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;

public class SemanticVisitor extends SysYParserBaseVisitor<Type> {
    private List<String> errorMessages = new ArrayList<>();
    private Scope currentScope;
    private boolean hasError = false;
    private Type expectedRetType = null;

    public SemanticVisitor(Scope globalScope) {
        this.currentScope = globalScope;
    }

    public boolean hasError() {
        return hasError;
    }

    @Override
    public Type visitVarDef(SysYParser.VarDefContext ctx) {
        String name = ctx.IDENT().getText();
        int line = ctx.IDENT().getSymbol().getLine();
        if (currentScope.isDeclaredLocally(name)) {
            reportError(3, line, "Redefine a Value");
            return null;
        }
        Type baseType = Type.INT;
        for (int i = 0; i < ctx.constExp().size(); i++) {
            baseType = new ArrayType(baseType);
        }
        if (ctx.ASSIGN() != null) {
            // 定义语句,检查类型匹配
            Type initType = visit(ctx.initVal());
            // 只有非数组时才报错
            if (ctx.constExp().isEmpty() && initType != null && !baseType.isSameType(initType)) {
                reportError(5, line, "Type dismatched");
            }
        }
        currentScope.define(name, baseType);
        return null;
    }

    @Override
    public Type visitLVal(SysYParser.LValContext ctx) {
        String name = ctx.IDENT().getText();
        int line = ctx.IDENT().getSymbol().getLine();
        Type type = currentScope.resolve(name);
        if (type == null) {
            reportError(1, line, "use defined value");
            type = Type.INT;
        }
        for (SysYParser.ExpContext expCtx : ctx.exp()) {
            Type indexType = visit(expCtx);
            if (indexType != null && !indexType.isInt()) {
                reportError(6, line, "Array index must be int");
            }
            if (type.isArray()) {
                type = ((ArrayType) type).getElementType();
            } else {
                reportError(9, line, "Not a Array");
                return Type.INT;
            }
        }
        return type;
    }

    @Override
    public Type visitStmt(SysYParser.StmtContext ctx) {
        if (ctx.ASSIGN() != null) {
            Type LeftType = visit(ctx.lVal());
            Type RightType = visit(ctx.exp());
            int line = ctx.lVal().getStart().getLine();
            if (LeftType instanceof FunctionType) {
                reportError(11, line, "Assign a function");
                return null;
            }
            if (LeftType != null && RightType != null && !LeftType.isSameType(RightType)) {
                reportError(5, line, "Type dismatched for Assignment");
            }
            return null;
        }
        if (ctx.RETURN() != null) {
            int line = ctx.getStart().getLine();
            Type actualReturnType = Type.VOID;
            if (ctx.exp() != null) {
                actualReturnType = visit(ctx.exp());
            }
            if (this.expectedRetType != null && actualReturnType != null
                    && !actualReturnType.isSameType(this.expectedRetType)) {
                reportError(7, line, "Return type dismatched");
            }
            return null;
        }
        return super.visitStmt(ctx);
    }

    @Override
    public Type visitExp(SysYParser.ExpContext ctx) {
        if (ctx.unaryOp()!=null){
            Type operandType = visit(ctx.exp(0));
            int line = ctx.getStart().getLine();
            if(operandType!=null&&!operandType.isInt()){
                reportError(6, line, "Type dismatched");
            }
            return Type.INT;
        }
        if (ctx.MUL() != null || ctx.DIV() != null || ctx.MOD() != null || ctx.PLUS() != null || ctx.MINUS() != null) {
            Type LeftType = visit(ctx.exp(0));
            Type RightType = visit(ctx.exp(1));
            int line = ctx.getStart().getLine();
            if (LeftType != null && RightType != null && !LeftType.isSameType(RightType)) {
                reportError(6, line, "Type dismatched for operands");
            }
            return Type.INT;
        }
        if (ctx.IDENT() != null && ctx.L_PAREN() != null) {
            String name = ctx.IDENT().getText();
            int line = ctx.IDENT().getSymbol().getLine();
            Type type = currentScope.resolve(name);
            if (type == null) {
                reportError(2, line, "Undefined function");
                return Type.INT;
            }
            if (!(type instanceof FunctionType)) {
                reportError(10, line, "Not a function");
                return Type.INT;
            }
            FunctionType functionType = (FunctionType) type;
            List<Type> formalParams = functionType.getParamsType();
            List<Type> actualParams = new ArrayList<>();
            if (ctx.funcRParams() != null) {
                for (SysYParser.ParamContext ParamCtx : ctx.funcRParams().param()) {
                    actualParams.add(visit(ParamCtx));
                }
            }
            if (formalParams.size() != actualParams.size()) {
                    reportError(8, line, "Function Params count dismatched");
                } else {
                    for (int i = 0; i < formalParams.size(); i++) {
                        Type fType = formalParams.get(i);
                        Type aType = actualParams.get(i);
                        if (aType != null && !aType.isSameType(fType)) {
                            reportError(8, line, "Function Params type dismatched");
                            break;
                        }
                    }
                }
            return functionType.getReturnType();
        }return super.visitExp(ctx);

    }

    @Override
    public Type visitFuncDef(SysYParser.FuncDefContext ctx) {
        String funcName = ctx.IDENT().getText();
        int line = ctx.IDENT().getSymbol().getLine();
        if (currentScope.isDeclaredLocally(funcName)) {
            reportError(4, line, "Function redefined");
            return null;
        }
        Type retType = ctx.funcType().getText().equals("int") ? Type.INT : Type.VOID;
        this.expectedRetType = retType;
        List<Type> paramsType = new ArrayList<>();
        List<Map.Entry<String, Type>> paramsInfo = new ArrayList<>();
        if (ctx.funcFParams() != null) {
            for (SysYParser.FuncFParamContext fParamCtx : ctx.funcFParams().funcFParam()) {
                Type pType = Type.INT;
                if (!fParamCtx.L_BRACKT().isEmpty()) {
                    // 数组
                    pType = new ArrayType(pType);
                    for (int i = 0; i < fParamCtx.exp().size(); i++) {
                        pType = new ArrayType(pType);
                    }
                }
                String pName = fParamCtx.IDENT().getText();
                paramsType.add(pType);
                paramsInfo.add(new AbstractMap.SimpleEntry<>(pName, pType));
            }
        }
        // 函数存入全局定义域
        FunctionType functionType = new FunctionType(retType, paramsType);
        currentScope.define(funcName, functionType);
        // 创建内部作用域
        Scope funcScope = new Scope(currentScope);
        Scope backupScope = currentScope;
        currentScope = funcScope;

        for (Map.Entry<String, Type> param : paramsInfo) {
            if (!currentScope.define(param.getKey(), param.getValue())) {
                reportError(3, line, "Value Redefined");
            }
        }
        if (ctx.block() != null) {
            visit(ctx.block());
        }
        currentScope = backupScope;
        this.expectedRetType = null;
        return null;
    }

    @Override
    public Type visitBlock(SysYParser.BlockContext ctx) {
        Scope backupScope = currentScope;
        currentScope = new Scope(currentScope);
        for (SysYParser.BlockItemContext itemCtx : ctx.blockItem()) {
            visit(itemCtx);
        }
        currentScope = backupScope;
        return null;
    }

    private void reportError(int typeID, int line, String msg) {
        hasError = true;
        String errorMessage = "Error type " + typeID + " at Line " + line + ": " + msg;
        errorMessages.add(errorMessage);
    }

    public void printSemanticErrorInformation() {
        for (String errorMessage : errorMessages) {
            System.err.println(errorMessage);
        }
    }

}
