package org.fiolino.common.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Created by Kuli on 6/17/2016.
 */
public final class Reflection {
    private Reflection() {
        throw new AssertionError();
    }

    static class AnnotationProviderAdapter implements AnnotationProvider {
        private final AnnotatedElement element;

        AnnotationProviderAdapter(AnnotatedElement element) {
            this.element = element;
        }

        @Override
        public <A extends Annotation> A get(Class<A> annotationType) {
            return element.getAnnotation(annotationType);
        }

        @Override
        public String toString() {
            return "Provides annotations from " + element;
        }
    }

    static class CombinedAnnotationProvider implements AnnotationProvider {
        private final AnnotationProvider first;
        private final AnnotationProvider second;

        CombinedAnnotationProvider(AnnotationProvider first, AnnotationProvider second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public <A extends Annotation> A get(Class<A> annotationType) {
            A annotation = first.get(annotationType);
            return annotation == null ? second.get(annotationType) : annotation;
        }

        @Override
        public String toString() {
            return "Combines annotation from " + first + " and " + second;
        }
    }

    public static void visitFields(Class<?> type,
                                   AttributeChooser chooser, Consumer<Field> consumer) {
        Class<?> c = type;
        do {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                AnnotationProvider provider = new AnnotationProviderAdapter(f);
                if (chooser.accepts(f.getName(), f.getType(), provider)) {
                    consumer.accept(f);
                }
            }
        } while ((c = c.getSuperclass()) != null);
    }

    private static final AttributeChooser CHOOSE_ALL = new AbstractAttributeChooser() {
        @Override
        public boolean accepts(String name, Class<?> type, AnnotationProvider annotationProvider) {
            return true;
        }
    };

    public static AttributeChooser chooseAll() {
        return CHOOSE_ALL;
    }

    public static AttributeChooser withPattern(String pattern) {
        final Pattern p = Pattern.compile(pattern);
        return new AbstractAttributeChooser() {
            @Override
            public boolean accepts(String name, Class<?> type, AnnotationProvider annotationProvider) {
                return p.matcher(name).matches();
            }
        };
    }

    public static AttributeChooser ofType(final Class<?> matchingType) {
        return new AbstractAttributeChooser() {
            @Override
            public boolean accepts(String name, Class<?> type, AnnotationProvider annotationProvider) {
                return matchingType.isAssignableFrom(type);
            }
        };
    }

    public static AttributeChooser annotatedWith(final Class<? extends Annotation> annoType) {
        return new AbstractAttributeChooser() {
            @Override
            public boolean accepts(String name, Class<?> type, AnnotationProvider annotationProvider) {
                return annotationProvider.get(annoType) != null;
            }
        };
    }


}
