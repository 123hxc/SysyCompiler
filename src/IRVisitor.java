import org.llvm4j.llvm4j.*;
import org.llvm4j.llvm4j.Module;
import org.llvm4j.optional.Option;
import java.io.File;
import java.util.Stack;

public class IRVisitor extends SysYParserBaseVisitor<Value> {

    // =======================================================
    // 1. LLVM 核心基础设施
    // =======================================================
    private final Context context = new Context();
    private final IRBuilder builder = context.newIRBuilder();
    private final Module module = context.newModule("sysy_module");
    private Function currentFunction;

    // 常用类型缓存
    private final IntegerType i32 = context.getInt32Type();
    private final IntegerType i1 = context.getInt1Type();
    private final VoidType voidType = context.getVoidType();
    
    // 作用域管理 (你刚写好的 IRScope)
    private IRScope currentScope = new IRScope(null);

    // =======================================================
    // 2. 循环控制栈 (用于处理 break 和 continue)
    // =======================================================
    private static class LoopInfo {
        BasicBlock condBlock; // continue 的跳转目标
        BasicBlock nextBlock; // break 的跳转目标
        public LoopInfo(BasicBlock cond, BasicBlock next) {
            this.condBlock = cond;
            this.nextBlock = next;
        }
    }
    private final Stack<LoopInfo> loopStack = new Stack<>();

    // 判断当前是否需要返回左值地址 (例如赋值语句左边需要地址而不是值)
    private boolean needLValueAddress = false;

    // 导出模块方法
    public void dump(String path) {
        module.dump(Option.of(new File(path)));
    }

    // =======================================================
    // Part 1 & 3: 函数定义
    // =======================================================
    @Override
    public Value visitFuncDef(SysYParser.FuncDefContext ctx) {
        String funcName = ctx.IDENT().getText();
        boolean isVoid = ctx.funcType().getText().equals("void");
        Type retType = isVoid ? voidType : i32;

        // 这里假设没有参数，如果有参数需要构造 Type[]
        // 实验 Part 3 需要在这里解析 FuncFParams，构建参数类型列表
        Type[] paramTypes = new Type[]{}; 
        FunctionType funcType = context.getFunctionType(retType, paramTypes, false);
        Function func = module.addFunction(funcName, funcType);
        this.currentFunction = func;

        // 创建入口基本块
        BasicBlock entryBlock = context.newBasicBlock(funcName + "Entry");
        func.addBasicBlock(entryBlock);
        builder.positionAfter(entryBlock);

        // 开启新的函数作用域
        currentScope = new IRScope(currentScope);

        // TODO: Part 3 - 遍历参数列表，为每个参数 alloca，store，并放入 currentScope

        // 访问函数体
        visit(ctx.block());

        // 离开作用域
        currentScope = currentScope.getParent();

        // 容错处理：如果基本块没有以 ret 结尾，补上默认返回
        // (LLVM 要求每个基本块必须有 terminator)
        // 注意：这里需要检查当前块是否已经终止，LLVM4J 可能需要通过某些标志位判断，或者简单粗暴在语义分析保证都有 return
        if (isVoid) {
            builder.buildReturn(Option.empty());
        }

        return func;
    }

    @Override
    public Value visitBlock(SysYParser.BlockContext ctx) {
        currentScope = new IRScope(currentScope);
        Value result = null;
        for (SysYParser.BlockItemContext item : ctx.blockItem()) {
            result = visit(item);
        }
        currentScope = currentScope.getParent();
        return result;
    }

    // =======================================================
    // Part 2: 变量定义与使用
    // =======================================================
    @Override
    public Value visitVarDef(SysYParser.VarDefContext ctx) {
        String varName = ctx.IDENT().getText();
        Value varAddr;

        if (currentScope.isGlobal()) {
            // 全局变量
            varAddr = module.addGlobalVariable(varName, i32, Option.empty()).unwrap();
            if (ctx.initVal() != null) {
                // 必须在编译期算出常量值
                Value initVal = visit(ctx.initVal());
                ((GlobalVariable) varAddr).setInitializer((Constant) initVal);
            } else {
                ((GlobalVariable) varAddr).setInitializer(i32.getConstant(0, true));
            }
        } else {
            // 局部变量
            varAddr = builder.buildAlloca((org.llvm4j.llvm4j.Type)i32,Option.of(varName));
            if (ctx.initVal() != null) {
                Value initVal = visit(ctx.initVal());
                builder.buildStore(varAddr, initVal); // 存入初始值
            }
        }
        
        currentScope.define(varName, varAddr);
        return varAddr;
    }

