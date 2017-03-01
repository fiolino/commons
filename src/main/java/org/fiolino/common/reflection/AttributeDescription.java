package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;

/**
 * Created by Kuli on 6/17/2016.
 */
public interface AttributeDescription extends AnnotationProvider {

    String name();

    Class<?> type();

    MethodHandle createGetter();

    MethodHandle createSetter();
}
