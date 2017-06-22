package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
     * @param target This handle will be called only once per parameter value.
     * @param leadingParameters Some values that are being added to the target at first
     * @return A Registry holding handles with exactly the same type as the target type minus the leading parameters
     */
    static Registry buildFor(MethodHandle target, Object... leadingParameters) {
        return Reflection.buildFor(target, leadingParameters);
    }

    /**
     * Builds a Registry for a given target handle where the input parameters can be mapped to an int of limited range.
     *
     * @param target This handle will be called only once per parameter value.
     * @param toIntMapper This will map the first n arguments to some int value...
     * @param maximumRange ... which is lower than this.
     * @return A Registry holding handles with exactly the same type as the target type
     */
    static Registry buildForLimitedRange(MethodHandle target, MethodHandle toIntMapper, int maximumRange) {
        MethodType intMapperType = toIntMapper.type();
        MethodType targetType = target.type();

        assert intMapperType.returnType() == int.class : "Must return an int";
        assert intMapperType.parameterCount() <= targetType.parameterCount() : "Must have at most that many arguments as the target";
        for (int i=intMapperType.parameterCount() - 1; i >= 0; i--) {
            assert intMapperType.parameterType(i).equals(target.type().parameterType(i)) : "Parameter #" + i + " must be the same";
        }

        return new Reflection.ParameterToIntMappingRegistry(target, h -> MethodHandles.collectArguments(h, 0, toIntMapper), maximumRange);
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
        if (Function.class.isAssignableFrom(functionalType)) {
            return new RegistryMapper<>(MultiArgumentExecutionBuilder.createFor((Function<?, ?>) function), functionalType);
        }
        Method lambdaMethod = Methods.findLambdaMethodOrFail(functionalType);
        if (lambdaMethod.getParameterCount() == 1) {
            return new RegistryMapper<T>(MultiArgumentExecutionBuilder.createFor(functionalType, function), functionalType);
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
