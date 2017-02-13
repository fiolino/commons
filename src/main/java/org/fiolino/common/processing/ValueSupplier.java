package org.fiolino.common.processing;

/**
 * Created by kuli on 15.04.15.
 */
@FunctionalInterface
public interface ValueSupplier<S, V> {

    V getFor(S source) throws Throwable;
}
