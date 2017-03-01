package org.fiolino.common.reflection;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Methods annotated with this will be used for converting values.
 * <p>
 * Created by Kuli on 6/21/2016.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Converter {
}
