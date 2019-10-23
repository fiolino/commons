package org.fiolino.common.ioc;

public interface LambdaRegistry<T> extends MethodHandleRegistry {
    void register(T lambda) throws MismatchedMethodTypeException;
}
