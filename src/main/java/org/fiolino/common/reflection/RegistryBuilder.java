package org.fiolino.common.reflection;

import org.fiolino.data.annotation.Register;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import static java.lang.invoke.MethodHandles.publicLookup;

/**
 * This class creates {@link Registry} instances, which can be used to fetch statically caching instances of MethodHandles or functions.
 *
 * Created by kuli on 07.03.17.
 */
public final class RegistryBuilder {

    /**
     * Builds a Registry for a given target handle.
     *
     * @param target This handle will be called only once per parameter value.
     * @return A Registry holding handles with exactly the same type as the target type
     */
    public Registry buildFor(MethodHandle target) {
        if (target.type().parameterCount() == 0) {
            return new OneTimeExecutor(target);
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a lambda function that wraps the given one with a registry container.
     *
     * @param functionalType The type - must be a functional interface
     * @param function The wrapped function itself
     * @param <T> Type of the lambda
     * @return An instance of the lambda that will be called only once per argument values
     */
    public <T> T buildForFunctionalType(Class<T> functionalType, T function) {
        Method lambdaMethod = Methods.findLambdaMethod(functionalType);
        MethodHandle lambdaHandle;
        try {
            lambdaHandle = publicLookup().unreflect(lambdaMethod);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Cannot access " + lambdaMethod, ex);
        }
        lambdaHandle = lambdaHandle.bindTo(function);
        Registry reg = buildFor(lambdaHandle);
        MethodHandle wrapped = reg.getAccessor();
        return Methods.lambdafy(wrapped, functionalType);
    }
}
