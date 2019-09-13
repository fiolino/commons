package org.fiolino.common.ioc;

import org.fiolino.common.reflection.Methods;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * Provider for a {@link java.lang.invoke.MethodHandle}.
 */
public interface MethodHandleProvider {

    /**
     * Creates a MethodHandle of the given type.
     * If the type is just similar and can be converted, then it is accepted as well.
     * May return null to indicate that this provider does not support the requested type.
     *
     * @param callback The calling instantiator. You can ask for a local lookup to create the handle, or you can find
     *                 inner converters for special arguments.
     * @param type The type that is asked for
     * @return A handle of that type, or of a similar one that can be changed to using MethodHand::asType.
     */
    @Nullable
    MethodHandle createFor(Instantiator callback, MethodType type) throws NoSuchMethodException, IllegalAccessException;

    /**
     * Makes a lambda out of the created handle.
     * Implementors can override this to add special initializers to the created lambda.
     *
     * @param callback The calling instantiator. You can ask for a local lookup to create the handle, or you can find
     *                 inner converters for special arguments.
     * @param functionalInterface Which interface to implement
     * @param methodType The requested type
     * @param <T> The requested type
     * @return The created lambda, or null if the type did not match
     */
    @Nullable
    default <T> T lambdafy(Instantiator callback, Class<T> functionalInterface, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle h = createFor(callback, methodType);
        return h == null ? null : Methods.lambdafy(callback.getLookup(), h, functionalInterface);
    }
}
