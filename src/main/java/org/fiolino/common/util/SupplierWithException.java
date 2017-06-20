package org.fiolino.common.util;

/**
 * {@link java.util.function.Supplier}-like interface which allows the implementor to throw exceptions.
 *
 * Created by kuli on 19.06.17.
 */
@FunctionalInterface
public interface SupplierWithException<T, E extends Throwable> {
    /**
     * Gets the value.
     */
    T get() throws E;
}
