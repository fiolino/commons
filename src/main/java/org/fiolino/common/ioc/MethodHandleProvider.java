package org.fiolino.common.ioc;

import javax.annotation.Nullable;
import java.lang.invoke.MethodType;

/**
 * Provider for a {@link java.lang.invoke.MethodHandle}.
 */
public interface MethodHandleProvider {

    /**
     * Implementors of this method will be able to create a MethodHandle of the given type.
     *
     * The implementor should call MethodHandleRegistry::register to register some handle.
     * This will only succeed if the handle's type is equal or convertible to the requested one.
     *
     * @param registry Here you can register the method handle
     * @param type The type that is asked for. Same as the one in the registry
     */
    void create(MethodHandleRegistry registry, MethodType type) throws NoSuchMethodException, IllegalAccessException, MismatchedMethodTypeException;

    /**
     * Makes a lambda out of the created handle.
     * Implementors can override this to add special initializers orr other behaviour to the created lambda.
     *
     * @param registry Here you can register the lambda
     * @param functionalInterface Which interface to implement
     * @param type The requested type
     * @param <T> The requested type
     */
    default <T> void lambdafy(LambdaRegistry<T> registry, Class<T> functionalInterface, MethodType type) throws NoSuchMethodException, IllegalAccessException, MismatchedMethodTypeException {
        create(registry, type);
    }
}
