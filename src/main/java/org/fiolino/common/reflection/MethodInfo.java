package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

public interface MethodInfo extends AnnotatedElement, Member {
    Method getMethod();

    String getName();

    MethodHandle getHandle();
}
