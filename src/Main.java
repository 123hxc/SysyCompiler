import java.io.IOException;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import symbol.*;
public class Main {

    public static void main(String[] args) throws IOException{
        if(args.length <1){
            System.err.println("input path is required");
        }
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        sysYParser.removeErrorListeners();
        MySyntaxErrorListener mySyntaxErrorListener = new MySyntaxErrorListener();
        sysYParser.addErrorListener(mySyntaxErrorListener);
        ParseTree tree = sysYParser.program();
        if(mySyntaxErrorListener.hasError()){
            mySyntaxErrorListener.printSyntaxErrorInformation();
        }else{
            //LAB2代码
            // FormatterVisitor formatterVisitor = new FormatterVisitor();
            // formatterVisitor.visit(tree);
            // System.out.println(formatterVisitor.getFormattedCode());
            Scope golabScope = new Scope(null);
            SemanticVisitor semanticVisitor = new SemanticVisitor(golabScope);
            semanticVisitor.visit(tree);
            if(semanticVisitor.hasError()){
                semanticVisitor.printSemanticErrorInformation();
                
            }else{
                System.err.println("No semantic errors in the program!");
            }
        }


        // LAB1代码
        // sysYLexer.removeErrorListeners();
        // MyErrorListener myErrorListener = new MyErrorListener();
        // sysYLexer.addErrorListener(myErrorListener);
        // List<? extends Token> myTokens = sysYLexer.getAllTokens();
        // if(myErrorListener.hasError()){
        //     myErrorListener.printLexerErrorInformation();
        // }else{
        //     Vocabulary vocabulary = sysYLexer.getVocabulary();
        //     for(Token i:myTokens){
        //         String tokenName = vocabulary.getSymbolicName(i.getType());
        //         if(i.getType() == SysYLexer.INTEGER_CONST){
        //             String text = i.getText();
        //             int value;
        //             if(text.startsWith("0x")||text.startsWith("0X")){
        //                 value = Integer.parseInt(text.substring(2),16);
        //             }else if(text.startsWith("0")&&text.length()>1){
        //                 value = Integer.parseInt(text,8);
        //             }else{
        //                 value = Integer.parseInt(text);
        //             }
        //             System.err.println(tokenName+" "+value+" at Line "+i.getLine()+".");
        //         }else{
        //             System.err.println(tokenName+" "+i.getText()+" at Line "+i.getLine()+".");
        //         }
        //     }
        // }
    }
}