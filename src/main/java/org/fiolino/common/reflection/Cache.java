package org.fiolino.common.reflection;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;

/**
 * Created by kuli on 12.07.17.
 */
public interface Cache extends Registry {
    CallSite getCallSite();

    default MethodHandle getAccessor() {
        return getCallSite().dynamicInvoker();
    }
}
