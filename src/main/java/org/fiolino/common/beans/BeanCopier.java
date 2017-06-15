package org.fiolino.common.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.fiolino.common.analyzing.ClassWalker;
import org.fiolino.common.reflection.Methods;
import org.fiolino.common.util.Types;

import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 20.02.16.
 */
public final class BeanCopier<S, T> {
    private static final Logger logger = Logger.getLogger(BeanCopier.class.getName());

    private static class Builder {

        private final FieldMatcher matcher;
        private MethodHandle current;
        private final MethodHandles.Lookup sourceLookup, targetLookup;
        private final Class<?> sourceType, targetType;
        private final List<String> fields = new ArrayList<>();

        public Builder(MethodHandles.Lookup lookup, Class<?> source, Class<?> target, FieldMatcher matcher) {
            this.sourceLookup = lookup.in(source);
            this.targetLookup = lookup.in(target);
            this.matcher = matcher;
            this.sourceType = source;
            this.targetType = target;

            copyFromTo(source, target);
        }

        MethodHandle getCopier() {
            return current.asType(methodType(void.class, Object.class, Object.class));
        }

        MethodHandle getFactory() {
            MethodHandle constructor;
            try {
                constructor = targetLookup.findConstructor(targetType, methodType(void.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                return null;
            }

            MethodHandle copier = Methods.returnArgument(current, 0).
                    asType(methodType(Object.class, targetType, Object.class));
            return MethodHandles.foldArguments(copier, constructor);
        }

        List<String> getFields() {
            return fields;
        }

        void copyFromTo(Field source, Field target) {
            MethodHandle getter = Methods.findGetter(sourceLookup, source);
            if (getter == null) {
                logger.log(Level.WARNING, () -> "No getter for " + source);
                return;
            }
            MethodHandle setter = Methods.findSetter(targetLookup, target);
            if (setter == null) {
                logger.log(Level.WARNING, () -> "No setter for " + target);
                return;
            }

            Class<?> gettersOwner = getter.type().parameterType(0);
            Class<?> gettersValue = getter.type().returnType();
            Class<?> settersValue = setter.type().parameterType(1);
            setter = setter.asType(setter.type().changeParameterType(1, gettersValue));
            if (!gettersValue.isPrimitive()) {
                if (!Types.isAssignableFrom(settersValue, gettersValue)) {
                    // Then the getter's value is a superclass of the setter's value
                    MethodHandle instanceOf = Methods.instanceCheck(Types.toWrapper(settersValue));
                    setter = Methods.invokeOnlyIfArgument(setter, 1, instanceOf);
                } else {
                    setter = Methods.secureNull(setter, 1);
                }
            }
            Class<?> settersOwner = setter.type().parameterType(0);
            MethodHandle transporter = MethodHandles.filterArguments(setter, 1, getter);
            if (current == null) {
                current = transporter;
            } else {
                current = MethodHandles.foldArguments(
                        transporter,
                        current.asType(methodType(void.class, settersOwner, gettersOwner)));
            }
        }

        private void copyFromTo(final Class<?> source, final Class<?> target) {
            ClassWalker<RuntimeException> targetWalker = new ClassWalker<>();
            targetWalker.onField(f -> {
                if (Modifier.isStatic(f.getModifiers())) {
                    return;
                }
                Field sourceField = findBestSource(f, source);
                if (sourceField == null) {
                    return;
                }
                fields.add(sourceField.getName() + "->" + f.getName());
                copyFromTo(sourceField, f);
            });
            targetWalker.analyze(target);
        }

        private static class BestMatch {
            Field field;
            int rank;
        }

        private Field findBestSource(final Field targetField, final Class<?> source) {
            final BestMatch bestMatch = new BestMatch();
            ClassWalker<RuntimeException> sourceWalker = new ClassWalker<>();
            sourceWalker.onField(f -> {
                if (Modifier.isStatic(f.getModifiers())) {
                    return;
                }
                if (!canMatch(f, targetField)) {
                    return;
                }
                int r = matcher.rank(f, targetField);
                if (r <= bestMatch.rank) {
                    return;
                }
                bestMatch.rank = r;
                bestMatch.field = f;
            });
            sourceWalker.analyze(source);

            return bestMatch.field;
        }

        private boolean canMatch(Field sourceField, Field targetField) {
            // @TODO check generics
            return Types.isAssignableFrom(targetField.getType(), sourceField.getType())
                    || Types.isAssignableFrom(sourceField.getType(), targetField.getType());
        }
    }

    private final MethodHandle copier, factory;
    private final Class<T> targetType;

    private BeanCopier(MethodHandles.Lookup lookup, Class<S> source, Class<T> target, FieldMatcher fieldMatcher) {
        this.targetType = target;

        Builder b = new Builder(lookup, source, target, fieldMatcher);
        List<String> fields = b.getFields();
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Copying from " + source.getName() + " to " + target.getName()
                    + " would copy not a single field!");
        }

        StringBuilder sb = new StringBuilder().append("Will copy ").append(fields.size()).append(" fields from ").append(
                source.getName()).append(" to ").append(target.getName()).append(":");
        for (String f : fields) {
            sb.append(' ').append(f);
        }

        logger.info(sb.toString());

        copier = b.getCopier();
        factory = b.getFactory();

    }

    public static <S, T> BeanCopier<S, T> copyFromTo(Class<S> source, Class<T> target) {
        return copyFromTo(MethodHandles.publicLookup().in(source), source, target);
    }

    public static <S, T> BeanCopier<S, T> copyFromTo(MethodHandles.Lookup lookup, Class<S> source, Class<T> target) {
        return copyFromTo(lookup, source, target, FieldMatcher.SAME_NAME);
    }

    public static <S, T> BeanCopier<S, T> copyFromTo(Class<S> source, Class<T> target, FieldMatcher matcher) {
        return copyFromTo(MethodHandles.publicLookup(), source, target, matcher);
    }

    public static <S, T> BeanCopier<S, T> copyFromTo(MethodHandles.Lookup lookup, Class<S> source, Class<T> target, FieldMatcher matcher) {
        return new BeanCopier<>(lookup, source, target, matcher);
    }

    public void copyFromTo(S source, T target) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (target == null) {
            throw new NullPointerException("target");
        }
        try {
            copier.invokeExact(target, source);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void checkConstructorAccess() {
        if (factory == null) {
            throw new AssertionError("Cannot create " + targetType.getName()
                    + " since no public empty construtor was accessible.");
        }
    }

    public T transform(S source) {
        checkConstructorAccess();
        if (source == null) {
            throw new NullPointerException("source");
        }
        try {
            return targetType.cast(factory.invokeExact(source));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
