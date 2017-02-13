package org.fiolino.common.reflection;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Created by Michael Kuhlmann on 14.12.2015.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ExecuteDirect {
}
