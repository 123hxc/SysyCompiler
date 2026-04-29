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

    public IRVisitor() {
        // --- 预声明 SysY 运行时库函数 ---
        // int getint()
        FunctionType getintType = context.getFunctionType(i32, new Type[]{}, false);
        module.addFunction("getint", getintType);
        
        // int getch()
        FunctionType getchType = context.getFunctionType(i32, new Type[]{}, false);
        module.addFunction("getch", getchType);

        // void putint(int)
        FunctionType putintType = context.getFunctionType(voidType, new Type[]{i32}, false);
        module.addFunction("putint", putintType);

        // void putch(int)
        FunctionType putchType = context.getFunctionType(voidType, new Type[]{i32}, false);
        module.addFunction("putch", putchType);
        
        // 如果后续有数组，还需要 getarray 和 putarray
        // 获取 i32 的指针类型 (相当于 C 语言的 int*)
        PointerType i32Ptr = context.getInt32Type().getPointerTo();

        // int getarray(int[]) -> 传参其实是指针 i32*
        FunctionType getarrayType = context.getFunctionType(i32, new Type[]{i32Ptr}, false);
        module.addFunction("getarray", getarrayType);

        // void putarray(int, int[])
        FunctionType putarrayType = context.getFunctionType(voidType, new Type[]{i32, i32Ptr}, false);
        module.addFunction("putarray", putarrayType);

        // starttime 和 stoptime (SysY 评测系统专用的性能计时函数)
        FunctionType timeType = context.getFunctionType(voidType, new Type[]{}, false);
        module.addFunction("starttime", timeType);
        module.addFunction("stoptime", timeType);
    }

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

    @Override
    public Value visitNumber(SysYParser.NumberContext ctx) {
        String text = ctx.getText();
        int val;
        // 处理十六进制、八进制和十进制
        if (text.startsWith("0x") || text.startsWith("0X")) {
            val = Integer.parseInt(text.substring(2), 16);
        } else if (text.startsWith("0") && text.length() > 1) {
            val = Integer.parseInt(text, 8);
        } else {
            val = Integer.parseInt(text);
        }
        return i32.getConstant(val, true);
    }

    @Override
    public Value visitExp(SysYParser.ExpContext ctx) {

        // 1. 括号表达式: L_PAREN exp R_PAREN
        // 注意：函数调用里也有左括号，所以要通过 IDENT 是否为空来排除函数调用
        if (ctx.L_PAREN() != null && ctx.IDENT() == null) {
            return visit(ctx.exp(0));
        }

        // 2. 左值: lVal
        if (ctx.lVal() != null) {
            return visit(ctx.lVal());
        }

        // 3. 数值常量: number
        if (ctx.number() != null) {
            return visit(ctx.number());
        }

        // 4. 函数调用: IDENT L_PAREN funcRParams? R_PAREN
        if (ctx.IDENT() != null && ctx.L_PAREN() != null) {
            String targetFuncName = ctx.IDENT().getText();

            // 从模块中获取已经定义好的函数
            Function targetFunc = module.getFunction(targetFuncName).unwrap();

            // 解析并计算实参 (Arguments)
            int argCount = 0;
            if (ctx.funcRParams() != null) {
                argCount = ctx.funcRParams().param().size();
            }
            Value[] args = new Value[argCount];
            for (int k = 0; k < argCount; k++) {
                // visit 会自动 load 变量 a 的值
                args[k] = visit(ctx.funcRParams().param(k));
            }

            // 生成 call 指令
            // 如果函数返回类型是 void，不能给返回结果命名，传 Option.empty()
            // 如果是 int，可以命名为 calltmp
            // 这里为了简单兼容，统一传 Option.empty() 或根据实际情况判断
            return builder.buildCall(targetFunc, args, Option.empty());
        }

        // 5. 单目运算: unaryOp exp
        if (ctx.unaryOp() != null) {
            // 获取后面的表达式的值
            Value child = visit(ctx.exp(0));
            String op = ctx.unaryOp().getText();

            if (op.equals("-")) {
                // 负号: 用 0 减去该值
                return builder.buildIntSub(i32.getConstant(0, true), child, WrapSemantics.Unspecified,
                        Option.of("negtmp"));
            } else if (op.equals("!")) {
                // 逻辑非: 判断是否等于 0，再扩展为 i32
                Value cmp = builder.buildIntCompare(IntPredicate.Equal, child, i32.getConstant(0, true),
                        Option.of("notcmp"));
                return builder.buildZeroExt(cmp, i32, Option.of("notext"));
            }
            // 正号 "+" 直接返回原值
            return child;
        }

        // 6. 双目运算: exp (MUL|DIV|MOD|PLUS|MINUS) exp
        // 只要包含两个 exp 子节点，就是双目运算
        if (ctx.exp().size() == 2) {
            Value left = visit(ctx.exp(0));
            Value right = visit(ctx.exp(1));

            if (ctx.MUL() != null) {
                return builder.buildIntMul(left, right, WrapSemantics.Unspecified, Option.of("multmp"));
            } else if (ctx.DIV() != null) {
                return builder.buildSignedDiv(left, right, false, Option.of("divtmp"));
            } else if (ctx.MOD() != null) {
                return builder.buildSignedRem(left, right, Option.of("modtmp"));
            } else if (ctx.PLUS() != null) {
                return builder.buildIntAdd(left, right, WrapSemantics.Unspecified, Option.of("addtmp"));
            } else if (ctx.MINUS() != null) {
                return builder.buildIntSub(left, right, WrapSemantics.Unspecified, Option.of("subtmp"));
            }
        }
        // 在你的 visitExp (或负责双目运算的逻辑) 中补充比较运算：

        return null;
    }
    // =======================================================
    // Part 3: 条件表达式处理
    // =======================================================
    @Override
    public Value visitCond(SysYParser.CondContext ctx) {
        
        // 1. 基本情况：cond 只是一个 exp (例如 while(a) 或 if(1))
        if (ctx.exp() != null) {
            Value expVal = visit(ctx.exp());
            // 必须将 i32 转换为 i1 给 br 指令使用 (相当于判断 expVal != 0)
            return builder.buildIntCompare(IntPredicate.NotEqual, expVal, i32.getConstant(0, true), Option.of("tobool"));
        }

        // 2. 双目运算 (包含两个 cond 子节点)
        if (ctx.cond().size() == 2) {
            
            // ⚠️ 核心技巧：提取左右两边的值并统一为 i32 格式
            // 为什么这么做？因为你的 g4 规定两边也是 cond。
            // 如果一边是单纯的变量 (exp)，我们直接取它的 i32 值；
            // 如果一边是比较结果 (比如 a < b，返回的是 i1)，我们需要先把它扩展(ZeroExt)回 i32 才能参与外层运算。
            Value left = getCondAsI32(ctx.cond(0));
            // --- 关系运算：比较 i32，生成 i1 ---
            if (ctx.LT() != null) {
                Value right = getCondAsI32(ctx.cond(1));
                return builder.buildIntCompare(IntPredicate.SignedLessThan, left, right, Option.of("cmptmp"));
            } else if (ctx.LE() != null) {
                Value right = getCondAsI32(ctx.cond(1));
                return builder.buildIntCompare(IntPredicate.SignedLessEqual, left, right, Option.of("cmptmp"));
            } else if (ctx.GT() != null) {
                Value right = getCondAsI32(ctx.cond(1));
                return builder.buildIntCompare(IntPredicate.SignedGreaterThan, left, right, Option.of("cmptmp"));
            } else if (ctx.GE() != null) {
                Value right = getCondAsI32(ctx.cond(1));
                return builder.buildIntCompare(IntPredicate.SignedGreaterEqual, left, right, Option.of("cmptmp"));
            } else if (ctx.EQ() != null) {
                Value right = getCondAsI32(ctx.cond(1));
                return builder.buildIntCompare(IntPredicate.Equal, left, right, Option.of("cmptmp"));
            } else if (ctx.NEQ() != null) {
                Value right = getCondAsI32(ctx.cond(1));
                return builder.buildIntCompare(IntPredicate.NotEqual, left, right, Option.of("cmptmp"));
            }
            
            // --- 逻辑运算：先将 i32 压成 i1，再执行 AND/OR，生成 i1 ---
            else if (ctx.AND() != null) {
                Value lBool = builder.buildIntCompare(IntPredicate.NotEqual, left, i32.getConstant(0, true), Option.of("lbool"));

                BasicBlock rightBlock = context.newBasicBlock("and_right");
                BasicBlock endBlock = context.newBasicBlock("and_end");

                // 1. 分配一块内存存结果，默认先把左边的结果存进去
                Value resAddr = builder.buildAlloca(i1, Option.of("and_res"));
                builder.buildStore(resAddr, lBool); 
                
                // 2. 如果左边为真，跳去算右边；如果为假，直接跳到结束（短路发生）
                builder.buildConditionalBranch(lBool, rightBlock, endBlock);

                // 3. 计算右边的块
                currentFunction.addBasicBlock(rightBlock);
                builder.positionAfter(rightBlock);
                Value right = getCondAsI32(ctx.cond(1));
                Value rBool = builder.buildIntCompare(IntPredicate.NotEqual, right, i32.getConstant(0, true), Option.of("rbool"));
                builder.buildStore(resAddr, rBool); // 右边算完，更新结果
                builder.buildBranch(endBlock);

                // 4. 收尾块，读出最终结果
                currentFunction.addBasicBlock(endBlock);
                builder.positionAfter(endBlock);
                return builder.buildLoad(resAddr, Option.empty());
            } 
            
            // --- 逻辑或 (OR)：左边为真时，直接短路 ---
            else if (ctx.OR() != null) {
                Value lBool = builder.buildIntCompare(IntPredicate.NotEqual, left, i32.getConstant(0, true), Option.of("lbool"));

                BasicBlock rightBlock = context.newBasicBlock("or_right");
                BasicBlock endBlock = context.newBasicBlock("or_end");

                Value resAddr = builder.buildAlloca(i1, Option.of("or_res"));
                builder.buildStore(resAddr, lBool); 
                
                // ⚠️ 注意这里的条件反了：如果左边为真，直接短路跳到结束；为假才去算右边
                builder.buildConditionalBranch(lBool, endBlock, rightBlock);

                currentFunction.addBasicBlock(rightBlock);
                builder.positionAfter(rightBlock);
                Value right = getCondAsI32(ctx.cond(1));
                Value rBool = builder.buildIntCompare(IntPredicate.NotEqual, right, i32.getConstant(0, true), Option.of("rbool"));
                builder.buildStore(resAddr, rBool);
                builder.buildBranch(endBlock);

                currentFunction.addBasicBlock(endBlock);
                builder.positionAfter(endBlock);
                return builder.buildLoad(resAddr, Option.empty());
            }
        }
        return null;
    }

    // 辅助方法：确保拿到的操作数是 i32 类型
    private Value getCondAsI32(SysYParser.CondContext ctx) {
        if (ctx.exp() != null) {
            // 如果它本身就是算术表达式，直接返回 i32
            return visit(ctx.exp());
        } else {
            // 如果它是比较或逻辑运算，它的结果是 i1，我们需要将它用 0 填充扩展为 i32
            Value condVal = visit(ctx);
            return builder.buildZeroExt(condVal, i32, Option.of("zexttmp"));
        }
    }
    

    @Override
    public Value visitInitVal(SysYParser.InitValContext ctx) {
        // 必须返回 visit(exp) 的结果，不能让它默认返回 null
        return visit(ctx.exp());
    }

    // =======================================================
    // Part 1 & 3: 函数定义
    // =======================================================
    @Override
    public Value visitFuncDef(SysYParser.FuncDefContext ctx) {
        // 注意：这里根据你的 .g4 文件，函数名可能是 IDENT() 也可能是 ID()
        String funcName = ctx.IDENT().getText();
        boolean isVoid = ctx.funcType().getText().equals("void");
        Type retType = isVoid ? (Type) voidType : (Type) i32;

        // 1. 获取形参列表并构建 LLVM 函数签名
        int paramCount = 0;
        if (ctx.funcFParams() != null) {
            paramCount = ctx.funcFParams().funcFParam().size();
        }
        Type[] paramTypes = new Type[paramCount];
        for (int k = 0; k < paramCount; k++) {
            // 目前阶段假设参数都是 int 类型
            paramTypes[k] = i32;
        }

        FunctionType funcType = context.getFunctionType(retType, paramTypes, false);
        Function func = module.addFunction(funcName, funcType);
        this.currentFunction = func;

        BasicBlock entryBlock = context.newBasicBlock(funcName + "Entry");
        func.addBasicBlock(entryBlock);
        builder.positionAfter(entryBlock);

        currentScope = new IRScope(currentScope);

        // 2. 【核心修复】将形参存入内存并注册到符号表
        if (ctx.funcFParams() != null) {
            for (int k = 0; k < paramCount; k++) {
                SysYParser.FuncFParamContext paramCtx = ctx.funcFParams().funcFParam(k);
                String paramName = paramCtx.IDENT().getText();

                // 获取 LLVM 底层传进来的第 k 个参数值
                // 注意：如果 LLVM4J 报错找不到 getArgument，请尝试替换为 getParam(k)
                Value argValue = func.getParameter(k).unwrap();

                // 在栈上为该形参分配一块局部内存
                Value paramAddr = builder.buildAlloca(i32, Option.of(paramName + "_addr"));

                // 将传入的值存储到这块内存中
                builder.buildStore(paramAddr, argValue);

                // 将内存地址注册到当前作用域，这样后面遇到 return i 就能 resolve 到了！
                currentScope.define(paramName, paramAddr);
            }
        }

        // 3. 访问函数体
        visit(ctx.block());

        currentScope = currentScope.getParent();

        // 容错机制：不管源码有没有写 return，我们强行在最后补一个默认 return
        // 避免因为源码漏写 return 导致该基本块没有 Terminator，从而报 Invalid IR
        if (isVoid) {
            builder.buildReturn(Option.empty());
        } else {
            builder.buildReturn(Option.of(i32.getConstant(0, true)));
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
            varAddr = builder.buildAlloca((org.llvm4j.llvm4j.Type) i32, Option.of(varName));
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

            // 注意：如果你的 ANTLR 把它当成了列表，这里可能需要改成 ctx.exp(0)
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
            
            // 【核心修复】：只有当 True 分支没有提前 return/break 时，才生成跳往 next 的指令
            if (!isTerminator(ctx.stmt(0))) {
                builder.buildBranch(nextBlock);
            }

            // --- 填充 False 分支 ---
            if (hasElse) {
                currentFunction.addBasicBlock(falseBlock);
                builder.positionAfter(falseBlock);
                visit(ctx.stmt(1)); // 访问 ELSE 后面的第二个 stmt
                
                // 【核心修复】：同样检查 False 分支
                if (!isTerminator(ctx.stmt(1))) {
                    builder.buildBranch(nextBlock);
                }
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
            
            // 【核心修复】：只有当 while 内部没有发生 break/continue/return 时，才默认跳回 cond 块
            if (!isTerminator(ctx.stmt(0))) {
                builder.buildBranch(condBlock);
            }

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

    // =======================================================
    // 辅助方法：探测器 (用于解决基本块截断 / Double Terminator)
    // =======================================================
    /**
     * 分析一条语句，判断它是否一定会通过 break, continue 或 return 终止当前基本块。
     */
    private boolean isTerminator(SysYParser.StmtContext ctx) {
        if (ctx == null) return false;
        
        // 1. 如果它本身就是终止语句
        if (ctx.BREAK() != null || ctx.CONTINUE() != null || ctx.RETURN() != null) {
            return true;
        }
        
        // 2. 如果它是一个代码块 {}，检查它内部是否包含终止语句
        if (ctx.block() != null) {
            for (SysYParser.BlockItemContext item : ctx.block().blockItem()) {
                if (item.stmt() != null && isTerminator(item.stmt())) {
                    return true;
                }
            }
            return false;
        }
        
        // 3. 如果它是一个 if 语句，必须 True 和 False 两个分支都发生终止，它才算绝对终止
        if (ctx.IF() != null) {
            boolean trueBranch = isTerminator(ctx.stmt(0));
            boolean falseBranch = ctx.ELSE() != null && isTerminator(ctx.stmt(1));
            return trueBranch && falseBranch;
        }
        
        // 其他情况 (如普通赋值、单独的 while 等) 不保证终结
        return false;
    }
}