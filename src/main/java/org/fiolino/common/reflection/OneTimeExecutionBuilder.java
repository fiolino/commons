package org.fiolino.common.reflection;

import java.lang.invoke.*;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * This creates a {@link MethodHandle} which executes its target and then sets the result as a constant handle
 * into its own CallSite so that later calls will directly return that cached value.
 *
 * The target itself is wrapped into a synchronization block so that concurrent calls on the initial handle will block
 * until the final result is available. This is done via some double checked locking: The synchronizing wrapper handle
 * points to an inner CallSite whose target is then set to the same constant handle as the outer one. Awaiting
 * threads will then call the same constant handle after release.
 *
 * The target can be of any type, but the API only exposes handles without parameters. Anything else won't make much sense
 * because the parameter value is not checked at all; having multiple calls with different parameter values would still
 * block all calls except the first one. This feature is only needed in the {@link MultiArgumentExecutionBuilder} in the
 * case of a parameter value of null.
 *
 * Created by kuli on 07.03.17.
 */
class OneTimeExecutionBuilder implements Registry {

    private static final MethodHandle SYNC;
    private static final MethodHandle CONSTANT_HANDLE_FACTORY;
    private static final MethodHandle DROP_ARGUMENTS;

    static {
        try {
            SYNC = publicLookup().findStatic(MutableCallSite.class, "syncAll", methodType(void.class, MutableCallSite[].class));
            CONSTANT_HANDLE_FACTORY = publicLookup().findStatic(MethodHandles.class, "constant",
                    methodType(MethodHandle.class, Class.class, Object.class));
            DROP_ARGUMENTS = publicLookup().findStatic(MethodHandles.class, "dropArguments",
                    methodType(MethodHandle.class, MethodHandle.class, int.class, Class[].class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    private final CallSite callSite;
    private final MethodHandle updatingHandle;

    /**
     * Creates the executor.
     *
     * @param target The target handle, which will be called only once as long as the updater or reset() aren't in play
     * @param isVolatile Use this to make the CallSite volatile; it's a performance feature, so when update or reset()
     *                   are used frequently, the this should be true
     */
    OneTimeExecutionBuilder(MethodHandle target, boolean isVolatile) {
        MethodType type = target.type();

        callSite = isVolatile ? new VolatileCallSite(type) : new MutableCallSite(type);
        CallSite innerCallSite = new VolatileCallSite(type);
        MethodHandle setTargetOuter, setTargetInner;
        try {
            setTargetOuter = publicLookup().bind(callSite, "setTarget", methodType(void.class, MethodHandle.class));
            setTargetInner = publicLookup().bind(innerCallSite, "setTarget", methodType(void.class, MethodHandle.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }

        MethodHandle innerCaller = createSyncHandle(target, setTargetOuter, setTargetInner);
        innerCallSite.setTarget(innerCaller);
        MethodHandle guardedCaller = Methods.synchronize(innerCallSite.dynamicInvoker());
        callSite.setTarget(guardedCaller);
        MethodHandle resetInner = setTargetInner.bindTo(innerCaller);
        resetInner = Methods.dropAllOf(resetInner, type);
        updatingHandle = MethodHandles.foldArguments(guardedCaller, resetInner);
    }

    // Input creates something, which will be used as the new execution of my callSite after calling
    private MethodHandle createSyncHandle(MethodHandle execution, MethodHandle setTargetOuter, MethodHandle setTargetInner) {
        MethodType type = execution.type();

        MethodHandle setBothTargets = MethodHandles.foldArguments(setTargetOuter, setTargetInner);

        Class<?> returnType = type.returnType();
        if (returnType == void.class) {
            return createVoidSyncHandle(execution, setBothTargets);
        }

        MethodHandle constantHandleFactory = CONSTANT_HANDLE_FACTORY.bindTo(returnType).asType(methodType(MethodHandle.class, returnType));
        // The following is only needed if execution accepts parameters, which is only the case when used from other MultiArgumentExecutionBuilder
        int parameterCount = type.parameterCount();
        if (parameterCount > 0) {
            MethodHandle dropArguments = MethodHandles.insertArguments(DROP_ARGUMENTS, 1, 0, type.parameterArray());
            constantHandleFactory = MethodHandles.filterReturnValue(constantHandleFactory, dropArguments);
        }

        setBothTargets = MethodHandles.filterArguments(setBothTargets, 0, constantHandleFactory);
        setBothTargets = addMutableCallSiteSynchronization(setBothTargets);

        MethodHandle setBothAndReturnValue = Methods.returnArgument(setBothTargets, 0);
        // The following is only needed if execution accepts parameters, which is only the case when used from other MultiArgumentExecutionBuilder
        if (parameterCount > 0) {
            setBothAndReturnValue = MethodHandles.dropArguments(setBothAndReturnValue, 1, type.parameterArray());
        }
        return MethodHandles.foldArguments(setBothAndReturnValue, execution);
    }

    private MethodHandle createVoidSyncHandle(MethodHandle execution, MethodHandle setTargets) {
        MethodHandle doNothing = Methods.dropAllOf(Methods.DO_NOTHING, execution.type());
        MethodHandle setTargetToNoop = setTargets.bindTo(doNothing);
        setTargetToNoop = addMutableCallSiteSynchronization(setTargetToNoop);

        // The following is only needed if execution accepts parameters, which is only the case when used from other MultiArgumentExecutionBuilder
        setTargetToNoop = Methods.dropAllOf(setTargetToNoop, execution.type());
        return MethodHandles.foldArguments(setTargetToNoop, execution);
    }

    private MethodHandle addMutableCallSiteSynchronization(MethodHandle doBefore) {
        if (!(callSite instanceof MutableCallSite)) {
            return doBefore;
        }
        MethodHandle syncOuterCallSite = SYNC.bindTo(new MutableCallSite[] {
                (MutableCallSite) callSite
        });
        syncOuterCallSite = Methods.dropAllOf(syncOuterCallSite, doBefore.type());
        return MethodHandles.foldArguments(syncOuterCallSite, doBefore);
    }

    @Override
    public void reset() {
        MutableCallSite cs = preReset();
        if (cs != null) {
            MutableCallSite.syncAll(new MutableCallSite[] {
                    cs
            });
        }
    }

    MutableCallSite preReset() {
        callSite.setTarget(updatingHandle);
        return callSite instanceof MutableCallSite ? (MutableCallSite)callSite : null;
    }

    @Override
    public MethodHandle getAccessor() {
        return callSite.dynamicInvoker();
    }

    @Override
    public MethodHandle getUpdater() {
        return updatingHandle;
    }
}
