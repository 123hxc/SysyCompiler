
import java.util.List;
public class FunctionType extends Type{
    private Type returnType;
    private List<Type> paramsType;
    public FunctionType(Type returnType,List<Type> paramsType){
        this.returnType = returnType;
        this.paramsType = paramsType;
    }
    public Type getReturnType(){
        return returnType;
    }
    public List<Type> getParamsType(){
        return paramsType;
    }
    @Override
    public boolean isSameType(Type other){
        if(!(other instanceof FunctionType)){return false;}
        FunctionType o = (FunctionType)other;
        return this.returnType.isSameType(o.returnType) && this.paramsType.equals(o.getParamsType());
    }
}
