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
        private final CallSite callSite;
        private final MethodHandle target, filter, exceptionHandler;
        private final int maximum;

        /**
         * Creates the caching handle factory.
         *
         * @param target Use this as the called handle.
         * @param maximum The initial size of the array; this should be the expected maximum length of an average key set
         */
        ParameterToIntMappingCache(MethodHandle target, MethodHandle filter, int maximum) {
            checkTargetAndFilter(target, filter);
            this.target = target;
            this.filter = filter;
            this.maximum = maximum;
            MethodHandles.Lookup lookup = lookup();
            MethodHandle exceptionHandler;
            try {
                exceptionHandler = lookup.findStatic(lookup.lookupClass(), "outOfBounds", methodType(MethodHandle.class, Throwable.class, int.class, Object[].class, int.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError(ex);
            }
            exceptionHandler = MethodHandles.insertArguments(exceptionHandler, 3, maximum - 1);
            this.exceptionHandler = modifyExceptionHandler(exceptionHandler, filter, target.type());
            callSite = new MutableCallSite(target.type());
            reset();
        }

        private MethodHandle createAccessorHandle() {
            MethodHandle[] executions = new MethodHandle[maximum];
            return createFullHandle(target, callSite, executions, filter, exceptionHandler);
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
            callSite.setTarget(createAccessorHandle());
            if (callSite instanceof MutableCallSite) {
                MutableCallSite.syncAll(new MutableCallSite[] {
                        (MutableCallSite) callSite
                });
            }
        }
    }

    private static void fillAccessors(MethodHandle target, CallSite outerCallSite, MethodHandle[] executions) {
        int length = executions.length;
        for (int i=0; i < length; i++) {
            MethodHandle h = executions[i];
            if (h == null) {
                h = createArrayUpdatingHandle(target, outerCallSite, executions, i);
                executions[i] = h;
            }
        }
    }

    private static MethodHandle createArrayUpdatingHandle(MethodHandle target, CallSite outerCallSite, MethodHandle[] executions, int arrayIndex) {
        MethodHandle setTarget = MethodHandles.arrayElementSetter(MethodHandle[].class);
        setTarget = MethodHandles.insertArguments(setTarget, 0, executions, arrayIndex);
        setTarget = addMutableCallSiteSynchronization(setTarget, outerCallSite);
        return createSingleExecutionHandle(target, setTarget);
    }

    private static MethodHandle createAccessingHandle(MethodHandle target, CallSite outerCallSite, MethodHandle[] executions, MethodHandle filter) {
        fillAccessors(target, outerCallSite, executions);
        MethodHandle getFromArray = MethodHandles.arrayElementGetter(MethodHandle[].class).bindTo(executions);
        MethodHandle filtered = applyFilter(getFromArray, filter, 0);
        return Methods.acceptThese(filtered, target.type().parameterArray());
    }

    private static MethodHandle createFullHandle(MethodHandle target, CallSite outerCallSite, MethodHandle[] executions, MethodHandle filter, MethodHandle exceptionHandler) {
        MethodHandle primary = createAccessingHandle(target, outerCallSite, executions, filter);
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
        private final CallSite callSite;
        private final int maximumLength, stepSize;
        private final MethodHandle exceptionHandler;
        private final Semaphore semaphore;
        private MethodHandle[] executions;

        /**
         * Creates the caching handle factory.
         *
         * @param target Use this as the called handle.
         * @param initialMaximum The initial size of the array; this should be the expected maximum length of an average key set
         * @param finalMaximum This is the absolute maximum size; if this exceeds, an exception is thrown. Use this to limit
         *                     the amount of stored keys to avoid memory and performance issues
         */
        ExpandableParameterToIntMappingCache(MethodHandle target, MethodHandle filter, MutableCallSite callSite,
                                             int initialMaximum, int finalMaximum, int stepSize) {
            if (finalMaximum >= 0 && finalMaximum < initialMaximum) {
                throw new IllegalArgumentException(finalMaximum + " < " + initialMaximum);
            }
            checkTargetAndFilter(target, filter);
            this.callSite = callSite;
            this.target = target;
            this.filter = filter;
            this.maximumLength = finalMaximum;
            this.stepSize = stepSize;
            this.semaphore = new Semaphore(1);
            expandExecutions(0, initialMaximum);

            MethodHandle exceptionHandler;
            try {
                exceptionHandler = lookup().bind(this, "outOfBounds", methodType(MethodHandle.class, ArrayIndexOutOfBoundsException.class, int.class, Object[].class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError(ex);
            }
            this.exceptionHandler = modifyExceptionHandler(exceptionHandler, filter, target.type(), semaphore);

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
            this(target, filter, new MutableCallSite(target.type()), initialMaximum, finalMaximum, stepSize);
        }

        private int expandExecutions(int length) {
            int l = executions.length;
            expandExecutions(l, length);
            return l;
        }

        private void expandExecutions(int start, int length) {
            if (start == 0) {
                executions = new MethodHandle[length];
            } else if (start < length) {
                executions = Arrays.copyOf(executions, length);
            } else {
                return;
            }
            for (int i=start; i < length; i++) {
                executions[i] = target;
            }
        }

        @Override
        public CallSite getCallSite() {
            return callSite;
        }

        @Override
        public void reset() {
            expandExecutions(0, maximumLength);
        }

        @SuppressWarnings("unused")
        private MethodHandle outOfBounds(ArrayIndexOutOfBoundsException ex, int index, Object[] arguments) {
            if (index < 0) {
                throw new LimitExceededException("" + index);
            }
            if (index < executions.length) {
                // If a concurrent call already expanded the array
                return executions[index];
            }
            if (maximumLength >= 0 && index > maximumLength) {
                throw new LimitExceededException(index + " > " + maximumLength);
            }
            int old = expandExecutions(index + stepSize);
            updateCallSites();
            return executions[index];
        }

        private void updateCallSites() {
            MethodHandle accessor = createFullHandle(target, callSite, executions, filter, exceptionHandler);
            callSite.setTarget(accessor);
        }
    }

    /**
     * Adds a call to MutableCallSite.syncAll() after the given handle if the callsite is a {@link MutableCallSite}.
     *
     * @param doBefore What will be done before
     * @param callSite The callsite to sync
     * @return A handle of the same type as doBefore
     */
    private static MethodHandle addMutableCallSiteSynchronization(MethodHandle doBefore, CallSite callSite) {
        if (callSite instanceof MutableCallSite) {
            MethodHandle syncOuterCallSite = SYNC.bindTo(new MutableCallSite[] {(MutableCallSite) callSite });
            syncOuterCallSite = Methods.acceptThese(syncOuterCallSite, doBefore.type().parameterArray());
            return MethodHandles.foldArguments(syncOuterCallSite, doBefore);
        }
        return doBefore;
    }

    /**
     * Creates a handle of type (&lt;type.returnType&gt;)MethodHandle that creates a constant handle returning the input value
     * returning the input of this factory.
     *
     * The created constant handle will be of the same type as the given type parameter.
     *
     * @param type The type of the new constant handle
     * @return the factory handle
     */
    private static MethodHandle createConstantHandleFactory(MethodType type) {
        Class<?> returnType = type.returnType();
        MethodHandle constantHandleFactory = CONSTANT_HANDLE_FACTORY.bindTo(returnType).asType(methodType(MethodHandle.class, returnType));
        if (type.parameterCount() > 0) {
            MethodHandle dropArguments = MethodHandles.insertArguments(DROP_ARGUMENTS, 1, 0, type.parameterArray());
            constantHandleFactory = MethodHandles.filterReturnValue(constantHandleFactory, dropArguments);
        }

        return constantHandleFactory;
    }

    /**
     * Creates a handle that calls the given execution, and then creates a static constant handle that will be used
     * as a parameter in the given setTarget handle.
     *
     * @param execution A handle of arbitrary type, executing something
     * @param setTarget (MethodHandle)void -- sets the created constant handle
     * @return A handle of the same type as the execution handle
     */
    private static MethodHandle createSingleExecutionHandle(MethodHandle execution, MethodHandle setTarget) {
        MethodType type = execution.type();
        if (type.returnType() == void.class) {
            return createVoidSyncHandle(execution, setTarget);
        }

        MethodHandle constantHandleFactory = Reflection.createConstantHandleFactory(type);
        setTarget = MethodHandles.filterArguments(setTarget, 0, constantHandleFactory);

        MethodHandle setTargetAndReturnValue = Methods.returnArgument(setTarget, 0);
        MethodHandle result = MethodHandles.filterReturnValue(execution, setTargetAndReturnValue);
        if (execution.isVarargsCollector()) {
            result = result.asVarargsCollector(type.parameterType(type.parameterCount() - 1));
        }
        return result;
    }

    /**
     * Creates a handle that calld execution only once, and then changes the target to a no-op handle.
     *
     * @param execution What to do only once
     * @param setTarget (MethodHandle)void
     * @return A handle of the same type as execution
     */
    private static MethodHandle createVoidSyncHandle(MethodHandle execution, MethodHandle setTarget) {
        MethodHandle doNothing = Methods.acceptThese(Methods.DO_NOTHING, execution.type().parameterArray());
        MethodHandle setTargetToNoop = setTarget.bindTo(doNothing);

        // The following is only needed if execution accepts parameters, which is only the case when used from other MultiArgumentExecutionBuilder
        setTargetToNoop = Methods.acceptThese(setTargetToNoop, execution.type().parameterArray());
        return MethodHandles.foldArguments(setTargetToNoop, execution);
    }

    /**
     * Creates a handle that will call the given target exactly once, wrapped in a synchronized VolatileCallSite, so
     * that it's guaranteed that it will definitely be called only once.
     *
     * The return value will be stored in a ConstantHandle and pushed back into the given {@link CallSite}.
     *
     * @param target What to execute
     * @param callSite Where to put the constant value
     * @param semaphore Used to synchronize the execution
     * @return A handle of the same type as target
     */
    static MethodHandle createSingleExecutionWithSynchronization(MethodHandle target, CallSite callSite, Semaphore semaphore) {
        MethodHandle setTarget = SET_TARGET.bindTo(callSite);
        setTarget = addMutableCallSiteSynchronization(setTarget, callSite);
        return createSingleExecutionWithSynchronization(target, setTarget, semaphore);
    }

    /**
     * Creates a handle that will call the given target exactly once, wrapped in a synchronized VolatileCallSite, so
     * that it's guaranteed that it will definitely be called only once.
     *
     * The return value will be stored in a ConstantHandle and pushed back via the given setTarget handle.
     *
     * @param target What to execute
     * @param setTarget (MethodHandle)void -- will be called when the return value is set
     * @param semaphore Used to synchronize the execution
     * @return A handle of the same type as target
     */
    private static MethodHandle createSingleExecutionWithSynchronization(MethodHandle target, MethodHandle setTarget, Semaphore semaphore) {
        MethodType type = target.type();
        CallSite innerCallSite = new VolatileCallSite(type);
        MethodHandle setTargetInner = SET_TARGET.bindTo(innerCallSite);

        MethodHandle setBothTargets = MethodHandles.foldArguments(setTarget, setTargetInner);
        MethodHandle innerCaller = createSingleExecutionHandle(target, setBothTargets);
        innerCallSite.setTarget(innerCaller);
        MethodHandle guardedCaller = Methods.synchronizeWith(innerCallSite.dynamicInvoker(), semaphore);
        try {
            setTarget.invokeExact(guardedCaller);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("Exception when setting target", t);
        }

        MethodHandle resetInner = setTargetInner.bindTo(innerCaller);
        resetInner = Methods.acceptThese(resetInner, type.parameterArray());
        if (target.isVarargsCollector()) {
            resetInner = resetInner.asVarargsCollector(type.parameterType(type.parameterCount() - 1));
        }
        return MethodHandles.foldArguments(guardedCaller, resetInner);
    }
}
