package org.fiolino.common.ioc;

import org.fiolino.common.reflection.Methods;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
     * @param lookup This was given by the caller, or it's just publicLookup() otherwise
     * @param callback The calling instantiator. You can ask for a local lookup to create the handle, or you can find
     *                 inner converters for special arguments.
     * @param type The type that is asked for
     * @return A handle of that type, or of a similar one that can be changed to using MethodHand::asType.
     */
    @Nullable
    MethodHandle createFor(MethodHandles.Lookup lookup, FactoryFinder callback, MethodType type) throws NoSuchMethodException, IllegalAccessException;

    /**
     * Makes a lambda out of the created handle.
     * Implementors can override this to add special initializers to the created lambda.
     *
     * @param lookup The lookup that was used to lambdafy this handle
     * @param callback The calling instantiator. You can ask for a local lookup to create the handle, or you can find
     *                 inner converters for special arguments.
     * @param functionalInterface Which interface to implement
     * @param methodType The requested type
     * @param <T> The requested type
     * @return The created lambda, or null if the type did not match
     */
    @Nullable
    default <T> T lambdafy(MethodHandles.Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle h = createFor(lookup, callback, methodType);
        return h == null ? null : Methods.lambdafy(lookup, h, functionalInterface);
    }
}
