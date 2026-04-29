package type;

import Type;

public class VoidType extends Type{
    private static final VoidType instance = new VoidType();
    private VoidType(){}
    public static VoidType getInstance(){
        return instance;
    }
    @Override 
    public boolean isSameType(Type other){
        return other instanceof VoidType;
    }
    @Override
    public String toString(){
        return "void";
    }
}
