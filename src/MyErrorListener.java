
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.BaseErrorListener;

public class MyErrorListener extends BaseErrorListener{
    private List<String> errorMessages = new ArrayList<>();
    private boolean hasError = false;
    @Override
    public void syntaxError(
        Recognizer<?,?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e){
            hasError = true;
            String errorMessage = "Error type A at Line "+line+":"+msg;
            errorMessages.add(errorMessage);
        }
    public boolean hasError(){
        return hasError;
    }    
    public void printLexerErrorInformation(){
        for(String errorMessage:errorMessages){
            System.err.println(errorMessage);
        }
    }
}
