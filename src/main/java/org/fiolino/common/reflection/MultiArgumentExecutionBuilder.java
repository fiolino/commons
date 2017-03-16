package org.fiolino.common.reflection;

import org.fiolino.common.util.Types;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Builder for targets with one or more arguments.
 *
 * The created handle is based on a Map instance which contains the parameters as its key and the executed result as the value.
 * Recurring calls with the same arguments then just return the cached value.
 *
 * If the target accepts more than one argument, then the arguments are being folded into an Object array, contained in my
 * inner class ParameterContainer.
 *
 * If the target accepts only one argument and this is nullable, then null keys are put into a private side instance of a
 * {@link OneTimeRegistryBuilder}. This is because the most common map implementation {@link ConcurrentHashMap} does not permit
 * null keys.
 *
 * If the value is nullable, then it's mapped to a constant replacement. The same holds true if the target does not return values.
 *
 * Created by kuli on 10.03.17.
 */
final class MultiArgumentExecutionBuilder implements Registry {

    private enum Null { VALUE }

    @SuppressWarnings("unused")
    private static Object returnToNull(Object input) {
        return input == Null.VALUE ? null : input;
    }

    private static Object returnFromNull(Object input) {
        return input == null ? Null.VALUE : input;
    }

    @SuppressWarnings("unused")
    private interface ParameterContainer<A> {}

    private static final MethodHandle FILTER_TO_NULL, FILTER_FROM_NULL;

