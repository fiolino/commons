package org.fiolino.common.util;

import java.lang.invoke.MethodHandle;

/**
 * Created by kuli on 08.03.17.
 */
public interface MapInjector {

    /**
     * If this type is eligible for my type of map, return a MethodHandle of type
     * @param type
     * @return
     */
    MethodHandle mapFactory(Class<?> type);
}
