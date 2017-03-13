package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 10.03.17.
 */
final class MultiArgumentExecutionBuilder implements Registry {

    private enum Null { VALUE }

    @SuppressWarnings("unused")
    private static Object returnToNull(Object input) {
        return input == Null.VALUE ? null : input;
    }

    @SuppressWarnings("unused")
    private static Object returnFromNull(Object input) {
        return input == null ? Null.VALUE : input;
    }

    @SuppressWarnings("unused")
    private static final class ParameterContainer {
        final Object[] values;

        ParameterContainer(Object[] values) {
            this.values = values;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ParameterContainer && Arrays.equals(values, ((ParameterContainer) obj).values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    private static final MethodHandle FILTER_TO_NULL, FILTER_FROM_NULL, CREATE_PCONTAINER, GET_PARAMS;

    static {
        MethodHandles.Lookup lookup = lookup();
        try {
            FILTER_TO_NULL = lookup.findStatic(lookup.lookupClass(), "returnToNull", methodType(Object.class, Object.class));
            FILTER_FROM_NULL = lookup.findStatic(lookup.lookupClass(), "returnFromNull", methodType(Object.class, Object.class));
            CREATE_PCONTAINER = lookup.findConstructor(ParameterContainer.class, methodType(void.class, Object[].class));
            GET_PARAMS = lookup.findGetter(ParameterContainer.class, "values", Object[].class);
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    private final Map<?, ?> map;
    private final MethodHandle accessor, updater;
    private final Resettable nullFallback;

    private <T, R> MultiArgumentExecutionBuilder(Map<?, ?> map, Function<T, R> targetFunction, MethodHandle targetHandle) {
        this.map = map;
        MethodType expectedType = targetHandle.type();
        assert expectedType.parameterCount() >= 1;
        boolean isMulti = expectedType.parameterCount() > 1;

        MethodType setType = methodType(Object.class, Object.class, Object.class);
        MethodHandle set, getOrSet;
        try {
            set = publicLookup().bind(map, "put", setType);
            getOrSet = publicLookup().bind(map, "computeIfAbsent", methodType(Object.class, Object.class, Function.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }

        MethodHandle reversedSet = MethodHandles.permuteArguments(set, setType, 1, 0);

        if (expectedType.returnType().isPrimitive()) { // If void, then the function returns already Null.VALUE
            getOrSet = MethodHandles.insertArguments(getOrSet, 1, targetFunction);
        } else {
            Function<T, Object> nullSafe = x -> {
                R r = targetFunction.apply(x);
                return r == null ? Null.VALUE : r;
            };
            getOrSet = MethodHandles.insertArguments(getOrSet, 1, nullSafe);
            getOrSet = MethodHandles.filterReturnValue(getOrSet, FILTER_TO_NULL);

            reversedSet = MethodHandles.filterArguments(reversedSet, 0, FILTER_FROM_NULL);
            reversedSet = Methods.returnArgument(reversedSet, 0); // Returns null or other new value
        }
        MethodHandle targetWithObject = targetHandle.asType(methodType(Object.class, Object.class));
        set = MethodHandles.foldArguments(reversedSet, targetWithObject);

        getOrSet = getOrSet.asType(expectedType);
        set = set.asType(expectedType);
        Class<?> argumentType = expectedType.parameterType(0);
        if (argumentType.isPrimitive()) {
            nullFallback = null;
        } else {
            OneTimeExecutionBuilder ex = new OneTimeExecutionBuilder(targetHandle, false);
            MethodHandle nullCheck = Methods.nullCheck().asType(methodType(boolean.class, argumentType));
            getOrSet = MethodHandles.guardWithTest(nullCheck, ex.getAccessor(), getOrSet);
            set = MethodHandles.guardWithTest(nullCheck, ex.getUpdater(), set);
            nullFallback = ex;
        }

        accessor = getOrSet;
        updater = set;
    }

    static <T> MultiArgumentExecutionBuilder createFor(Class<T> lambdaType, T instance) {
        return createFor(new ConcurrentHashMap<>(), lambdaType, instance);
    }

    static <T> MultiArgumentExecutionBuilder createFor(Map<?, ?> map, Class<T> lambdaType, T instance) {
        MethodHandles.Lookup lookup = publicLookup().in(lambdaType);
        Method method = Methods.findLambdaMethodOrFail(lambdaType);
        MethodHandle handle;
        try {
            handle = lookup.unreflect(method);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(method + " not accessible.", ex);
        }

        return new MultiArgumentExecutionBuilder(map, createFunction(handle, instance), handle.bindTo(instance));
    }

    static MultiArgumentExecutionBuilder createFor(Function<?, ?> target) {
        return createFor(new ConcurrentHashMap<>(), target);
    }

    static MultiArgumentExecutionBuilder createFor(Map<?, ?> map, Function<?, ?> target) {
        MethodHandle functionHandle;
        try {
            functionHandle = publicLookup().bind(target, "apply", methodType(Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }

        return new MultiArgumentExecutionBuilder(map, target, functionHandle);
    }

    static MultiArgumentExecutionBuilder createFor(MethodHandle target, Object... leadingValues) {
        return createFor(new ConcurrentHashMap<>(), target, leadingValues);
    }

    static MultiArgumentExecutionBuilder createFor(Map<?, ?> map, MethodHandle target, Object... leadingValues) {
        if (target.type().parameterCount() != 1) {
            throw new IllegalArgumentException(target + " must accept exactly one parameter");
        }
        return new MultiArgumentExecutionBuilder(map, createFunction(target, leadingValues), target);
    }

    private static Function<?, ?> createFunction(MethodHandle handle, Object... leadingValues) {
        return Methods.lambdafy(handle, Function.class, leadingValues);
    }

    @Override
    public void reset() {
        map.clear();
        if (nullFallback != null) {
            nullFallback.reset();
        }
    }

    @Override
    public MethodHandle getAccessor() {
        return accessor;
    }

    @Override
    public MethodHandle getUpdater() {
        return updater;
    }
}
