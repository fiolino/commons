package org.fiolino.common.reflection.dynamic;

import net.bytebuddy.implementation.MethodCall;

/**
 * Created by kuli on 26.04.17.
 */
public abstract class Argument {
    abstract MethodCall insertArgument(MethodCall existing);
}
