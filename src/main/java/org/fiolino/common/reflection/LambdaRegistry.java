package org.fiolino.common.reflection;

/**
 * Returns lambda interfaces based on the one given in the Registry factory method.
 *
 * Created by kuli on 10.03.17.
 */
public interface LambdaRegistry<T> extends Resettable {
    /**
     * Gets the function that calls the cached service.
     */
    T getAccessor();
}
