package org.fiolino.common.ioc;

import org.fiolino.common.reflection.Methods;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

/**
 * Provider for a {@link java.lang.invoke.MethodHandle}.
 */
public interface MethodHandleProvider {

    /**
     * Creates a MethodHandle of the given type.
     * If the type is just similar and can be converted, then it is accepted as well.
     * May return null to indicate that this provider does not support the requested type.
     *
     * @param lookup The lookup can be used to create the handle
     * @param type The type that is asked for
     * @return A handle of that type, or of a similar one that can be changed to using MethodHand::asType.
     */
    @Nullable
    MethodHandle createFor(MethodHandles.Lookup lookup, MethodType type) throws NoSuchMethodException, IllegalAccessException;

    /**
     * Makes a lambda out of the created handle.
     * Implementors can override this to add special initializers to the created lambda.
     *
     * @param lookup Used to create the lambda
     * @param functionalInterface Which interface to implement
     * @param methodType The requested type
     * @param <T> The requested type
     * @return The created lambda, or empty() if the type did not match
     */
    default <T> Optional<T> lambdafy(MethodHandles.Lookup lookup, Class<T> functionalInterface, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        return Optional.ofNullable(createFor(lookup, methodType)).map(h -> Methods.lambdafy(lookup, h, functionalInterface));
    }
}
