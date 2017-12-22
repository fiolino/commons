package org.fiolino.common.reflection;

import java.lang.invoke.*;
import java.util.concurrent.Semaphore;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * This creates a Registry for MethodHandles which executes its target and then sets the result as a constant handle
 * into its own CallSite so that later calls will directly return that cached value.
 *
 * The target itself is wrapped into a synchronization block so that concurrent calls on the initial handle will block
 * until the final result is available. This is done via some double checked locking: The synchronizing wrapper handle
 * points to an inner CallSite whose target is then set to the same constant handle as the outer one. Awaiting
 * threads will then call the same constant handle after release.
 *
 * The target can be of any type, but parameters are not evaluated at all. That means successive calls will be discarded
 * independent of their parameter values. So it's mostly useful for parameter-less targets.
 *
 * Created by kuli on 07.03.17.
 */
final class OneTimeRegistryBuilder implements OneTimeExecution {

    private final Semaphore semaphore;
    private final CallSite callSite;
    private final MethodHandle updatingHandle;

    /**
     * Creates the builder.
     *
     * @param target The target handle, which will be called only once as long as the updater or reset() aren't in play
     * @param isVolatile Use this to make the CallSite volatile; it's a performance feature, so when update or reset()
     *                   are used frequently, the this should be true
     */
    OneTimeRegistryBuilder(MethodHandle target, boolean isVolatile) {
        this(target, isVolatile ? new VolatileCallSite(target.type()) : new MutableCallSite(target.type()));
    }

    /**
     * Creates the builder.
     *
     * @param target The target handle, which will be called only once as long as the updater or reset() aren't in play
     * @param callSite An existing CallSite, whch will be updated with
     */
    OneTimeRegistryBuilder(MethodHandle target, CallSite callSite) {
        this.callSite = callSite;
        MethodType type = target.type();

        semaphore = new Semaphore(1);
        CallSite innerCallSite = new VolatileCallSite(type);
        MethodHandle setTargetOuter, setTargetInner;
        try {
            setTargetOuter = publicLookup().bind(callSite, "setTarget", methodType(void.class, MethodHandle.class));
            setTargetInner = publicLookup().bind(innerCallSite, "setTarget", methodType(void.class, MethodHandle.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }

        MethodHandle setBothTargets = MethodHandles.foldArguments(setTargetOuter, setTargetInner);
        MethodHandle innerCaller = createSyncHandle(target, setBothTargets);
        innerCallSite.setTarget(innerCaller);
        MethodHandle guardedCaller = Methods.synchronizeWith(innerCallSite.dynamicInvoker(), semaphore);
        callSite.setTarget(guardedCaller);
        MethodHandle resetInner = setTargetInner.bindTo(innerCaller);
        resetInner = Methods.acceptThese(resetInner, type.parameterArray());
        if (target.isVarargsCollector()) {
            resetInner = resetInner.asVarargsCollector(type.parameterType(type.parameterCount() - 1));
        }
        updatingHandle = MethodHandles.foldArguments(guardedCaller, resetInner);
    }

    // Input creates something, which will be used as the new execution of my callSite after calling
    private MethodHandle createSyncHandle(MethodHandle execution, MethodHandle setTarget) {
        MethodType type = execution.type();


        if (type.returnType() == void.class) {
            return createVoidSyncHandle(execution, setTarget);
        }

        MethodHandle constantHandleFactory = Reflection.createConstantHandleFactory(type);
        setTarget = MethodHandles.filterArguments(setTarget, 0, constantHandleFactory);
        setTarget = addMutableCallSiteSynchronization(setTarget);

        MethodHandle setBothAndReturnValue = Methods.returnArgument(setTarget, 0);
        // The following is only needed if execution accepts parameters, which is only the case when used from other MultiArgumentExecutionBuilder
        if (type.parameterCount() > 0) {
            setBothAndReturnValue = MethodHandles.dropArguments(setBothAndReturnValue, 1, type.parameterArray());
        }
        MethodHandle result = MethodHandles.foldArguments(setBothAndReturnValue, execution);
        if (execution.isVarargsCollector()) {
            result = result.asVarargsCollector(type.parameterType(type.parameterCount() - 1));
        }
        return result;
    }

    private MethodHandle createVoidSyncHandle(MethodHandle execution, MethodHandle setTargets) {
        MethodHandle doNothing = Methods.acceptThese(Methods.DO_NOTHING, execution.type().parameterArray());
        MethodHandle setTargetToNoop = setTargets.bindTo(doNothing);
        setTargetToNoop = addMutableCallSiteSynchronization(setTargetToNoop);

        // The following is only needed if execution accepts parameters, which is only the case when used from other MultiArgumentExecutionBuilder
        setTargetToNoop = Methods.acceptThese(setTargetToNoop, execution.type().parameterArray());
        return MethodHandles.foldArguments(setTargetToNoop, execution);
    }

    private MethodHandle addMutableCallSiteSynchronization(MethodHandle doBefore) {
        if (!(callSite instanceof MutableCallSite)) {
            return doBefore;
        }
        return Reflection.addMutableCallSiteSynchronization(doBefore, (MutableCallSite) callSite);
    }

    @Override
    public void reset() {
        resetTo(updatingHandle);
    }

    private void resetTo(MethodHandle handle) {
        MutableCallSite cs = preResetTo(handle);
        if (cs != null) {
            MutableCallSite.syncAll(new MutableCallSite[] {
                    cs
            });
        }
    }

    MutableCallSite preReset() {
        return preResetTo(updatingHandle);
    }

    private MutableCallSite preResetTo(MethodHandle handle) {
        callSite.setTarget(Methods.acceptThese(handle, updatingHandle.type().parameterArray()));
        return callSite instanceof MutableCallSite ? (MutableCallSite)callSite : null;
    }

    @Override
    public CallSite getCallSite() {
        return callSite;
    }

    @Override
    public MethodHandle getUpdater() {
        return updatingHandle;
    }

    @Override
    public void updateTo(Object newValue) {
        Class<?> returnType = updatingHandle.type().returnType();
        if (returnType == void.class) {
            // No synchronization necessary
            resetTo(Methods.DO_NOTHING);
            return;
        }
        semaphore.acquireUninterruptibly();
        try {
            resetTo(MethodHandles.constant(returnType, newValue));
        } finally {
            semaphore.release();
        }
    }
}
