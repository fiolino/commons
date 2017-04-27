package org.fiolino.common.reflection.dynamic;

import java.lang.invoke.MethodHandle;

/**
 * Created by kuli on 11.04.17.
 */
abstract class InstanceField extends Field {
    InstanceField(DynamicClassBuilder<?> builder, Class<?> type) {
        super(builder, type);
    }

    public MethodHandle createGetter() {
        return builder.createGetterFor(this);
    }
}
