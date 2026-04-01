import java.util.List;
import java.io.IOException;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;

public class Main {

    public static void main(String[] args) throws IOException{
        if(args.length <1){
            System.err.println("input path is required");
        }
        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);
        sysYLexer.removeErrorListeners();
        MyErrorListener myErrorListener = new MyErrorListener();
        sysYLexer.addErrorListener(myErrorListener);
        List<? extends Token> myTokens = sysYLexer.getAllTokens();
        if(myErrorListener.hasError()){
            myErrorListener.printLexerErrorInformation();
        }else{
            Vocabulary vocabulary = sysYLexer.getVocabulary();
            for(Token i:myTokens){
                String tokenName = vocabulary.getSymbolicName(i.getType());
                if(i.getType() == SysYLexer.INTEGER_CONST){
                    String text = i.getText();
                    int value;
                    if(text.startsWith("0x")||text.startsWith("0X")){
                        value = Integer.parseInt(text.substring(2),16);
                    }else if(text.startsWith("0")&&text.length()>1){
                        value = Integer.parseInt(text,8);
                    }else{
                        value = Integer.parseInt(text);
                    }
                    System.err.println(tokenName+" "+value+" at Line "+i.getLine()+".");
                }else{
                    System.err.println(tokenName+" "+i.getText()+" at Line "+i.getLine()+".");
                }
            }
        }
    }
}