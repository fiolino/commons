package org.fiolino.common.reflection.dynamic;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import org.fiolino.common.reflection.Methods;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 06.04.17.
 */
public class DynamicClassBuilder<T> {

    private final DynamicType.Builder<T> buddy;
    private Class<?> result;
    private final List<Field> fields = new ArrayList<>();

    public DynamicClassBuilder(Class<T> superclass, BootstrapVisitor visitor) {
        this.buddy = new ByteBuddy().subclass(superclass);

    }

    private void iterate(Class<?> clazz, BootstrapVisitor visitor) {
        Methods.visitAllMethods(publicLookup(), clazz, null, (v, m, f) -> {
            Implementation implementation = visitor.onMethod(clazz, m.getName(), methodType(m.getReturnType(), m.getParameterTypes()), Modifier.isAbstract(m.getModifiers()));
            if (implementation == null) return null;

        });
    }

    Class<?> createdClass() {
        if (result == null) {
            throw new IllegalStateException("Result class not yet created!");
        }

        return result;
    }

    static MethodHandle getHandle(int index) {
        return null;
    }

    MethodHandle createGetterFor(Field field) {
        return null;
    }

    public Constant createConstant(Class<?> type, Object value) {
        Constant constant = new Constant(this, type, value);
        fields.add(constant);
        return constant;
    }
}
