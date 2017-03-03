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
public @interface Provider {

    /**
     * If this is set to true, then this is an optional provider for that type, which means it can return null.
     * In that case, the previously installed provider will be invoked, if there is any, or the bean's constructor.
     *
     * The same happens if the provider method is marked with {@link javax.annotation.Nullable}.
     */
    boolean optional() default false;
}