    @Override
    public Value visitLVal(SysYParser.LValContext ctx) {
        String varName = ctx.IDENT().getText();
        Value addr = currentScope.resolve(varName);

        if (addr == null) {
            throw new RuntimeException("Undefined variable: " + varName);
        }

        // 如果我们在赋值语句的左侧（如 a = 1;），需要返回地址本身
        if (needLValueAddress) {
            return addr;
        } 
        // 否则（作为表达式，如 b = a + 1;），需要加载它的值
        return builder.buildLoad(addr, Option.empty());
    }

    @Override
    public Value visitStmt(SysYParser.StmtContext ctx) {
        
        // 1. 赋值语句: lVal ASSIGN exp SEMICOLON
        if (ctx.ASSIGN() != null) {
            needLValueAddress = true;
            Value leftAddr = visit(ctx.lVal());
            needLValueAddress = false;
            
            // 注意：因为 exp 在整个 stmt 规则中最多出现一次，通常 ANTLR 会生成 ctx.exp()。
            // 如果你的 ANTLR 把它当成了列表，这里可能需要改成 ctx.exp(0)
            Value rightVal = visit(ctx.exp()); 
            
            return builder.buildStore(leftAddr, rightVal);
        }
        
        // 2. if语句: IF L_PAREN cond R_PAREN stmt (ELSE stmt)?
        else if (ctx.IF() != null) {
            BasicBlock trueBlock = context.newBasicBlock("if_true");
            BasicBlock falseBlock = context.newBasicBlock("if_false");
            BasicBlock nextBlock = context.newBasicBlock("if_next");

            Value cond = visit(ctx.cond());

            boolean hasElse = ctx.ELSE() != null;
            if (hasElse) {
                builder.buildConditionalBranch(cond, trueBlock, falseBlock);
            } else {
                builder.buildConditionalBranch(cond, trueBlock, nextBlock);
            }

            // --- 填充 True 分支 ---
            currentFunction.addBasicBlock(trueBlock); // 将块挂载到当前函数
            builder.positionAfter(trueBlock);
            visit(ctx.stmt(0)); // 访问 IF 后面的第一个 stmt
            builder.buildBranch(nextBlock);

            // --- 填充 False 分支 ---
            if (hasElse) {
                currentFunction.addBasicBlock(falseBlock);
                builder.positionAfter(falseBlock);
                visit(ctx.stmt(1)); // 访问 ELSE 后面的第二个 stmt
                builder.buildBranch(nextBlock);
            }

            // --- 切换到 Next 块 ---
            currentFunction.addBasicBlock(nextBlock);
            builder.positionAfter(nextBlock);
            
            return null;
        }
        
        // 3. while语句: WHILE L_PAREN cond R_PAREN stmt
        else if (ctx.WHILE() != null) {
            BasicBlock condBlock = context.newBasicBlock("while_cond");
            BasicBlock bodyBlock = context.newBasicBlock("while_body");
            BasicBlock nextBlock = context.newBasicBlock("while_next");

            builder.buildBranch(condBlock);
            loopStack.push(new LoopInfo(condBlock, nextBlock));

            // --- 条件判断块 ---
            currentFunction.addBasicBlock(condBlock);
            builder.positionAfter(condBlock);
            Value cond = visit(ctx.cond());
            builder.buildConditionalBranch(cond, bodyBlock, nextBlock);

            // --- 循环体块 ---
            currentFunction.addBasicBlock(bodyBlock);
            builder.positionAfter(bodyBlock);
            visit(ctx.stmt(0)); // 访问 while 内部的 stmt
            builder.buildBranch(condBlock);

            // 循环结束，弹出栈
            loopStack.pop();

            // --- 切换到 Next 块 ---
            currentFunction.addBasicBlock(nextBlock);
            builder.positionAfter(nextBlock);
            
            return null;
        }
        
        // 4. break语句: BREAK SEMICOLON
        else if (ctx.BREAK() != null) {
            LoopInfo currentLoop = loopStack.peek();
            return builder.buildBranch(currentLoop.nextBlock);
        }
        
        // 5. continue语句: CONTINUE SEMICOLON
        else if (ctx.CONTINUE() != null) {
            LoopInfo currentLoop = loopStack.peek();
            return builder.buildBranch(currentLoop.condBlock);
        }
        
        // 6. return语句: RETURN (exp)? SEMICOLON
        else if (ctx.RETURN() != null) {
            if (ctx.exp() != null) {
                Value retVal = visit(ctx.exp());
                return builder.buildReturn(Option.of(retVal));
            } else {
                return builder.buildReturn(Option.empty());
            }
        }
        
        // 7. 复合语句: block
        else if (ctx.block() != null) {
            return visit(ctx.block());
        }
        
        // 8. 表达式语句: (exp)? SEMICOLON
        else {
            if (ctx.exp() != null) {
                return visit(ctx.exp());
            }
            return null;
        }
    }
}