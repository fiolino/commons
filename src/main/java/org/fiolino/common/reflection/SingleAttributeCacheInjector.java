package org.fiolino.common.reflection;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;

/**
 * Created by kuli on 09.03.17.
 */
public abstract class SingleAttributeCacheInjector {
    /**
     * Creates a MethodHandle that checks the index at the given position. It will call the target if there is no value cached,
     * or just return the cached instance.
     *
     * The returned handle must have the same type as the given target, and the type's parameter at the given index described
     * the class type of this cache.
     *
     * This method may return null, whoch means it does not provide a cache for the expected type.
     *
     * @param target The target to call
     * @param argumentIndex Use the parameter at this index
     * @return A caching handle, if the class of the given parameter is accepted
     */
    @Nullable
    protected abstract MethodHandle cache(MethodHandle target, int argumentIndex);
}
