package org.fiolino.common.processing;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Type;

import org.fiolino.common.analyzing.ModelInconsistencyException;

/**
 * Created by kuli on 11.02.15.
 */
public interface ValueDescription extends ConfigurationContainer {

    String getName();

    ModelDescription owningModel();

    Class<?> getValueType();

    Type getGenericType();

    Class<?> getTargetType() throws ModelInconsistencyException;

    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    MethodHandle createGetter();

    MethodHandle createSetter();

    ModelDescription getRelationTarget() throws ModelInconsistencyException;
}
