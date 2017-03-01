package org.fiolino.common.reflection;

/**
 * Created by Kuli on 6/17/2016.
 */
public interface AttributeChooser {

    boolean accepts(String name, Class<?> type, AnnotationProvider annotationProvider);

    AttributeChooser or(AttributeChooser alternative);

    AttributeChooser and(AttributeChooser alternative);

    AttributeChooser complement();
}
