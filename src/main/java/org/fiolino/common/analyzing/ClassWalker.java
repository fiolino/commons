package org.fiolino.common.analyzing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Analyzes classes to find annotated elements (fields or methods).
 *
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
public class ClassWalker<E extends Exception> {

    private final List<ClassVisitor<E>> visitors = new ArrayList<>();

    public static final class ClassVisitor<E extends Exception> {
        private final Predicate<? super Class<?>> classTest;
        private final List<Consumer<? super Field>> fieldActions = new ArrayList<>();
        private final List<Consumer<? super Method>> methodActions = new ArrayList<>();

        private ClassVisitor(Predicate<? super Class<?>> classTest) {
            this.classTest = classTest;
        }

        private ClassVisitor() {
            this(c -> true);
        }

        private boolean startClass(Class<?> type) {
            return classTest.test(type);
        }

        private void visitField(Field f) {
            for (Consumer<? super Field> c : fieldActions) {
                c.accept(f);
            }
        }

        private void visitMethod(Method m) {
            for (Consumer<? super Method> c : methodActions) {
                c.accept(m);
            }
        }

        public ClassVisitor<E> onField(Consumer<? super Field> action) {
            fieldActions.add(action);
            return this;
        }

        public ClassVisitor<E> onMethod(Consumer<? super Method> action) {
            methodActions.add(action);
            return this;
        }
    }

    private ClassVisitor<E> addVisitor(ClassVisitor<E> visitor) {
        visitors.add(visitor);
        return visitor;
    }

    public ClassVisitor<E> forClasses(Predicate<? super Class<?>> classTest) {
        return addVisitor(new ClassVisitor<E>(classTest));
    }

    public ClassVisitor<E> onField(Consumer<? super Field> action) {
        return addVisitor(new ClassVisitor<E>()).onField(action);
    }

    public ClassVisitor<E> onMethod(Consumer<? super Method> action) {
        return addVisitor(new ClassVisitor<E>()).onMethod(action);
    }

    public void analyze(Class<?> model) throws E {
        for (ClassVisitor<E> v : visitors) {
            if (v.startClass(model)) {
                analyzeUsing(model, v);
            }
        }
    }

    private void analyzeUsing(Class<?> modelClass, ClassVisitor<E> visitor) throws E {
        Class<?> supertype = modelClass.getSuperclass();
        if (supertype != null && supertype != Object.class) {
            analyzeUsing(supertype, visitor);
        }

        for (Field f : modelClass.getDeclaredFields()) {
            visitor.visitField(f);
        }
        for (Method m : modelClass.getDeclaredMethods()) {
            visitor.visitMethod(m);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " with " +
                visitors.size() + " visitors.";
    }

}