    static {
        MethodHandles.Lookup lookup = lookup();
        try {
            FILTER_TO_NULL = lookup.findStatic(lookup.lookupClass(), "returnToNull", methodType(Object.class, Object.class));
            FILTER_FROM_NULL = lookup.findStatic(lookup.lookupClass(), "returnFromNull", methodType(Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    private final Map<?, ?> map;
    private final MethodHandle accessor, updater;
    private final OneTimeExecution nullFallback;

    private <T> MultiArgumentExecutionBuilder(Map<?, ?> map, Function<T, ?> targetFunction, MethodHandle targetHandle) {
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
        if (needsNullCheck(returnType, map)) { // If void, then the function already returns Null.VALUE
            Function<T, Object> nullSafe = x -> {
                Object r = targetFunction.apply(x);
                return returnFromNull(r);
            };
            setIfAbsent = MethodHandles.insertArguments(setIfAbsent, 1, nullSafe);
            setIfAbsent = MethodHandles.filterReturnValue(setIfAbsent, FILTER_TO_NULL);
        } else {
            setIfAbsent = MethodHandles.insertArguments(setIfAbsent, 1, targetFunction);
        }

        Class<?>[] allParametersAreObjects = new Class<?>[parameterCount];
        Arrays.fill(allParametersAreObjects, Object.class);
        int updateOffset;
        MethodType updateType;
        if (returnType == void.class) {
            update = MethodHandles.insertArguments(update, 1, Null.VALUE);
            updateOffset = 0;
            updateType = expectedType;
        } else {
            if (needsNullCheck(returnType, map)) {
                // for Objects it needs null check
                update = MethodHandles.filterArguments(update, 1, FILTER_FROM_NULL);
            }
            update = MethodHandles.permuteArguments(update, setType, 1, 0);
            update = Methods.returnArgument(update, 0); // Returns null or other new value
            updateOffset = 1;
            updateType = expectedType.insertParameterTypes(0, returnType);
        }
        Class<?>[] expectedParameters = expectedType.parameterArray();
        if (parameterCount > 1) {
            update = collectArguments(update, updateOffset, parameterCount, updateType.parameterArray());
            setIfAbsent= collectArguments(setIfAbsent, 0, parameterCount, expectedParameters);
        }
        setIfAbsent = convertArraysToContainers(setIfAbsent, 0, expectedParameters);
        setIfAbsent = setIfAbsent.asType(expectedType);

        update = convertArraysToContainers(update, updateOffset, expectedParameters);
        update = update.asType(updateType);
        update = MethodHandles.foldArguments(update, targetHandle);

        if (parameterCount > 1 || expectedType.parameterType(0).isArray() || !needsNullCheck(expectedType.parameterType(0), map)) {
            nullFallback = null;
        } else {
            OneTimeExecution ex = OneTimeExecution.createFor(targetHandle);
            MethodHandle nullCheck = Methods.nullCheck().asType(methodType(boolean.class, expectedType.parameterType(0)));
            setIfAbsent = MethodHandles.guardWithTest(nullCheck, ex.getAccessor(), setIfAbsent);
            update = MethodHandles.guardWithTest(nullCheck, ex.getUpdater(), update);
            nullFallback = ex;
        }

        if (targetHandle.isVarargsCollector()) {
            Class<?> parameterType = expectedType.parameterType(expectedType.parameterCount() - 1);
            setIfAbsent = setIfAbsent.asVarargsCollector(parameterType);
            update = update.asVarargsCollector(parameterType);
        }
        accessor = setIfAbsent;
        updater = update;
    }

    private static boolean needsNullCheck(Class<?> type, Map<?, ?> mapImplementation) {
        return !(type.isPrimitive() || mapImplementation instanceof HashMap);
    }

    private static MethodHandle convertArraysToContainers(MethodHandle target, int offset, Class<?>[] arguments) {
        MethodHandle h = target;
        for (int i=0; i < arguments.length; i++) {
            Class<?> p = arguments[i];
            if (p.isArray()) {
                MethodHandle toContainer = ArrayMappers.toContainer(p).asType(methodType(Object.class, Object.class));
                h = MethodHandles.filterArguments(h, i + offset, toContainer);
            }
        }

        return h;
    }

    private static MethodHandle collectArguments(MethodHandle target, int pos, int argCount, Class<?>[] parameters) {
        Class<?> p = commonClassOf(parameters, pos, argCount);
        MethodHandle toContainer = ArrayMappers.toContainer(p);
        MethodHandle h = MethodHandles.filterArguments(target, pos, toContainer.asType(toContainer.type().changeReturnType(target.type().parameterType(pos))));
        return h.asCollector(toContainer.type().parameterType(0), argCount);

    }

    private static Class<?> commonClassOf(Class<?>[] parameters, int start, int count) {
        Class<?> p = parameters[start];
        if (p.isPrimitive()) {
            for (int i = 1; i < count; i++) {
                Class<?> p2 = parameters[i + start];
                if (!p.equals(p2)) {
                    p = Object.class;
                    break;
                }
            }
        } else {
            p = Object.class;
        }
        return p;
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
        if (target != targetHandle && target.isVarargsCollector()) {
            targetHandle = targetHandle.asVarargsCollector(targetHandle.type().parameterType(targetHandle.type().parameterCount() - 1));
        }
        return new MultiArgumentExecutionBuilder(map, createFunction(target, leadingValues), targetHandle);
    }

    private static Function<?, ?> createFunction(MethodHandle handle, Object... leadingValues) {
        MethodType type = handle.type();
        MethodHandle h = type.returnType() == void.class ? MethodHandles.filterReturnValue(handle, MethodHandles.constant(Object.class, Null.VALUE)) : handle;
        int parameterCount = type.parameterCount();
        int start = leadingValues.length;
        // First check for transformed arrays
        for (int i = start; i < parameterCount; i++) {
            Class<?> p = type.parameterType(i);
            if (p.isArray()) {
                MethodHandle fromContainer = ArrayMappers.fromContainer(p);
                h = MethodHandles.filterArguments(h, i, fromContainer.asType(fromContainer.type().changeReturnType(p)));
            }
        }
        // Then fold the arguments, if necessary
        int count = parameterCount - start;
        if (count > 1) {
            // Then it's multi-parameter
            Class<?> p = commonClassOf(type.parameterArray(), start, count);
            MethodHandle fromContainer = ArrayMappers.fromContainer(p);
            h = h.asSpreader(fromContainer.type().returnType(), count);
            h = MethodHandles.filterArguments(h, start, fromContainer); // targetHandle now accepts a ParameterContainer as the last argument
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
        return nullFallback instanceof OneTimeRegistryBuilder ? ((OneTimeRegistryBuilder) nullFallback).preReset() : null;
    }

    @Override
    public MethodHandle getAccessor() {
        return accessor;
    }

    @Override
    public MethodHandle getUpdater() {
        return updater;
    }

    private static final class ArrayMappers {
        @SuppressWarnings("unused")
        private static final class ObjectParameterContainer implements ParameterContainer<Object[]> {
            final Object[] values;

            ObjectParameterContainer(Object[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof ObjectParameterContainer && Arrays.equals(values, ((ObjectParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
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

        @SuppressWarnings("unused")
        private static final class IntParameterContainer implements ParameterContainer<int[]> {
            final int[] values;

            IntParameterContainer(int[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof IntParameterContainer && Arrays.equals(values, ((IntParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
                return Arrays.hashCode(values);
            }

            @Override
            public String toString() {
                return Arrays.toString(values);
            }
        }

        @SuppressWarnings("unused")
        private static final class LongParameterContainer implements ParameterContainer<long[]> {
            final long[] values;

            LongParameterContainer(long[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof LongParameterContainer && Arrays.equals(values, ((LongParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
                return Arrays.hashCode(values);
            }

            @Override
            public String toString() {
                return Arrays.toString(values);
            }
        }

        @SuppressWarnings("unused")
        private static final class ByteParameterContainer implements ParameterContainer<byte[]> {
            final byte[] values;

            ByteParameterContainer(byte[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof ByteParameterContainer && Arrays.equals(values, ((ByteParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
                return Arrays.hashCode(values);
            }

            @Override
            public String toString() {
                return Arrays.toString(values);
            }
        }

        @SuppressWarnings("unused")
        private static final class ShortParameterContainer implements ParameterContainer<short[]> {
            final short[] values;

            ShortParameterContainer(short[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof ShortParameterContainer && Arrays.equals(values, ((ShortParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
                return Arrays.hashCode(values);
            }

            @Override
            public String toString() {
                return Arrays.toString(values);
            }
        }

        @SuppressWarnings("unused")
        private static final class CharParameterContainer implements ParameterContainer<char[]> {
            final char[] values;

            CharParameterContainer(char[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof CharParameterContainer && Arrays.equals(values, ((CharParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
                return Arrays.hashCode(values);
            }

            @Override
            public String toString() {
                return Arrays.toString(values);
            }
        }

        @SuppressWarnings("unused")
        private static final class FloatParameterContainer implements ParameterContainer<float[]> {
            final float[] values;

            FloatParameterContainer(float[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof FloatParameterContainer && Arrays.equals(values, ((FloatParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
                return Arrays.hashCode(values);
            }

            @Override
            public String toString() {
                return Arrays.toString(values);
            }
        }

        @SuppressWarnings("unused")
        private static final class DoubleParameterContainer implements ParameterContainer<double[]> {
            final double[] values;

            DoubleParameterContainer(double[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof DoubleParameterContainer && Arrays.equals(values, ((DoubleParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
                return Arrays.hashCode(values);
            }

            @Override
            public String toString() {
                return Arrays.toString(values);
            }
        }

        @SuppressWarnings("unused")
        private static final class BooleanParameterContainer implements ParameterContainer<boolean[]> {
            final boolean[] values;

            BooleanParameterContainer(boolean[] values) {
                this.values = values;
            }

            @Override
            public boolean equals(Object obj) {
                // Identity will never happen
                return obj instanceof BooleanParameterContainer && Arrays.equals(values, ((BooleanParameterContainer) obj).values);
            }

            @Override
            public int hashCode() {
                // Will always compare with other ParameterContainers
                return Arrays.hashCode(values);
            }

            @Override
            public String toString() {
                return Arrays.toString(values);
            }
        }

        private static final Map<Class<?>, MethodHandle> TO_CONTAINERS, FROM_CONTAINERS;

        static {
            TO_CONTAINERS = new HashMap<>();
            FROM_CONTAINERS = new HashMap<>();

            MethodHandles.Lookup lookup = lookup();
            Class[] classes = AccessController.doPrivileged((PrivilegedAction<Class[]>) () -> lookup.lookupClass().getDeclaredClasses());
            for (Class<?> pc : classes) {
                if (!ParameterContainer.class.isAssignableFrom(pc)) continue;
                Class<?> arrayType = Types.rawArgument(pc, ParameterContainer.class, 0, Types.Bounded.EXACT);
                if (!arrayType.isArray()) {
                    throw new InternalError(arrayType.getName());
                }
                Class componentType = arrayType.getComponentType();
                try {
                    MethodHandle toContainer = lookup.findConstructor(pc, methodType(void.class, arrayType));
                    TO_CONTAINERS.put(arrayType, toContainer);
                    TO_CONTAINERS.put(componentType, toContainer);

                    MethodHandle fromContainer = lookup.findGetter(pc, "values", arrayType);
                    FROM_CONTAINERS.put(arrayType, fromContainer);
                    FROM_CONTAINERS.put(componentType, fromContainer);

                } catch (IllegalAccessException | NoSuchMethodException | NoSuchFieldException ex) {
                    throw new InternalError(arrayType.getName(), ex);
                }
            }
        }

        static MethodHandle fromContainer(Class<?> type) {
            return FROM_CONTAINERS.getOrDefault(type, FROM_CONTAINERS.get(Object.class));
        }

        static MethodHandle toContainer(Class<?> type) {
            return TO_CONTAINERS.getOrDefault(type, TO_CONTAINERS.get(Object.class));
        }
    }
}
