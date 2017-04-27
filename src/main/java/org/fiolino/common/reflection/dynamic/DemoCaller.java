package org.fiolino.common.reflection.dynamic;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Date;
import java.util.function.Function;

/**
 * Created by kuli on 10.04.17.
 */
public class DemoCaller implements Function<String, Date>, Serializable {

    private static final MethodHandle HANDLE = DynamicClassBuilder.getHandle(1);
    private static final long serialVersionUID = -3238960673429121707L;

    static {
        System.out.println("Hello!");
    }

    final Object target;
    final String name;

    DemoCaller(Object target, String name) {
        this.target = target;
        this.name = name;
    }

    @Override
    public Date apply(String s) {
        try {
            return (Date) HANDLE.invokeExact(target, name, s);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
    }

    @Override
    public String toString() {
        return name + " Function";
    }
}
