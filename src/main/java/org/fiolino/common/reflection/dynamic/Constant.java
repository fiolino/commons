package org.fiolino.common.reflection.dynamic;

import java.lang.reflect.Modifier;

/**
 * Created by kuli on 11.04.17.
 */
public final class Constant extends Field {
    private final Object value;

    Constant(DynamicClassBuilder<?> builder, Class<?> type, Object value) {
        super(builder, type);
        this.value = value;
    }

    @Override
    int modifiers() {
        return Modifier.STATIC | Modifier.FINAL;
    }
}
