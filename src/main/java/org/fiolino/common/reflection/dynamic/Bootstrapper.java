package org.fiolino.common.reflection.dynamic;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Created by kuli on 12.04.17.
 */
public interface Bootstrapper {

    CallSite bootstrap(MethodHandles.Lookup caller, Class<?> owner, String methodName, MethodType methodType) throws Throwable;
}
