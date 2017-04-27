package org.fiolino.common.reflection.dynamic;

import net.bytebuddy.implementation.Implementation;

import java.lang.invoke.MethodType;

/**
 * Created by kuli on 24.04.17.
 */
public interface BootstrapVisitor {

    Implementation onMethod(Class<?> owner, String name, MethodType type, boolean isMandatory);
}
