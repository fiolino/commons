package org.fiolino.common.reflection.dynamic;

/**
 * Created by kuli on 07.04.17.
 */
public abstract class Field extends Argument {
    private String name;
    private final Class<?> type;
    final DynamicClassBuilder<?> builder;

    Field(DynamicClassBuilder<?> builder, Class<?> type) {
        this.builder = builder;
        this.type = type;
    }

    public Class<?> getType() {
        return type;
    }

    public void setName(String name) {
        this.name = name;
    }

    abstract int modifiers();
}
