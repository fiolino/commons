package org.fiolino.common.util;

import org.fiolino.common.reflection.Methods;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 07.03.17.
 */
class OneTimeExecutor implements Registry {

    private static final MethodHandle SYNC;
    private static final MethodHandle CONSTANT_HANDLE_FACTORY;

    static {
        MethodHandles.Lookup lookup = lookup();
        try {
            SYNC = lookup.findStatic(lookup.lookupClass(), "sync", methodType(void.class, MutableCallSite.class));
            CONSTANT_HANDLE_FACTORY = publicLookup().findStatic(MethodHandles.class, "constant",
                    methodType(MethodHandle.class, Class.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }

    private final MutableCallSite callSite;
    private final MethodHandle initialCaller;

    OneTimeExecutor(MethodHandle target) {
        MethodType type = target.type();
        callSite = new MutableCallSite(type);
        initialCaller = createSyncHandle(target);
        clear();
    }

    // target: ()<T> or ()void
    // return: ()<T> or ()void
    // Input creates something, which will be used as the new target of my callSite after calling
    private MethodHandle createSyncHandle(MethodHandle target) {
        MethodHandle setTarget;
        try {
            setTarget = publicLookup().bind(callSite, "setTarget", methodType(void.class, MethodHandle.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
        MethodHandle syncMyCallSite = MethodHandles.insertArguments(SYNC, 0, callSite);

        Class<?> returnType = target.type().returnType();
        if (returnType == void.class) {
            setTarget = MethodHandles.insertArguments(setTarget, 0, Methods.DO_NOTHING);
            MethodHandle setTargetAndSync = MethodHandles.foldArguments(syncMyCallSite, setTarget);
            return MethodHandles.foldArguments(setTargetAndSync, target);
        }
        MethodHandle constantHandleFactory = MethodHandles.insertArguments(CONSTANT_HANDLE_FACTORY, 0, returnType);
        setTarget = MethodHandles.filterArguments(setTarget, 0, constantHandleFactory);
        MethodHandle setTargetAndReturnValue = Methods.returnArgument(setTarget.asType(methodType(void.class, returnType)), 0);
        MethodHandle executeAndSetTarget = MethodHandles.foldArguments(setTargetAndReturnValue, target);

        MethodHandle syncAndReturn = Methods.returnArgument(MethodHandles.dropArguments(syncMyCallSite, 0, returnType), 0);
        return MethodHandles.foldArguments(syncAndReturn, executeAndSetTarget);
    }

    @SuppressWarnings("unused")
    private static void sync(MutableCallSite callSite) {
        MutableCallSite.syncAll(new MutableCallSite[] {
                callSite
        });
    }

    @Override
    public void clear() {
        callSite.setTarget(initialCaller);
    }

    @Override
    public MethodHandle getAccessor() {
        return callSite.dynamicInvoker();
    }

    @Override
    public MethodHandle getUpdater() {
        return initialCaller;
    }
}
