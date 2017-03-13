package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.publicLookup;

/**
 * A registry is used to construct registry handles for a certain target handle.
 *
 * Created by kuli on 08.03.17.
 */
public interface Registry extends Resettable {
    /**
     * Gets the handle that calls the cached service.
     *
     * The type of this handle is exactly the same as the one that was used for creating this registry.
     */
    MethodHandle getAccessor();

    /**
     * Gets the handle that calls the original service, overwriting any cached data.
     *
     * The type of this handle is exactly the same as the one that was used for creating this registry.
     */
    MethodHandle getUpdater();


    // Static factory methods

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
    static Registry buildFor(MethodHandle target, Object... leadingParameters) {
        int pCount = target.type().parameterCount() - leadingParameters.length;
        if (pCount < 0) {
            throw new IllegalArgumentException("Too many leading parameters for " + target);
        }
        if (leadingParameters.length > 0) {
            target = MethodHandles.insertArguments(target, 0, leadingParameters);
        }
        switch (pCount) {
            case 0:
                return new OneTimeExecutor(target, false);
            case 1:
                return OneArgumentRegistryBuilder.createFor(target, leadingParameters);
            default:
                throw new UnsupportedOperationException();
        }
    }

    /**
     * Creates a lambda function that wraps the given one with a registry container.
     *
     * @param functionalType The type - must be a functional interface
     * @param function The wrapped function itself
     * @param <T> Type of the lambda
     * @return A container of the lambda that will be called only once per argument values
     */
    static <T> LambdaRegistry<T> buildForFunctionalType(Class<T> functionalType, T function) {
        if (functionalType.equals(Function.class)) {
            return new RegistryMapper<>(OneArgumentRegistryBuilder.createFor((Function<?, ?>) function), functionalType);
        }
        Method lambdaMethod = Methods.findLambdaMethodOrFail(functionalType);
        if (lambdaMethod.getParameterCount() == 1) {
            return new RegistryMapper<T>(OneArgumentRegistryBuilder.createFor(functionalType, function), functionalType);
        }
        MethodHandle lambdaHandle;
        try {
            lambdaHandle = publicLookup().unreflect(lambdaMethod);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Cannot access " + lambdaMethod, ex);
        }
        return new RegistryMapper<>(buildFor(lambdaHandle, function), functionalType);
    }

    /**
     * Creates a lambda function that wraps the given one with a registry container.
     *
     * @param function The wrapped function itself
     * @param <T> Type of the lambda
     * @return A container of the lambda that will be called only once per argument values
     */
    static <T> LambdaRegistry<T> buildForFunctionalType(T function) {
        LambdaRegistry<T> registry = Methods.doInClassHierarchy(function.getClass(), c -> {
            for (Class<?> i : function.getClass().getInterfaces()) {
                if (Methods.findLambdaMethod(i).isPresent()) {
                    @SuppressWarnings("unchecked")
                    Class<T> castedInterface = (Class<T>) i;
                    return buildForFunctionalType(castedInterface, function);
                }
            }

            return null;
        });

        if (registry == null) {
            throw new IllegalArgumentException(function + " does not seem to be a lambda expression");
        }
        return registry;
    }
}
