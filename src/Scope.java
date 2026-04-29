
import java.util.Map;
import java.util.HashMap;
public class Scope {
    private Scope parent;
    private Map<String,Type>symbols = new HashMap<>();
    public Scope(Scope parent){
        this.parent = parent;
    }
    public Type resolve(String name){
        if(symbols.containsKey(name)){
            return symbols.get(name);
        }else if(parent!=null){
            return parent.resolve(name);
        }
        return null;
    }
    public boolean define(String name,Type type){
        if(symbols.containsKey(name)){
            return false;
        }else{
            symbols.put(name, type);
            return true;
        }
    }
    public boolean isDeclaredLocally(String name){
        return symbols.containsKey(name);
    }
}
