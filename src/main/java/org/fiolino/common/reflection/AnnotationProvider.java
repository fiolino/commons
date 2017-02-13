package org.fiolino.common.reflection;

import java.lang.annotation.Annotation;

/**
 * Created by Kuli on 6/17/2016.
 */
public interface AnnotationProvider {

  /**
   * Gets the annotation of a specific type.
   *
   * @param annotationType The type to choose
   * @param <A> The type
   * @return The matching annotation, or null if it does not exist there
   */
  <A extends Annotation> A get(Class<A> annotationType);
}
