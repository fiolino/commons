package org.fiolino.common.reflection;

import javax.annotation.Nullable;

/**
 * Interface to find some converting values from one type to another.
 *
 * Created by Kuli on 6/17/2016.
 */
public interface ConverterLocator {

    /**
     * Finds a MethodHandle that converts from the source type to some target type.
     * <p>
     * The handle will accept exactly one argument matching the source, and returns a result as one of the target types.
     * <p>
     * The resulting handle is not necessarily of the exact same type. Possibly the handle needs some explicit type case.
     * <p>
     * If the types can be casted on their own, and there is no converter for the explicit conversion,
     * then null is returned whoch means the caller should just use asType() or something like that.
     * <p>
     * If there is no matching converter and the types don't match, a {@link NoMatchingConverterException} is thrown.
     *
     * @param source The input type
     * @param targets The output type; must be at least one
     * @return A MethodHandle of type (&lt;source&gt;)&lt;target&gt;, where source may also be a more generic
     * type, and target may be a more specific type.
     */
    Converter find(Class<?> source, Class<?>... targets);
}
