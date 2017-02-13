package org.fiolino.common.reflection;

/**
 * Created by Michael Kuhlmann on 14.12.2015.
 */
@FunctionalInterface
public interface MethodFinderCallback<T> {
    void findVia(T prototype) throws Throwable;
}
