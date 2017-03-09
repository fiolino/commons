package org.fiolino.common.reflection;

import java.lang.invoke.*;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 07.03.17.
 */
class OneTimeExecutor implements Registry {

    private static final MethodHandle SYNC;
    private static final MethodHandle CONSTANT_HANDLE_FACTORY;

    static {
        try {
            SYNC = publicLookup().findStatic(MutableCallSite.class, "syncAll", methodType(void.class, MutableCallSite[].class));
            CONSTANT_HANDLE_FACTORY = publicLookup().findStatic(MethodHandles.class, "constant",
                    methodType(MethodHandle.class, Class.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }

    private final MutableCallSite callSite;
    private final MethodHandle updatingHandle;

    OneTimeExecutor(MethodHandle target) {
        MethodType type = target.type();
        callSite = new MutableCallSite(type);
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
        MethodHandle guardedCaller = Methods.guardWithSemaphore(innerCallSite.dynamicInvoker());
        callSite.setTarget(guardedCaller);
        MethodHandle resetInner = MethodHandles.insertArguments(setTargetInner, 0, innerCaller);
        updatingHandle = MethodHandles.foldArguments(guardedCaller, resetInner);
    }

    // target: ()<T> or ()void
    // return: ()<T> or ()void
    // Input creates something, which will be used as the new target of my callSite after calling
    private MethodHandle createSyncHandle(MethodHandle target, MethodHandle setTargetOuter, MethodHandle setTargetInner) {
        MethodType type = target.type();

        MethodHandle syncOuterCallSite = MethodHandles.insertArguments(SYNC, 0, (Object) new MutableCallSite[] {
                callSite
        });
        MethodHandle setBoth = MethodHandles.foldArguments(setTargetOuter, setTargetInner);

        Class<?> returnType = type.returnType();
        if (returnType == void.class) {
            setBoth = MethodHandles.insertArguments(setBoth, 0, Methods.DO_NOTHING);
            MethodHandle setTargetAndSync = MethodHandles.foldArguments(syncOuterCallSite, setBoth);
            return MethodHandles.foldArguments(setTargetAndSync, target);
        }

        MethodHandle constantHandleFactory = MethodHandles.insertArguments(CONSTANT_HANDLE_FACTORY, 0, returnType);
        setBoth = MethodHandles.filterArguments(setBoth, 0, constantHandleFactory);
        MethodHandle setTargetAndReturnValue = Methods.returnArgument(setBoth.asType(methodType(void.class, returnType)), 0);
        MethodHandle executeAndSetTarget = MethodHandles.foldArguments(setTargetAndReturnValue, target);

        MethodHandle syncAndReturn = Methods.returnArgument(MethodHandles.dropArguments(syncOuterCallSite, 0, returnType), 0);
        return MethodHandles.foldArguments(syncAndReturn, executeAndSetTarget);
    }

    @Override
    public void clear() {
        callSite.setTarget(updatingHandle);
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
