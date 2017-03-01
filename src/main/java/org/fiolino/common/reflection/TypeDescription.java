package org.fiolino.common.reflection;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Created by Kuli on 6/17/2016.
 */
public final class TypeDescription<T> {

    private final Class<T> type;
    private final MethodHandles.Lookup lookup;
    private final String name;
    private final Map<String, AttributeDescription> attributes = new LinkedHashMap<>();

    private TypeDescription(MethodHandles.Lookup lookup, Class<T> type, String name) {
        this.type = type;
        this.lookup = lookup;
        this.name = name;
    }

    public static <T> TypeDescription<T> of(Class<T> type) {
        return of(MethodHandles.publicLookup().in(type), type);
    }

    public static <T> TypeDescription<T> of(MethodHandles.Lookup lookup, Class<T> type) {
        return of(lookup, type, type.getSimpleName());
    }

    public static <T> TypeDescription<T> of(Class<T> type, String name) {
        return new TypeDescription<>(MethodHandles.publicLookup().in(type), type, name);
    }

    public static <T> TypeDescription<T> of(MethodHandles.Lookup lookup, Class<T> type, String name) {
        return new TypeDescription<>(lookup, type, name);
    }

    public void add(Field f) {
        add(f, f.getName());
    }

    public void add(Field f, String name) {
        if (attributes.containsKey(name)) {
            throw new IllegalStateException(name + " is already in use for " + type.getName());
        }
        if (!f.getDeclaringClass().isAssignableFrom(type)) {
            throw new IllegalArgumentException(f + " is not part of " + type.getName());
        }
        attributes.put(name, new FieldDescription(lookup, f, name));
    }

    public void foreach(Consumer<AttributeDescription> consumer) {
        attributes.values().forEach(consumer);
    }

    private static class FieldDescription implements AttributeDescription {
        private final MethodHandles.Lookup lookup;
        private final String name;
        private final Field f;

        FieldDescription(MethodHandles.Lookup lookup, Field f, String name) {
            this.lookup = lookup;
            this.name = name;
            this.f = f;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Class<?> type() {
            return f.getType();
        }

        @Override
        public MethodHandle createGetter() {
            return Methods.findGetter(lookup, f);
        }

        @Override
        public MethodHandle createSetter() {
            return Methods.findSetter(lookup, f);
        }

        @Override
        public <A extends Annotation> A get(Class<A> annotationType) {
            return f.getAnnotation(annotationType);
        }
    }
}
