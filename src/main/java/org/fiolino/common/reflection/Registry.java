package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

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


    // Static factory methods

    /**
     * Builds a Registry for a given target handle.
     *
     * If the target accepts exactly one argument of a certain enumerable type (currently boolean or Enum), then an optimized
     * accessor is created.
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
     * Use this if it's possible to fetch an int value from the given input type which is of some limited range.
     * If the mapped int gets too large, then this method would be very resource consuming. In general, there shouldn't be
     * too many gaps in between.
     *
     * You must specify an initial length of the mapped int range, and a maximum upper limit as a security range check.
     * If the mapped value is greater than the initial length, the underlying data structure gets expanded and a new MethodHandle
     * is created, which is a costly operation. Because of that, the initial length should at best cover the whole possible
     * range, but it shouldn't be too large to minimize resources.
     *
     * If the mapped value is greater than this maximum, a {@link LimitExceededException} is thrown.
     * If the maximum value is the same as the initial length, then this cache can't expand internally.
     *
     * If the maximum limit is -1, then there's no maximum check at all.
     *
     * @param target This handle will be called only once per parameter value.
     * @param toIntMapper This will map the first n arguments to some int value...
     * @param maximumRange ... which is lower than this.
     * @return A Registry holding handles with exactly the same type as the target type
     */
    static Registry buildForLimitedRange(MethodHandle target, MethodHandle toIntMapper, int initialSize, int maximumRange, int stepSize) {
        if (toIntMapper != null) {
            MethodType intMapperType = toIntMapper.type();
            MethodType targetType = target.type();

            if (intMapperType.returnType() != int.class) {
                throw new IllegalArgumentException("Must return an int");
            }
            if (intMapperType.parameterCount() > targetType.parameterCount()) {
                throw new IllegalArgumentException("Must have at most that many arguments as the target");
            }
        }

        return Reflection.createCache(target, toIntMapper, initialSize, maximumRange, stepSize);
    }

    /**
     * Builds a Registry for a given target handle where the only input parameter will be one of the given fixed values.
     *
     * This can be faster than the normal registry if the number of fixed values is not too large, but don't use this
     * for huge fixed value lists.
     *
     * @param target This handle will be called only once per parameter value.
     * @param fixedValues The parameter value must be one of these, otherwise an {@link IllegalArgumentException} will be thrown
     * @return A Registry holding handles with exactly the same type as the target type
     */
    static Registry buildForFixedValues(MethodHandle target, Object... fixedValues) {
        checkArgumentsForFixedValues(target, fixedValues);
        Class<?> valueType = target.type().parameterType(0);
        if (valueType == boolean.class) {
            return buildFor(target);
        }
        int n = fixedValues.length;
        Object[] sortedValues = Arrays.copyOf(fixedValues, n);
        Arrays.sort(sortedValues);
        Object typedArray;
        if (valueType.isPrimitive()) {
            typedArray = Array.newInstance(valueType, n);
            for (int i = 0; i < n; i++) {
                Array.set(typedArray, i, sortedValues[i]);
            }
        } else {
            valueType = Object.class;
            typedArray = sortedValues;
        }
        MethodHandle search;
        try {
            search = publicLookup().findStatic(Arrays.class, "binarySearch", methodType(int.class, typedArray.getClass(), valueType));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
        return returnFixedValuesRegistry(target, search, typedArray);
    }

    /**
     * Builds a Registry for a given target handle where the only input parameter will be one of the given fixed values.
     *
     * This can be faster than the normal registry if the number of fixed values is not too large, but don't use this
     * for huge fixed value lists.
     *
     * This will take a Comparator to find the right value.
     *
     * @param target This handle will be called only once per parameter value.
     * @param comparator Used to compare the given values
     * @param fixedValues The parameter value must be one of these, otherwise an {@link IllegalArgumentException} will be thrown
     * @return A Registry holding handles with exactly the same type as the target type
     */
    @SafeVarargs
    static <T> Registry buildForFixedValues(MethodHandle target, Comparator<? super T> comparator, T... fixedValues) {
        checkArgumentsForFixedValues(target, fixedValues);
        MethodHandle search;
        try {
            search = publicLookup().findStatic(Arrays.class, "binarySearch", methodType(int.class, Object[].class, Object.class, Comparator.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
        search = MethodHandles.insertArguments(search, 2, comparator);
        T[] sortedValues = Arrays.copyOf(fixedValues, fixedValues.length);
        Arrays.sort(sortedValues, comparator);
        return returnFixedValuesRegistry(target, search, sortedValues);
    }

    static void checkArgumentsForFixedValues(MethodHandle target, Object[] fixedValues) {
        if (fixedValues.length == 0) {
            throw new IllegalArgumentException("No values given");
        }
        if (target.type().parameterCount() != 1) {
            throw new IllegalArgumentException(target + " should accept exactly one parameter value.");
        }
    }

    static Registry returnFixedValuesRegistry(MethodHandle target, MethodHandle search, Object sortedValues) {
        MethodHandle toInt = search.bindTo(sortedValues).asType(methodType(int.class, target.type().parameterType(0)));

        int length = Array.getLength(sortedValues);
        return Reflection.createCache(target, toInt, length, length, 1);
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
            return new RegistryMapper<>(MultiArgumentExecutionBuilder.createFor(functionalType, function), functionalType);
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
        Class<?> functionClass = function.getClass();
        for (Class<?> i : functionClass.getInterfaces()) {
            if (Methods.findLambdaMethod(i).isPresent()) {
                @SuppressWarnings("unchecked")
                Class<T> castedInterface = (Class<T>) i;
                return buildForFunctionalType(castedInterface, function);
            }
        }
        throw new IllegalArgumentException(function + " does not seem to be a lambda expression");
    }
}
