package org.fiolino.common.reflection;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 09.03.17.
 */
public abstract class MapCacheInjector extends SingleAttributeCacheInjector {
    @Nullable
    abstract Map<?, ?> createMap(Class<?> type);

    @Nullable
    @Override
    protected MethodHandle cache(MethodHandle target, int argumentIndex) {
        MethodType type = target.type();
        Class<?> argType = type.parameterType(argumentIndex);
        Map<?, ?> map = createMap(argType);
        if (map == null) {
            return null;
        }
        MethodHandles.Lookup lookup = MethodHandles.publicLookup().in(map.getClass());
        MethodHandle compute = lookup.bind(map, "computeIfAbsent", methodType(Object.class, Object.class, Function.class));

        return null;
    }

    protected MapRegistryBuilder createBuilder(MethodHandle target, Object... leadingArguments) {
        MethodHandle functionFactory = Methods.createLambdaFactory(lookup(), target, Function.class);
        if (functionFactory == null) {
            // Not a direct handle
            return null;
        }

        switch (leadingArguments.length) {
            case 0:
                return new MapRegistryBuilder(functionFactory);
            case 1:
                return new MapRegistryBuilder(functionFactory.bindTo(leadingArguments[0]));
            default:
                return new MapRegistryBuilder(MethodHandles.insertArguments(functionFactory, 0, leadingArguments));
        }
    }
}
