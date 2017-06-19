package org.fiolino.common.util;

/**
 * Created by kuli on 19.06.17.
 */
@FunctionalInterface
public interface SupplierWithException<T, E extends Throwable> {
    T get() throws E;
}
