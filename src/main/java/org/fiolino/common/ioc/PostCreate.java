package org.fiolino.common.ioc;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotate methods that shall be invoked after calling the constructor.
 * If multiple methods are annotated, the order is undefined.
 *
 * The method must be parameter-less or may accept the created instance as the only parameter if it is static.
 *
 * The method must return void, or an instance of the created type which will be replaced then.
 *
 * Created by kuli on 23.02.17.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface PostCreate {
}
