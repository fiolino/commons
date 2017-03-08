package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;

/**
 * This class creates {@link Registry} instances, which can be used to fetch statically caching instances of MethodHandles or functions.
 *
 * Created by kuli on 07.03.17.
 */
public final class RegistryBuilder {

    /**
     * Builds a Registry for a given target handle.
     *
     * @param target This handle will be called only once per parameter value.
     * @return A Registry holding handles with exactly the same type as the target type
     */
    public Registry buildFor(MethodHandle target) {
        if (target.type().parameterCount() == 0) {
            return new OneTimeExecutor(target);
        }
        throw new UnsupportedOperationException();
    }

}
