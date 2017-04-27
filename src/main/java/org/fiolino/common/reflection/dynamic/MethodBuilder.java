package org.fiolino.common.reflection.dynamic;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.InvokeDynamic;
import net.bytebuddy.implementation.MethodCall;
import sun.reflect.misc.MethodUtil;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Created by kuli on 25.04.17.
 */
public final class MethodBuilder {

    private final MethodDescription method;
    private Implementation implementation;

    MethodBuilder(MethodDescription method) {
        this.method = method;
    }

    public MethodDescription getMethod() {
        return method;
    }

    private MethodCall insertAll(MethodCall input, Argument... args) {
        MethodCall c = input;
        for (Argument a : args) {
            c = a.insertArgument(c);
        }
        return c;
    }

    private void register(Implementation impl) {
        if (implementation == null) {
            implementation = impl;
        } else if (implementation instanceof Implementation.Composable) {
            implementation = ((Implementation.Composable) implementation).andThen(impl);
        } else {
            throw new IllegalStateException(implementation + " is not composable");
        }
    }

    public void registerMethodHandle(MethodHandle handle, Argument... arguments) {
        Method m;
        try {
            m = MethodHandles.reflectAs(Method.class, handle);
        } catch (IllegalArgumentException ex) {
            registerBootstrapper((l, c, n, t) -> new ConstantCallSite(handle));
            return;
        }
        register(insertAll(MethodCall.invoke(m), arguments));
    }

    public void registerBootstrapper(Bootstrapper bootstrapper, Argument... arguments) {
        InvokeDynamic.WithImplicitTarget invoke = InvokeDynamic.bootstrap((Method) null);

    }
}
