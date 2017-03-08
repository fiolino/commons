package org.fiolino.common.util;

import java.lang.invoke.MethodHandle;

/**
 * Created by kuli on 08.03.17.
 */
public interface Registry {
    /**
     * Clears all cached data for this registry.
     */
    void clear();

    /**
     * Gets the handle that calls the cached service.
     *
     * The type of this handle is exactly the same as the one that was used for creating this registry.
     */
    MethodHandle getAccessor();

    /**
     * Gets the handle that calls the original service, overwriting any cached data.
     *
     * The type of this handle is exactly the same as the one that was used for creating this registry.
     */
    MethodHandle getUpdater();
}
