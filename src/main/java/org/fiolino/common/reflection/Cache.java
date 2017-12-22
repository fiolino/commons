package org.fiolino.common.reflection;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;

/**
 * A cache is a Registry that can be used as an initializer in an #invokedymnamic call.
 *
 * Created by kuli on 12.07.17.
 */
public interface Cache extends Registry {
    /**
     * Gets the {@link CallSite} that is used to fetch the accessor handle.
     */
    CallSite getCallSite();

    default MethodHandle getAccessor() {
        return getCallSite().dynamicInvoker();
    }
}
