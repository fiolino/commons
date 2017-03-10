package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandles;

/**
 * Created by kuli on 10.03.17.
 */
final class RegistryMapper<T> implements LambdaRegistry<T> {
    private final Registry registry;
    private final T accessor, updater;

    RegistryMapper(HandleRegistry registry, Class<T> lambdaType) {
        this.registry = registry;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        accessor = Methods.lambdafy(lookup, registry.getAccessor(), lambdaType);
        updater = Methods.lambdafy(lookup, registry.getUpdater(), lambdaType);
    }

    @Override
    public T getAccessor() {
        return accessor;
    }

    @Override
    public T getUpdater() {
        return updater;
    }

    @Override
    public void clear() {
        registry.clear();
    }
}
