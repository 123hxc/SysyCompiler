import org.llvm4j.llvm4j.Value;
import java.util.Map;
import java.util.HashMap;
public class IRScope {
    private IRScope parent;
    private Map<String,Value>symbols = new HashMap<>();
    public IRScope(IRScope parent){
        this.parent = parent;
    }
    public IRScope getParent(){
        return parent;
    }
    public Value resolve(String name){
        if(symbols.containsKey(name)){
            return symbols.get(name);
        }else if(parent!=null){
            return parent.resolve(name);
        }
        return null;
    }
    public boolean define(String name,Value value){
        if(symbols.containsKey(name)){
            return false;
        }else{
            symbols.put(name, value);
            return true;
        }
    }
    public boolean isGlobal() {
        return parent == null;
    }
    public boolean isDeclaredLocally(String name){
        return symbols.containsKey(name);
    }
}
