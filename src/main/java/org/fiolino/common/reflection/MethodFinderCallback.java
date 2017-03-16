package org.fiolino.common.reflection;

/**
 * Use this to select a certain method from some interface.
 *
 * Created by Michael Kuhlmann on 14.12.2015.
 */
@FunctionalInterface
public interface MethodFinderCallback<T> {
    /**
     * In this method, the implementor should call exactly that method that should be used in the selecting method.
     *
     * The method won't do anything useful, and any given parameters are completely irrelevant. The only reason is to
     * select it.
     *
     * @param prototype This is an instance of the interface
     * @throws Throwable You (or the selected method) can call any exception
     */
    void callMethodFrom(T prototype) throws Throwable;
}
