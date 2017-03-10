package org.fiolino.common.reflection;

/**
 * Returns lambda interfaces based on the one given in the Registry factory method.
 *
 * Created by kuli on 10.03.17.
 */
public interface LambdaRegistry<T> extends Registry {
    /**
     * Gets the function that calls the cached service.
     */
    T getAccessor();

    /**
     * Gets the function that calls the original service, overwriting any cached data.
     */
    T getUpdater();
}
