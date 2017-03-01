package org.fiolino.common.analyzing;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Created by kuli on 02.08.16.
 */
public abstract class AnnotationWalker<A extends Annotation> {
    private final Class<A> type;
    private AnnotationWalker<?> next;

    public AnnotationWalker(Class<A> type) {
        this.type = type;
    }

    public final void andThen(AnnotationWalker<?> next) {
        if (this.next == null) {
            this.next = next;
        } else {
            this.next.andThen(next);
        }
    }

    protected boolean allowModifiers(int modifiers) {
        return !Modifier.isStatic(modifiers);
    }

    protected void visitField(Field f, A annotation) {
        // Do nothing
    }

    protected void visitConstructor(java.lang.reflect.Constructor<?> c, A annotation, A[] parameterAnnotations) {
        // Do nothing
    }

    protected void visitMethod(Method m, A annotation, A[] parameterAnnotations) {
        // Do nothing
    }

    public static <B extends Annotation> B getParameterAnnotation(Class<B> type, Annotation... annotations) {
        for (Annotation a : annotations) {
            if (type.isInstance(a)) {
                return type.cast(a);
            }
        }
        return null;
    }

    public final int visit(Class<?> model) {
        return visit(model, 0);
    }

    private int visit(Class<?> model, int count) {
        int c = visit0(model, count);
        if (next == null) {
            return c;
        }
        return next.visit(model, c);
    }

    private int visit0(Class<?> model, int count) {
        if (model == null || model == Object.class) {
            return count;
        }
        int c = visit0(model.getSuperclass(), count);
        for (Class<?> i : model.getInterfaces()) {
            c = visit0(i, c);
        }

        for (Field f : model.getDeclaredFields()) {
            if (!allowModifiers(f.getModifiers())) {
                continue;
            }
            A anno = f.getAnnotation(type);
            if (anno != null) {
                c++;
                visitField(f, anno);
            }
        }

        for (Method m : model.getDeclaredMethods()) {
            if (!allowModifiers(m.getModifiers())) {
                continue;
            }
            A[] annotations = examine(m, m.getParameterAnnotations());
            if (annotations != null) {
                c++;
                visitMethod(m, m.getAnnotation(type), annotations);
            }
        }

        for (java.lang.reflect.Constructor<?> x : model.getConstructors()) {
            A[] annotations = examine(x, x.getParameterAnnotations());
            if (annotations != null) {
                c++;
                visitConstructor(x, x.getAnnotation(type), annotations);
            }
        }

        return c;
    }

    private A[] examine(AnnotatedElement x, Annotation[][] parameterAnnotations) {
        boolean annotated = x.isAnnotationPresent(type);
        int n = parameterAnnotations.length;
        @SuppressWarnings("unchecked")
        A[] parameters = (A[]) Array.newInstance(type, n);
        for (int i = 0; i < n; i++) {
            A a = getParameterAnnotation(type, parameterAnnotations[i]);
            if (a != null) {
                annotated = true;
                parameters[i] = a;
            }
        }
        return annotated ? parameters : null;
    }
}
