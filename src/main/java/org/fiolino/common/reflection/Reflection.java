package org.fiolino.common.reflection;

import java.lang.invoke.*;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Static class for various reflection based methods.
 *
 * Created by Kuli on 6/17/2016.
 */
final class Reflection {

    private static final MethodHandle SYNC, CONSTANT_HANDLE_FACTORY, DROP_ARGUMENTS, SET_TARGET;

    static {
        try {
            SYNC = publicLookup().findStatic(MutableCallSite.class, "syncAll", methodType(void.class, MutableCallSite[].class));
            CONSTANT_HANDLE_FACTORY = publicLookup().findStatic(MethodHandles.class, "constant",
                    methodType(MethodHandle.class, Class.class, Object.class));
            DROP_ARGUMENTS = publicLookup().findStatic(MethodHandles.class, "dropArguments",
                    methodType(MethodHandle.class, MethodHandle.class, int.class, Class[].class));
            SET_TARGET = publicLookup().findVirtual(CallSite.class, "setTarget",
                    methodType(void.class, MethodHandle.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

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
        switch (pCount) {
            case 0:
                // No args, so just one simple instance
                target = insertLeadingArguments(target, leadingParameters);
                return new OneTimeRegistryBuilder(target, false);
            case 1:
                // Maybe just some type with few possible values?
                Class<?> singleArgumentType = target.type().parameterType(leadingParameters.length);
                if (singleArgumentType.isEnum() && singleArgumentType.getEnumConstants().length <= 64) {
                    // Then it can be mapped directly
                    target = insertLeadingArguments(target, leadingParameters);
                    return buildForEnumParameter(target, singleArgumentType.asSubclass(Enum.class));
                }
                if (singleArgumentType == boolean.class) {
                    target = insertLeadingArguments(target, leadingParameters);
                    return new BooleanMappingCache(target);
                }
            default:
                return MultiArgumentExecutionBuilder.createFor(target, leadingParameters);
        }
    }

    private static MethodHandle insertLeadingArguments(MethodHandle target, Object[] leadingParameters) {
        if (leadingParameters.length > 0) {
            target = MethodHandles.insertArguments(target, 0, leadingParameters);
        }
        return target;
    }

    private static <E extends Enum<E>> Cache buildForEnumParameter(MethodHandle target, Class<E> enumType) {
        MethodHandle ordinal;
        try {
            ordinal = publicLookup().findVirtual(enumType, "ordinal", methodType(int.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("ordinal", ex);
        }
        int length = enumType.getEnumConstants().length;
        return createCache(target, ordinal, length, length, 1);
    }

    static Cache createCache(MethodHandle target, MethodHandle filter, int initialSize, int maximum, int stepSize) {
        if (initialSize < 0) {
            throw new IllegalArgumentException("" + initialSize);
        }
        if (maximum >= 0) {
            if (initialSize > maximum) {
                throw new IllegalArgumentException(initialSize + " > " + maximum);
            }
            if (initialSize == maximum) {
                return new ParameterToIntMappingCache(target, filter, maximum);
            }
        }
        return new ExpandableParameterToIntMappingCache(target, filter, initialSize, maximum, stepSize);
    }

    private static class BooleanMappingCache implements Cache {
        private final OneTimeExecution falseExecution, trueExecution;
        private final CallSite callSite;

        BooleanMappingCache(MethodHandle target) {
            if (target.type().parameterCount() != 1 || target.type().parameterType(0) != boolean.class) {
                throw new IllegalArgumentException(target.toString());
            }
            falseExecution = OneTimeExecution.createFor(target);
            trueExecution = OneTimeExecution.createFor(target);
            MethodHandle decider = createDecider(Registry::getAccessor);
            callSite = new ConstantCallSite(decider);
        }

        private MethodHandle createDecider(Function<Registry, MethodHandle> type) {
            return MethodHandles.guardWithTest(MethodHandles.identity(boolean.class),
                    type.apply(trueExecution), type.apply(falseExecution));
        }

        @Override
        public CallSite getCallSite() {
            return callSite;
        }

        @Override
        public void reset() {
            falseExecution.reset();
            trueExecution.reset();
        }

        @Override
        public MethodHandle getUpdater() {
            return createDecider(Registry::getUpdater);
        }
    }

    /**
     * Adds the index of the failed insertion to it.
     *
     * @param exceptionHandler (ArrayIndexOutOfBoundsException,int,&lt;valueType[]&gt;)MethodHandle
     * @return (ArrayIndexOutOfBoundsException,&lt;valueTypes&gt;)MethodHandle
     */
    static MethodHandle modifyExceptionHandler(MethodHandle exceptionHandler, MethodHandle filter, MethodType targetType) {
        return modifyExceptionHandler(exceptionHandler, filter, targetType, new Semaphore(1));
    }

    /**
     * Adds the index of the failed insertion to it.
     *
     * @param exceptionHandler (ArrayIndexOutOfBoundsException,int,&lt;valueType[]&gt;)MethodHandle
     * @return (ArrayIndexOutOfBoundsException,&lt;valueTypes&gt;)MethodHandle
     */
    static MethodHandle modifyExceptionHandler(MethodHandle exceptionHandler, MethodHandle filter, MethodType targetType, Semaphore semaphore) {
        Class<?>[] targetParameters = targetType.parameterArray();
        int targetArgCount = targetParameters.length;
        int filterArgCount = filter == null ? 1 : filter.type().parameterCount();
        assert filterArgCount <= targetArgCount;
        Class<?>[] innerParameters = new Class<?>[targetArgCount+1];
        innerParameters[0] = ArrayIndexOutOfBoundsException.class;
        System.arraycopy(targetParameters, 0, innerParameters, 1, targetArgCount);
        int[] argumentIndexes = new int[filterArgCount + targetArgCount + 1];
        for (int i=1; i<=targetArgCount; i++) {
            if (i <= filterArgCount) {
                argumentIndexes[i] = i;
            }
            argumentIndexes[filterArgCount + i] = i;
        }

        MethodHandle h = applyFilter(exceptionHandler, filter, 1);
        h = h.asCollector(Object[].class, targetArgCount);
        Class<?>[] allArguments = h.type().parameterArray();
        System.arraycopy(targetParameters, 0, allArguments, 1, filterArgCount);
        System.arraycopy(targetParameters, 0, allArguments, filterArgCount+1, targetArgCount);
        allArguments[0] = ArrayIndexOutOfBoundsException.class;
        h = h.asType(methodType(MethodHandle.class, allArguments));

        h = MethodHandles.permuteArguments(h, methodType(MethodHandle.class, innerParameters), argumentIndexes);
        return Methods.synchronizeWith(h, semaphore);
    }

    private static MethodHandle applyFilter(MethodHandle target, MethodHandle filter, int pos) {
        if (filter == null) return target;
        return MethodHandles.collectArguments(target, pos, filter);
    }

    /**
     * A Registry implementation that fetches accessor and updater from arrays of MethodHandles.
     */
    private static class ParameterToIntMappingCache implements Cache {
        private final OneTimeExecution[] executions;
        private final CallSite callSite;
        private final MethodHandle updater;

        /**
         * Creates the caching handle factory.
         *
         * @param target Use this as the called handle.
         * @param maximum The initial size of the array; this should be the expected maximum length of an average key set
         */
        ParameterToIntMappingCache(MethodHandle target, MethodHandle filter, int maximum) {
            checkTargetAndFilter(target, filter);
            this.executions = new OneTimeExecution[maximum];
            MethodHandles.Lookup lookup = lookup();
            MethodHandle exceptionHandler;
            try {
                exceptionHandler = lookup.findStatic(lookup.lookupClass(), "outOfBounds", methodType(MethodHandle.class, Throwable.class, int.class, Object[].class, int.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError(ex);
            }
            exceptionHandler = MethodHandles.insertArguments(exceptionHandler, 3, maximum - 1);
            exceptionHandler = modifyExceptionHandler(exceptionHandler, filter, target.type());
            MethodHandle primary = createFullHandle(target, executions, filter, exceptionHandler, Registry::getAccessor);
            callSite = new ConstantCallSite(primary);
            updater = createFullHandle(target, executions, filter, exceptionHandler, Registry::getUpdater);
        }

        @Override
        public CallSite getCallSite() {
            return callSite;
        }

        @SuppressWarnings("unused")
        private static MethodHandle outOfBounds(Throwable t, int index, Object[] values, int maximum) {
            throw new LimitExceededException("Argument value " + Arrays.toString(values) + " resolves to " + index + " which is beyond 0.." + maximum, t);
        }

        @Override
        public void reset() {
            for (Resettable r : executions) {
                r.reset();
            }
        }

        @Override
        public MethodHandle getUpdater() {
            return updater;
        }
    }

    private static MethodHandle[] createAccessors(MethodHandle target, OneTimeExecution[] executions, Function<Registry, MethodHandle> type) {
        int length = executions.length;
        MethodHandle[] accessors = new MethodHandle[length];
        for (int i=0; i < length; i++) {
            OneTimeExecution x = executions[i];
            if (x == null) {
                x = OneTimeExecution.createFor(target);
                executions[i] = x;
            }
            accessors[i] = type.apply(x);
        }

        return accessors;
    }

    private static MethodHandle createAccessingHandle(MethodHandle target, OneTimeExecution[] executions, MethodHandle filter, Function<Registry, MethodHandle> type) {
        MethodHandle[] accessors = createAccessors(target, executions, type);
        MethodHandle getFromArray = MethodHandles.arrayElementGetter(MethodHandle[].class).bindTo(accessors);
        MethodHandle filtered = applyFilter(getFromArray, filter, 0);
        return Methods.acceptThese(filtered, target.type().parameterArray());
    }

    private static MethodHandle createFullHandle(MethodHandle target, OneTimeExecution[] executions, MethodHandle filter, MethodHandle exceptionHandler, Function<Registry, MethodHandle> type) {
        MethodHandle primary = createAccessingHandle(target, executions, filter, type);
        MethodHandle handleGetter = MethodHandles.catchException(primary, ArrayIndexOutOfBoundsException.class, exceptionHandler);
        MethodHandle invoker = MethodHandles.exactInvoker(target.type());
        return MethodHandles.foldArguments(invoker, handleGetter);
    }

    private static void checkTargetAndFilter(MethodHandle target, MethodHandle filter) {
        if (filter != null && target.type().parameterCount() < filter.type().parameterCount()) {
            throw new IllegalArgumentException("Filter " + filter + " accepts more arguments than " + target);
        }
    }

    private static class ExpandableParameterToIntMappingCache implements Cache {
        private final MethodHandle target, filter;
        private final CallSite accessorCallSite, updaterCallSite;
        private final int maximumLength, stepSize;
        private final MethodHandle accessorExceptionHandler, updaterExceptionHandler;
        private final Semaphore semaphore;
        private OneTimeExecution[] executions;

        /**
         * Creates the caching handle factory.
         *
         * @param target Use this as the called handle.
         * @param initialMaximum The initial size of the array; this should be the expected maximum length of an average key set
         * @param finalMaximum This is the absolute maximum size; if this exceeds, an exception is thrown. Use this to limit
         *                     the amount of stored keys to avoid memory and performance issues
         */
        ExpandableParameterToIntMappingCache(MethodHandle target, MethodHandle filter,
                                             CallSite accessorCallSite, CallSite updaterCallSite,
                                             int initialMaximum, int finalMaximum, int stepSize) {
            if (finalMaximum >= 0 && finalMaximum < initialMaximum) {
                throw new IllegalArgumentException(finalMaximum + " < " + initialMaximum);
            }
            checkTargetAndFilter(target, filter);
            this.accessorCallSite = accessorCallSite;
            this.updaterCallSite = updaterCallSite;
            this.target = target;
            this.filter = filter;
            this.maximumLength = finalMaximum;
            this.stepSize = stepSize;
            this.semaphore = new Semaphore(1);
            expandExecutions(0, initialMaximum);

            MethodHandle exceptionHandler;
            try {
                exceptionHandler = lookup().bind(this, "outOfBounds", methodType(MethodHandle.class, ArrayIndexOutOfBoundsException.class, int.class, Object[].class, Function.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError(ex);
            }
            MethodHandle specificExceptionHandler = MethodHandles.insertArguments(exceptionHandler, 3, (Function<Registry, MethodHandle>) Registry::getAccessor);
            this.accessorExceptionHandler = modifyExceptionHandler(specificExceptionHandler, filter, target.type(), semaphore);
            specificExceptionHandler = MethodHandles.insertArguments(exceptionHandler, 3, (Function<Registry, MethodHandle>) Registry::getUpdater);
            this.updaterExceptionHandler = modifyExceptionHandler(specificExceptionHandler, filter, target.type(), semaphore);

            updateCallSites();
        }

        /**
         * Creates the caching handle factory.
         *
         * @param target Use this as the called handle.
         * @param initialMaximum The initial size of the array; this should be the expected maximum length of an average key set
         * @param finalMaximum This is the absolute maximum size; if this exceeds, an exception is thrown. Use this to limit
         *                     the amount of stored keys to avoid memory and performance issues
         */
        ExpandableParameterToIntMappingCache(MethodHandle target, MethodHandle filter, int initialMaximum, int finalMaximum, int stepSize) {
            this(target, filter, new MutableCallSite(target.type()), new MutableCallSite(target.type()), initialMaximum, finalMaximum, stepSize);
        }

        private int expandExecutions(int length) {
            int l = executions.length;
            expandExecutions(l, length);
            return l;
        }

        private void expandExecutions(int start, int length) {
            if (start == 0) {
                executions = new OneTimeExecution[length];
            } else if (start < length) {
                executions = Arrays.copyOf(executions, length);
            } else {
                return;
            }
            for (int i=start; i < length; i++) {
                executions[i] = OneTimeExecution.createFor(target);
            }
        }

        @Override
        public CallSite getCallSite() {
            return accessorCallSite;
        }

        @Override
        public void reset() {
            semaphore.acquireUninterruptibly();
            try {
                for (Resettable r : executions) {
                    r.reset();
                }
            } finally {
                semaphore.release();
            }
        }

        @Override
        public MethodHandle getUpdater() {
            return updaterCallSite.dynamicInvoker();
        }

        @SuppressWarnings("unused")
        private MethodHandle outOfBounds(ArrayIndexOutOfBoundsException ex, int index, Object[] arguments, Function<Registry, MethodHandle> accessType) {
            if (index < 0) {
                throw new LimitExceededException("" + index);
            }
            if (index < executions.length) {
                // If a concurrent call already expanded
                return accessType.apply(executions[index]);
            }
            if (maximumLength >= 0 && index > maximumLength) {
                throw new LimitExceededException(index + " > " + maximumLength);
            }
            int old = expandExecutions(index + stepSize);
            updateCallSites();
            return accessType.apply(executions[index]);
        }

        private void updateCallSites() {
            MethodHandle accessor = createFullHandle(target, executions, filter, accessorExceptionHandler, Registry::getAccessor);
            MethodHandle updater = createFullHandle(target, executions, filter, updaterExceptionHandler, Registry::getUpdater);
            accessorCallSite.setTarget(accessor);
            updaterCallSite.setTarget(updater);
        }
    }

    static MethodHandle addMutableCallSiteSynchronization(MethodHandle doBefore, MutableCallSite... callSites) {
        if (callSites.length == 0) {
            return doBefore;
        }
        MethodHandle syncOuterCallSite = SYNC.bindTo(callSites);
        syncOuterCallSite = Methods.acceptThese(syncOuterCallSite, doBefore.type().parameterArray());
        return MethodHandles.foldArguments(syncOuterCallSite, doBefore);
    }

    /**
     * Creates a handle of type (&lt;type.returnType&gt;)MethodHandle that creates a constant handle returning the input value
     * returning the input of this factory.
     *
     * The created constant handle will be of the same type as the goven type parameter.
     *
     * @param type The type of the new constant handle
     * @return the factory handle
     */
    static MethodHandle createConstantHandleFactory(MethodType type) {
        Class<?> returnType = type.returnType();
        MethodHandle constantHandleFactory = CONSTANT_HANDLE_FACTORY.bindTo(returnType).asType(methodType(MethodHandle.class, returnType));
        if (type.parameterCount() > 0) {
            MethodHandle dropArguments = MethodHandles.insertArguments(DROP_ARGUMENTS, 1, 0, type.parameterArray());
            constantHandleFactory = MethodHandles.filterReturnValue(constantHandleFactory, dropArguments);
        }

        return constantHandleFactory;
    }
}
