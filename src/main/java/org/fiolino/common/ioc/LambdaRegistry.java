package org.fiolino.common.ioc;

public interface LambdaRegistry<T> extends MethodHandleRegistry {
    /**
     * Gets the type that shall be registered.
     */
    Class<T> functionalType();

    /**
     * Register the created lambda instance here.
     *
     * @param lambda The instance that was created
     * @throws MismatchedMethodTypeException if the lambda is not of the correct type
     */
    void register(T lambda) throws MismatchedMethodTypeException;
}
