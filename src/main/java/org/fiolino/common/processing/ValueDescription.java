package org.fiolino.common.processing;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Type;

import org.fiolino.common.analyzing.ModelInconsistencyException;

import javax.annotation.Nullable;

/**
 * Created by kuli on 11.02.15.
 */
public interface ValueDescription extends ConfigurationContainer {

    /**
     * The name of the field. If this was a method, the extracted name is returned -- e.g., for getName(), the result would be "name".
     */
    String getName();

    /**
     * The owning model, i.e. the model of the containing class.
     */
    ModelDescription owningModel();

    /**
     * The raw class of the given field.
     */
    Class<?> getValueType();

    /**
     * The generic type; useful for {@link org.fiolino.common.util.Types} utility class.
     */
    Type getGenericType();

    /**
     * The type of the target relation, either single or multi - actually the inner generic type of a {@link java.util.Collection}, or the same as getValueType() otherwise.
     */
    Class<?> getTargetType() throws ModelInconsistencyException;

    /**
     * Gets the annotation of a certain type.
     */
    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    /**
     * Create a getter handle.
     * If there is no getter method, null is returned.
     * If a method is annotated, then this is returned if it is a getter, or null otherwise.
     *
     * @param additionalTypes The returned handle will accept these types on top. Either the underlying method really
     *                        specifies them, or they're ignored.
     * @return handle accepts the owner plus some of the the additional types, and returns the result of getValueType().
     */
    @Nullable
    MethodHandle createGetter(Class<?>... additionalTypes);

    /**
     * Create a setter handle.
     * If there is no setter method, null is returned.
     * If a method is annotated, then this is returned if it is a setter, or null otherwise.
     *
     * @param additionalTypes The returned handle will accept these types on top. Either the underlying method really
     *                        specifies them, or they're ignored.
     * @return void handle accepts the owner, the new value of type getValueType(), plus some of the the additional types.
     */
    @Nullable
    MethodHandle createSetter(Class<?>... additionalTypes);

    /**
     * Gets a model description of the target type.
     */
    ModelDescription getRelationTarget() throws ModelInconsistencyException;
}
