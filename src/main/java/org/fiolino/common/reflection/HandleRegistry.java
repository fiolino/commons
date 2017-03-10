package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;

/**
 * A registry is used to construct registry handles for a certain target handle.
 *
 * It is constructed by the {@link RegistryBuilder}.
 *
 * Created by kuli on 08.03.17.
 */
public interface HandleRegistry extends Registry {
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
