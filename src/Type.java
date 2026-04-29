

import type.VoidType;

public abstract class Type {
    public static final Type INT = new IntType();
    public static final Type VOID = VoidType.getInstance();
    //单例模式，因为有海量的INT类型，所有不必每次都创建，而且INT只需要检查是否是INT就行了
    public abstract boolean isSameType(Type other);
    public boolean isInt(){return this instanceof IntType;}
    public boolean isArray(){return this instanceof ArrayType;}
    public boolean isFunction(){return this instanceof FunctionType;}
}

