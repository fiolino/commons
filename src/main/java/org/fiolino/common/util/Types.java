package org.fiolino.common.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;

/**
 * Static class for checking type access and retrieving information from generic types.
 *
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
public class Types {
    private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_WRAPPER_CLASSES
            = new HashMap<>();

    private static final Map<Class<?>, Class<?>> WRAPPERS_TO_PRIMITIVES
            = new HashMap<>();

    static {
        PRIMITIVES_TO_WRAPPER_CLASSES.put(boolean.class, Boolean.class);
        PRIMITIVES_TO_WRAPPER_CLASSES.put(char.class, Character.class);
        PRIMITIVES_TO_WRAPPER_CLASSES.put(byte.class, Byte.class);
        PRIMITIVES_TO_WRAPPER_CLASSES.put(short.class, Short.class);
        PRIMITIVES_TO_WRAPPER_CLASSES.put(int.class, Integer.class);
        PRIMITIVES_TO_WRAPPER_CLASSES.put(long.class, Long.class);
        PRIMITIVES_TO_WRAPPER_CLASSES.put(float.class, Float.class);
        PRIMITIVES_TO_WRAPPER_CLASSES.put(double.class, Double.class);
        PRIMITIVES_TO_WRAPPER_CLASSES.put(void.class, Void.class);

        PRIMITIVES_TO_WRAPPER_CLASSES.forEach(WRAPPERS_TO_PRIMITIVES::put);
    }

    /**
     * Checks whether the (possible) supertype is assignable from the (possible) subtype.
     * In contrast to Class.isAssignableFrom(), this also allows primitive types:
     * These are assignable if their corresponding wrapper type would be assignable as well.
     *
     * @param supertype Checks whether this really is a supertype
     * @param subtype   Checks whether this really is a subtype
     * @return True if it is so
     */
    public static boolean isAssignableFrom(Class<?> supertype, Class<?> subtype) {
        if (supertype.isAssignableFrom(subtype)) return true;
        if (supertype.isPrimitive()) {
            return subtype.isPrimitive() ? supertype.equals(subtype) : subtype.equals(PRIMITIVES_TO_WRAPPER_CLASSES.get(supertype));
        }
        return subtype.isPrimitive() && supertype.isAssignableFrom(PRIMITIVES_TO_WRAPPER_CLASSES.get(subtype));
    }

    /**
     * Gets the wrapper type, if the input is a primitive, or just returns the given input otherwise.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> toWrapper(Class<T> input) {
        if (!input.isPrimitive()) return input;
        return (Class<T>) PRIMITIVES_TO_WRAPPER_CLASSES.get(input);
    }

    /**
     * Gets the primitive type of the given argument is a wrapper, or null otherwise.
     */
    public static Class<?> asPrimitive(Class<?> wrapper) {
        return WRAPPERS_TO_PRIMITIVES.get(wrapper);
    }

    /**
     * Gets the distance of a subclass to a superclass.
     * Gets a negative distance if subclass and superclass are reversed, and throws a
     * NotAssignableException if the subclass and the superclass are not related at all.
     *
     * @param subType   The sub type
     * @param superType The super type; either a direct superclass, or an interface
     * @return The distance in classes; 0 means subtype and supertype are equal
     */
    public static int getDistance(Class<?> subType, Class<?> superType) {
        if (!superType.isAssignableFrom(subType)) {
            if (subType.isAssignableFrom(superType)) {
                return -distanceOf(superType, subType);
            }
            throw new NotAssignableException(subType.getName() + " is not a subclass of " + superType.getName());
        }
        return distanceOf(subType, superType);
    }

    /**
     * Gets the distance of a subclass to a superclass.
     * Returns -1 if the given subclass is not a subclass of the given
     * superclass at all.
     *
     * @param subType   The sub type
     * @param superType The super type; either a direct superclass, or an interface
     * @return The distance in classes; 0 means subtype and supertype are equal
     */
    public static int distanceOf(Class<?> subType, Class<?> superType) {
        if (subType == null) {
            // Then subType was an interface, and its superclasses were analyzed
            return superType.equals(Object.class) ? 1 : -1;
        }
        if (subType.equals(superType)) return 0;

        if (!superType.isAssignableFrom(subType)) {
            return -1;
        }
        if (superType.isInterface()) {
            for (Class<?> intface : subType.getInterfaces()) {
                int distance = distanceOf(intface, superType);
                if (distance >= 0) {
                    return distance + 1;
                }
            }
            throw new AssertionError(subType.getName() + " is subclass of interface " + superType.getName() + ", but no interface is directly accessible.");
        }
        int ofSuperclass = distanceOf(subType.getSuperclass(), superType);
        if (ofSuperclass >= 0) {
            return ofSuperclass + 1;
        }
        throw new AssertionError(subType.getName() + " is subclass of class " + superType.getName() + ", but direct class to getSuperclass() can't find it.");
    }

    public static Class<?> getRawType(Type type) {
        return getRawType(type, Bounded.EXACT);
    }

    public static Class<?> getRawType(Type type, Bounded b) {
        if (type instanceof Class) return toWrapper((Class<?>) type);
        if (type instanceof ParameterizedType) {
            return getRawType(((ParameterizedType) type).getRawType(), b);
        }
        if (type instanceof WildcardType) {
            Type[] bounds = b.getBounds((WildcardType) type);
            if (bounds.length == 1) {
                return getRawType(bounds[0], b);
            }
            throw new IllegalArgumentException("Type " + type + " has multiple bounds");
        }
        throw new IllegalArgumentException("Cannot extract raw class type from " + type);
    }

    public enum Bounded {
        UPPER {
            @Override
            Type[] getBounds(WildcardType t) {
                return t.getUpperBounds();
            }
        },
        LOWER {
            @Override
            Type[] getBounds(WildcardType t) {
                return t.getLowerBounds();
            }
        },
        EXACT {
            @Override
            Type[] getBounds(WildcardType t) {
                throw new IllegalStateException("Type " + t + " is not exact.");
            }
        };

        abstract Type[] getBounds(WildcardType t);
    }

    public static Type getGenericArgument(Type type, Class<?> reference, int index) {
        Type arg = getGenericArgument0(type, reference, index);
        if (arg == null) {
            throw new NotAssignableException("Type " + type + " is not assignable from " + reference);
        }
        return arg;
    }

    public static Class<?> getRawArgument(Type type, Class<?> reference, int index, Bounded b) {
        return getRawType(getGenericArgument(type, reference, index), b);
    }

    private static Type getGenericArgument0(Type type, Class<?> reference, int index) {
        Class<?> rawType = getRawType(type);
        if (rawType.equals(reference)) {
            return getGenericArgumentOf(type, index, null, null);
        }
        if (!reference.isAssignableFrom(rawType)) return null;

        Type supertype = rawType.getGenericSuperclass();
        if (supertype != null) {
            // Can be null if interface
            Type supertypeArgument = getGenericArgument0(supertype, reference, index);
            if (supertypeArgument != null) {
                return mapArgument(supertypeArgument, type, rawType);
            }
        }
        for (Type interfaceType : rawType.getGenericInterfaces()) {
            Type interfaceArgument = getGenericArgument0(interfaceType, reference, index);
            if (interfaceArgument != null) {
                return mapArgument(interfaceArgument, type, rawType);
            }
        }
        return null;
    }

    private static Type mapArgument(Type argument, Type caller, Class<?> rawCaller) {
        if (argument instanceof TypeVariable) {
            int mappedIndex = getVariableIndex(rawCaller, ((TypeVariable<?>) argument).getName());
            Type[] upperBounds = ((TypeVariable<?>) argument).getBounds();

            AnnotatedType[] boundAnnotations = ((TypeVariable<?>) argument).getAnnotatedBounds();
            return getGenericArgumentOf(caller, mappedIndex, upperBounds, boundAnnotations);
        }
        return argument;
    }

    private static Type getGenericArgumentOf(Type type, int index, Type[] upperBounds, AnnotatedType[] boundAnnotations) {
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            if (index >= arguments.length) {
                throw new IllegalArgumentException("Type " + type + " has only " + arguments.length + " arguments, but #" + index + " is requested.");
            }
            return wrapWithBounds(arguments[index], upperBounds, boundAnnotations);
        }
        throw new IllegalArgumentException("Type " + type + " is not parameterized.");
    }

    private static Type wrapWithBounds(Type t, Type[] upperBounds, AnnotatedType[] boundAnnotations) {
        if (upperBounds == null || upperBounds.length == 0) return t;

        if (t instanceof TypeVariable) {
            if (t instanceof BoundedTypeVariable) t = ((BoundedTypeVariable<?>) t).wrapped;
            Type[] thisUpperBounds = ((TypeVariable<?>) t).getBounds();
            if (isAnyBelow(upperBounds, thisUpperBounds)) return t;
            return wrapTypeVariable((TypeVariable<?>) t, upperBounds, boundAnnotations);

        }
        if (t instanceof WildcardType) {
            if (t instanceof BoundedWildcardType) t = ((BoundedWildcardType) t).wrapped;
            Type[] thisUpperBounds = ((WildcardType) t).getUpperBounds();
            if (isAnyBelow(upperBounds, thisUpperBounds)) return t;
            return new BoundedWildcardType((WildcardType) t, upperBounds);
        }
        return t;
    }

    private static <D extends GenericDeclaration> TypeVariable<D> wrapTypeVariable(
            TypeVariable<D> var, Type[] upperBounds, AnnotatedType[] boundAnnotations) {
        return new BoundedTypeVariable<>(var, upperBounds, boundAnnotations);
    }

    private static boolean isBelow(Type toCheck, Type[] bounds) {
        Class<?> r = getRawType(toCheck, Bounded.UPPER);
        for (Type b : bounds) {
            Class<?> rawBound = getRawType(b, Bounded.UPPER);
            if (r.isAssignableFrom(rawBound)) return true;
        }
        return false;
    }

    private static boolean isAnyBelow(Type[] toCheck, Type[] bounds) {
        for (Type t : toCheck) {
            if (isBelow(t, bounds)) return true;
        }
        return false;
    }

    private static int getVariableIndex(GenericDeclaration type, String variableName) {
        TypeVariable<?>[] vars = type.getTypeParameters();
        int i = 0;
        for (TypeVariable<?> v : vars) {
            if (v.getName().equals(variableName)) return i;
            i++;
        }
        throw new IllegalStateException("Variable " + variableName + " not found in " + type);
    }

    private static abstract class BoundedType {
        final Type[] upperBounds;

        BoundedType(Type[] upperBounds) {
            this.upperBounds = upperBounds;
        }

        String allBounds() {
            StringBuilder builder = new StringBuilder();
            builder.append(upperBounds[0].toString());
            for (int i = 1; i < upperBounds.length; i++) {
                builder.append(", ").append(upperBounds[i].toString());
            }
            return builder.toString();
        }
    }


    private static class BoundedTypeVariable<D extends GenericDeclaration> extends BoundedType implements TypeVariable<D> {
        final TypeVariable<D> wrapped;
        final AnnotatedType[] annotatedTypes;

        BoundedTypeVariable(TypeVariable<D> wrapped, Type[] upperBounds, AnnotatedType[] annotatedTypes) {
            super(upperBounds);
            this.wrapped = wrapped;
            this.annotatedTypes = annotatedTypes;
        }

        @Override
        public Type[] getBounds() {
            return upperBounds;
        }

        @Override
        public D getGenericDeclaration() {
            return wrapped.getGenericDeclaration();
        }

        @Override
        public String getName() {
            return wrapped.getName();
        }

        // @Override
        public AnnotatedType[] getAnnotatedBounds() {
            return annotatedTypes;
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return wrapped.getAnnotation(annotationClass);
        }

        @Override
        public Annotation[] getAnnotations() {
            return wrapped.getAnnotations();
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return wrapped.getDeclaredAnnotations();
        }

        @Override
        public String toString() {
            return getName() + " extends " + allBounds();
        }
    }

    private static class BoundedWildcardType extends BoundedType implements WildcardType {
        final WildcardType wrapped;

        public BoundedWildcardType(WildcardType wrapped, Type[] upperBounds) {
            super(upperBounds);
            this.wrapped = wrapped;
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds;
        }

        @Override
        public Type[] getLowerBounds() {
            return wrapped.getLowerBounds();
        }

        @Override
        public String toString() {
            return wrapped.toString() + " extends " + allBounds();
        }

    }
}
