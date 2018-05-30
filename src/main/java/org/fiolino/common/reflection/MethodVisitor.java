package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Created by Kuli on 6/15/2016.
 */
public interface MethodVisitor<V> {

    /**
     * Is called as a callback from a method visitor.
     *
     * @param value          The value from the previous run (or the initial value, if it's the first run)
     * @param l              The local lookup
     * @param m              The visited method
     * @param handleSupplier Will return the MethodHandle of that method, or the created handle by that
     * @return The value for the next run, or the return value in case of the last run
     */
    V visit(V value, MethodHandles.Lookup l, Method m, Supplier<MethodHandle> handleSupplier);
}
