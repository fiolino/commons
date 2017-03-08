package org.fiolino.common.util;

import java.lang.invoke.MethodHandle;

/**
 * Created by kuli on 07.03.17.
 */
public class RegistryBuilder {

    public Registry buildFor(MethodHandle target) {
        if (target.type().parameterCount() == 0) {
            return new OneTimeExecutor(target);
        }
        throw new UnsupportedOperationException();
    }

}
