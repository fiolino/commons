package org.fiolino.common.ioc;

import java.util.function.Function;
import java.util.function.Supplier;

public interface LambdaMapper {
    /**
     * Creates a provider which implements the given functional interface an calls the appropriate provider method or constructor.
     *
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies the parameter values and return type.
     * @param <T> The interface type
     * @return A lambda or proxy
     * @throws NoSuchProviderException If there is no provider that converts from the sources to the result type
     */
    <T> T createProviderFor(Class<T> functionalInterface);

    /**
     * Creates a provider which implements the given functional interface and calls the appropriate provider method or constructor.
     * This method can be used when the given interface is generic, so that the signature doesn't specify the argument and return type enough.
     *
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies parts of the parameter values.
     * @param returnType The expected return type
     * @param parameterTypes The expected parameter types; only the first ones that differ from the lambda method signature
     *                       must be given
     * @param <T> The interface type
     * @return A lambda or proxy
     * @throws NoSuchProviderException If there is no provider that converts from the sources to the result type
     */
    <T> T createProviderFor(Class<T> functionalInterface, Class<?> returnType, Class<?>... parameterTypes);

    /**
     * Creates a {@link Supplier} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs an empty public constructor
     * @param <T>  The type
     * @return The Supplier
     */
    default <T> Supplier<T> createSupplierFor(Class<T> type) {
        @SuppressWarnings("unchecked")
        Supplier<T> supplier = createProviderFor(Supplier.class, type);
        return supplier;
    }

    /**
     * Creates a {@link Function} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs a public constructor with exactly one argument
     *             of type argumentType, or an empty one as an alternative
     * @param <T>  The type
     * @return The Function that accepts the only parameter and returns a freshly intantiated object
     */
    default <T, P> Function<P, T> createFunctionFor(Class<T> type, Class<P> argumentType) {
        @SuppressWarnings("unchecked")
        Function<P, T> function = createProviderFor(Function.class, type, argumentType);
        return function;
    }

}
