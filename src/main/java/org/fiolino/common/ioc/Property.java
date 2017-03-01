package org.fiolino.common.ioc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by kuli on 26.03.15.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Property {
    /**
     * The property key
     */
    String value();

    /**
     * The default value, if nothing is configured.
     */
    String defaultValue() default "";
}
