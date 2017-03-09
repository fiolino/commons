package org.fiolino.common.reflection;

import org.fiolino.data.annotation.Register;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import static java.lang.invoke.MethodHandles.lookup;
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
     * Implementation note:
     * This works for all kinds of MethodHandles if the resulting handle has no parameters,
     * but it will only work for direct method handles if there are some parameter types.
     *
     * @param target This handle will be called only once per parameter value.
     * @param leadingParameters Some values that are being added to the target at first
     * @return A Registry holding handles with exactly the same type as the target type minus the leading parameters
     */
    public Registry buildFor(MethodHandle target, Object... leadingParameters) {
        int pCount = target.type().parameterCount();
        if (pCount < leadingParameters.length) {
            throw new IllegalArgumentException("Too many leading parameters for " + target);
        }
        if (pCount == leadingParameters.length) {
            if (pCount > 0) {
                target = MethodHandles.insertArguments(target, 0, leadingParameters);
            }
            return new OneTimeExecutor(target);
        }

        Methods.createLambdaFactory(lookup(), )
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
