public class ArrayType extends Type{
    private Type elementType;
    public ArrayType(Type elementType){
        this.elementType = elementType;
    }
    public Type getElementType(){
        return elementType;
    }
    @Override
    public boolean isSameType(Type other){
        if(!(other instanceof ArrayType)) return false;
        return this.elementType.isSameType(((ArrayType)other).getElementType());
    }
}
