package org.fiolino.common.reflection.dynamic;

import java.lang.reflect.Modifier;

/**
 * Created by kuli on 11.04.17.
 */
public class MutableField extends InstanceField {
    MutableField(DynamicClassBuilder<?> builder, Class<?> type) {
        super(builder, type);
    }

    @Override
    int modifiers() {
        return 0;
    }
}
