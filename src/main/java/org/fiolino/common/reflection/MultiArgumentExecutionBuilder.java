package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Handler;

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

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Parameters ");
            for (int i=0; i < values.length; i++) {
                if (i > 0) sb.append("; ");
                sb.append('#').append(i).append(": ").append(values[i]);
            }
            return sb.toString();
        }
    }

    private static final MethodHandle FILTER_TO_NULL, FILTER_FROM_NULL, CREATE_PCONTAINER, GET_PARAMS;

    static {
        MethodHandles.Lookup lookup = lookup();
        try {
            FILTER_TO_NULL = lookup.findStatic(lookup.lookupClass(), "returnToNull", methodType(Object.class, Object.class));
            FILTER_FROM_NULL = lookup.findStatic(lookup.lookupClass(), "returnFromNull", methodType(Object.class, Object.class));
            CREATE_PCONTAINER = lookup.findConstructor(ParameterContainer.class, methodType(void.class, Object[].class)).asType(methodType(Object.class, Object[].class));
            GET_PARAMS = lookup.findGetter(ParameterContainer.class, "values", Object[].class);
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    private final Map<?, ?> map;
    private final MethodHandle accessor, updater;
    private final OneTimeExecutionBuilder nullFallback;

    private <T, R> MultiArgumentExecutionBuilder(Map<?, ?> map, Function<T, R> targetFunction, MethodHandle targetHandle) {
        this.map = map;
        MethodType expectedType = targetHandle.type();
        int parameterCount = expectedType.parameterCount();
        assert parameterCount >= 1;

        MethodType setType = methodType(Object.class, Object.class, Object.class);
        MethodHandle update, setIfAbsent;
        try {
            update = publicLookup().bind(map, "put", setType);
            setIfAbsent = publicLookup().bind(map, "computeIfAbsent", methodType(Object.class, Object.class, Function.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }

        Class<?> returnType = expectedType.returnType();
        if (parameterCount > 1 || returnType.isPrimitive()) { // If void, then the function returns already Null.VALUE
            setIfAbsent = MethodHandles.insertArguments(setIfAbsent, 1, targetFunction);
        } else {
            Function<T, Object> nullSafe = x -> {
                R r = targetFunction.apply(x);
                return r == null ? Null.VALUE : r;
            };
            setIfAbsent = MethodHandles.insertArguments(setIfAbsent, 1, nullSafe);
            setIfAbsent = MethodHandles.filterReturnValue(setIfAbsent, FILTER_TO_NULL);
        }

        MethodHandle targetWithObject;
        Class<?>[] allParametersAreObjects = new Class<?>[parameterCount];
        Arrays.fill(allParametersAreObjects, Object.class);
        if (returnType == void.class) {
            update = MethodHandles.insertArguments(update, 1, Null.VALUE);
            targetWithObject = targetHandle.asType(methodType(void.class, allParametersAreObjects));
        } else {
            targetWithObject = targetHandle.asType(methodType(Object.class, allParametersAreObjects));
            if (!returnType.isPrimitive()) {
                // for Objects it needs null check
                update = MethodHandles.filterArguments(update, 1, FILTER_FROM_NULL);
            }
            update = MethodHandles.permuteArguments(update, setType, 1, 0);
            update = Methods.returnArgument(update, 0); // Returns null or other new value
        }
        if (parameterCount > 1) {
            update = collectArguments(update, returnType == void.class ? 0 : 1, parameterCount);
            setIfAbsent= collectArguments(setIfAbsent, 0, parameterCount);
        }
        update = MethodHandles.foldArguments(update, targetWithObject);

        setIfAbsent = setIfAbsent.asType(expectedType);
        update = update.asType(expectedType);

        if (parameterCount > 1 || expectedType.parameterType(0).isPrimitive()) {
            nullFallback = null;
        } else {
            OneTimeExecutionBuilder ex = new OneTimeExecutionBuilder(targetHandle, false);
            MethodHandle nullCheck = Methods.nullCheck().asType(methodType(boolean.class, expectedType.parameterType(0)));
            setIfAbsent = MethodHandles.guardWithTest(nullCheck, ex.getAccessor(), setIfAbsent);
            update = MethodHandles.guardWithTest(nullCheck, ex.getUpdater(), update);
            nullFallback = ex;
        }

        accessor = setIfAbsent;
        updater = update;
    }

    private static MethodHandle collectArguments(MethodHandle target, int pos, int argCount) {
        MethodHandle h = MethodHandles.filterArguments(target, pos, CREATE_PCONTAINER); // h now accepts an object array as the key and stores a ParameterContainer
        return h.asCollector(Object[].class, argCount);

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
            // publicLookup() does not work for local functions
            functionHandle = lookup().bind(target, "apply", methodType(Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }

        return new MultiArgumentExecutionBuilder(map, target, functionHandle);
    }

    static MultiArgumentExecutionBuilder createFor(MethodHandle target, Object... leadingValues) {
        return createFor(new ConcurrentHashMap<>(), target, leadingValues);
    }

    static MultiArgumentExecutionBuilder createFor(Map<?, ?> map, MethodHandle target, Object... leadingValues) {
        MethodHandle targetHandle = MethodHandles.insertArguments(target, 0, leadingValues);
        return new MultiArgumentExecutionBuilder(map, createFunction(target, leadingValues), targetHandle);
    }

    private static Function<?, ?> createFunction(MethodHandle handle, Object... leadingValues) {
        MethodType type = handle.type();
        MethodHandle h = type.returnType() == void.class ? MethodHandles.filterReturnValue(handle, MethodHandles.constant(Object.class, Null.VALUE)) : handle;
        int parameterCount = type.parameterCount();
        if (parameterCount > leadingValues.length + 1) {
            // Then it's multi-parameter
            h = h.asSpreader(Object[].class, parameterCount - leadingValues.length);
            h = MethodHandles.filterArguments(h, leadingValues.length, GET_PARAMS); // targetHandle now accepts a ParameterContainer as the last argument
        }
        return Methods.lambdafy(h, Function.class, leadingValues);
    }

    @Override
    public void reset() {
        map.clear();
        if (nullFallback != null) {
            nullFallback.reset();
        }
    }

    MutableCallSite preReset() {
        map.clear();
        return nullFallback == null ? null : nullFallback.preReset();
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
