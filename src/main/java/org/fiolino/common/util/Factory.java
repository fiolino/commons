package org.fiolino.common.util;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Methods marked with this annotation will be used as a factory for the returned type.
 *
 * Created by kuli on 02.03.17.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Factory {
}
