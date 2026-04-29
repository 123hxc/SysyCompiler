

public class IntType extends Type{
    @Override
    public boolean isSameType(Type other){
        return other instanceof IntType;
    }
    @Override
    public String toString(){
        return "INT";
    }
}
