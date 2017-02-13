package org.fiolino.common.reflection;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks methods that created gtheir own {@link java.lang.invoke.MethodHandle}.
 * <p>
 * Each method can either have a Class argument, expecting the possible return type,
 * or no argument if the handle just creates exactly one type.
 * <p>
 * If the type argument is expected, then the value() must be specified.
 *
 * Created by Michael Kuhlmann on 14.12.2015.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface MethodHandleFactory {
  /**
   * The top level result type.
   */
  Class<?> value() default void.class;
}
