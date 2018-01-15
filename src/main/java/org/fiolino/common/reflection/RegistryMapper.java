package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandles;

/**
 * Created by kuli on 10.03.17.
 */
final class RegistryMapper<T> implements LambdaRegistry<T> {
    private final Resettable registry;
    private final T accessor;

    RegistryMapper(Registry registry, Class<T> lambdaType) {
        this.registry = registry;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        accessor = Methods.lambdafy(lookup, registry.getAccessor(), lambdaType);
    }

    @Override
    public T getAccessor() {
        return accessor;
    }

    @Override
    public void reset() {
        registry.reset();
    }
}
