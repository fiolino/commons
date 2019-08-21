package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

public interface MethodInfo extends AnnotatedElement {
    Method getMethod();

    String getName();

    MethodHandle getHandle();
}
