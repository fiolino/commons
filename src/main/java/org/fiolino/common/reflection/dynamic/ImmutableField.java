package org.fiolino.common.reflection.dynamic;

import java.lang.reflect.Modifier;

/**
 * Created by kuli on 11.04.17.
 */
public class ImmutableField extends InstanceField {
    private final int constructorIndex;

    ImmutableField(DynamicClassBuilder<?> builder, Class<?> type, int constructorIndex) {
        super(builder, type);
        this.constructorIndex = constructorIndex;
    }

    @Override
    int modifiers() {
        return Modifier.FINAL;
    }
}
