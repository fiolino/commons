package org.fiolino.common.analyzing;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by kuli on 26.11.15.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AnnotationInterest {

    /**
     * The priority when this method shall be executed.
     */
    Priority value();

    /**
     * The annotation to which I show interest.
     */
    Class<? extends Annotation> annotation() default Annotation.class;

    /**
     * A list of the elements that should be analyzed for this interest.
     */
    AnalyzedElement[] elements() default {};
}
