package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;

/**
 * Created by Kuli on 6/17/2016.
 */
public interface ConverterLocator {

    /**
     * Finds a MethodHandle that converts from the source type to the target type.
     * <p>
     * The handle will accept exactly one argument matching the source, and returns a result of the target type.
     * <p>
     * The resulting handle is not necessarily of the exact same type. Possibly the handle needs some explicit type case.
     * <p>
     * If the types can be casted on their own, and there is no converter for the explicit conversion,
     * then null is returned.
     * <p>
     * If there is no matching converter and the types don't match, a {@link NoMatchingConverterException} is thrown.
     *
     * @param source The input type
     * @param target The output type
     * @return A MethodHandle of type (&lt;source&gt;)&lt;target&gt;, where source may also be a more generic
     * type, and target may be a more specific type.
     */
    MethodHandle find(Class<?> source, Class<?> target);
}
