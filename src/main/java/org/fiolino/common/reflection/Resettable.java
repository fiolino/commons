package org.fiolino.common.reflection;

/**
 * A registry is used to construct registry handles for a certain target handle.
 *
 * Created by kuli on 08.03.17.
 */
public interface Resettable {
    /**
     * Clears all cached data for this registry.
     *
     * Later calls will be handled as if they were the first ones.
     */
    void reset();
}
