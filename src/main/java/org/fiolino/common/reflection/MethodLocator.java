package org.fiolino.common.reflection;


import org.fiolino.common.util.Strings;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Instances of this try to find various methods inside a given class.
 * Only methods that are visible from the given {@link Lookup} are located.
 */
public final class MethodLocator {

    private final Lookup lookup;
    private final Class<?> type;

    private MethodLocator(Lookup lookup, Class<?> type) {
        this.lookup = lookup;
        this.type = type;
    }

    /**
     * Creates a Locator for the type in the given lookup.
     *
     * @param lookup This should be a native lookup object for a certain class
     * @return A new MethodLocator
     */
    public static MethodLocator forLocal(Lookup lookup) {
        return forLocal(lookup, lookup.lookupClass());
    }

    /**
     * Creates a Locator for a certain type, with the access restrictions of the given lookup.
     *
     * @param lookup Some lookup
     * @param type Look in this type
     * @return A new MethodLocator
     */
    public static MethodLocator forLocal(Lookup lookup, Class<?> type) {
        return new MethodLocator(lookup, type);
    }

    /**
     * Creates a Locator for a certain type, locating only public methods.
     *
     * @param type Look in this type
     * @return A new MethodLocator
     */
    public static MethodLocator forPublic(Class<?> type) {
        return new MethodLocator(publicLookup(), type);
    }

    /**
     * Gets the associated lookup instance.
     */
    public Lookup lookup() {
        return lookup;
    }

    /**
     * The type which gets examined.
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Finds the getter method for a given field.
     * Tries to find getters in this order:
     * 1. By looking up a method getFieldname with the certain return type
     * 2. If the field is of type boolean, tries to find a getter named isFieldname
     * 3. Looks for a getter method fieldname, i.e. with the exact same name as the field
     * 4. Tries to access the field directly through this lookup.
     * <p>
     * If none of these apply, then it returns null.
     *
     * @param field  The field
     * @param additionalTypes The handle will accept these on top
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;)&lt;type&gt;, or null otherwise
     */
    @Nullable
    public static MethodHandle findGetter(Field field, Class<?>... additionalTypes) {
        return findGetter(publicLookup(), field, additionalTypes);
    }

    /**
     * Finds the getter method for a given field.
     * Tries to find getters in this order:
     * 1. By looking up a method getFieldname with the certain return type
     * 2. If the field is of type boolean, tries to find a getter named isFieldname
     * 3. Looks for a getter method fieldname, i.e. with the exact same name as the field
     * 4. Tries to access the field directly through this lookup.
     * <p>
     * If none of these apply, then it returns null.
     *
     * @param lookup The lookup
     * @param field  The field
     * @param additionalTypes The handle will accept these on top
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;)&lt;type&gt;, or null otherwise
     */
    @Nullable
    public static MethodHandle findGetter(Lookup lookup, Field field, Class<?>... additionalTypes) {
        if (Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        Class<?> type = field.getType();
        Class<?> owner = field.getDeclaringClass();
        return forLocal(lookup, owner).findGetter(field.getName(), type, field, additionalTypes);
    }

    /**
     * Finds the getter method for a given field in my owner class.
     * Tries to find getters in this order:
     * 1. By looking up a method getFieldname with the certain return type
     * 2. If the field is of type boolean, tries to find a getter named isFieldname
     * 3. Looks for a getter method fieldname, i.e. with the exact same name as the field
     * 4. Tries to access the field of that name directly through this lookup.
     * <p>
     * If none of these apply, then it returns null.
     *
     * @param fieldName The plain field name
     * @param type      The getter's expected return type
     * @param additionalTypes The handle will accept these on top
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;)&lt;type&gt;, or null otherwise
     */
    @Nullable
    public MethodHandle findGetter(String fieldName, Class<?> type, Class<?>... additionalTypes) {
        return findGetter(fieldName, type, null, additionalTypes);
    }

    @Nullable
    private MethodHandle findGetter(String fieldName, Class<?> fieldType, Field field, Class<?>... additionalTypes) {
        String name = Strings.addLeading(fieldName, "get");
        return attachTo(onTop -> {
            String n = name;
            MethodHandle h = findAccessor(n, fieldType, onTop);
            if (h == null && (fieldType == boolean.class || fieldType == Boolean.class)) {
                n = fieldName;
                if (!n.startsWith("is") || n.length() > 2 && Character.isLowerCase(n.charAt(2))) {
                    n = Strings.addLeading(fieldName, "is");
                }
                h = findAccessor(n, fieldType, onTop);
            }
            if (h == null) {
                // Look for a method with the exact same name
                h = findAccessor(fieldName, fieldType, onTop);
            }
            return h;
        }, additionalTypes, () -> {
            // Look for the direct field getter
            try {
                return lookup.findGetter(type, fieldName, fieldType);
            } catch (NoSuchFieldException ex) {
                // Then there is no such field
                return null;
            } catch (IllegalAccessException ex) {
                // Try to make it accessible
                return tryUnreflectField(fieldName, fieldType, field, lookup::unreflectGetter, MethodType::returnType);
            }
        });
    }

    private MethodHandle attachTo(Function<Class<?>[], MethodHandle> handleFactory, Class<?>[] additionalTypes,
                                  Supplier<MethodHandle> alternative) {
        int missing = 0;
        MethodHandle handle;
        int n = additionalTypes.length;
        do {
            Class<?>[] onTop = Arrays.copyOf(additionalTypes, n-missing);
            handle = handleFactory.apply(onTop);
        } while (handle == null && missing++ < n);

        if (handle != null) {
            return handle;
        }

        return alternative.get();
    }

    /**
     * Finds the setter method for a given field.
     * Tries to find setters in this order:
     * 1. By looking up a method setFieldname with the certain argument type
     * 2. Looks for a setter method fieldname, i.e. with the exact same name as the field
     * 3. Tries to access the field directly through this lookup.
     * <p>
     * It will only look up virtual methods with void return type and the field's type as the
     * sole argument.
     * <p>
     * If none of these apply, then it returns null.
     *
     * @param field  The field to look for
     * @param additionalTypes The handle will accept these on top
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;,&lt;type&gt;)void, or null otherwise
     */
    @Nullable
    public static MethodHandle findSetter(Field field, Class<?>... additionalTypes) {
        return findSetter(publicLookup(), field, additionalTypes);
    }

    /**
     * Finds the setter method for a given field.
     * Tries to find setters in this order:
     * 1. By looking up a method setFieldname with the certain argument type
     * 2. Looks for a setter method fieldname, i.e. with the exact same name as the field
     * 3. Tries to access the field directly through this lookup.
     * <p>
     * It will only look up virtual methods with void return type and the field's type as the
     * sole argument.
     * <p>
     * If none of these apply, then it returns null.
     *
     * @param lookup The lookup
     * @param field  The field to look for
     * @param additionalTypes The handle will accept these on top
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;,&lt;type&gt;)void, or null otherwise
     */
    @Nullable
    public static MethodHandle findSetter(Lookup lookup, Field field, Class<?>... additionalTypes) {
        if (Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        int n = additionalTypes.length;
        Class<?>[] types = new Class<?>[n + 1];
        types[0] = field.getType();
        System.arraycopy(additionalTypes, 0, types, 1, n);
        Class<?> owner = field.getDeclaringClass();
        return forLocal(lookup, owner).findSetter(field.getName(), field, types);
    }

    /**
     * Finds the setter method for a given field in the given owner class.
     * Tries to find setters in this order:
     * 1. By looking up a method setFieldname with the certain argument type
     * 2. Looks for a setter method fieldname, i.e. with the exact same name as the field
     * 3. Tries to access the field of that name directly through this lookup.
     * <p>
     * It will only look up virtual methods with void return type and the field's type as the
     * sole argument.
     * <p>
     * If none of these apply, then it returns null.
     *
     * @param fieldName The plain field name
     * @param types     The setter's expected argument types (can be multiple or none as well)
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;,&lt;type&gt;)void, or null otherwise
     */
    @Nullable
    public MethodHandle findSetter(String fieldName, Class<?>... types) {
        return findSetter(fieldName, null, types);
    }

    @Nullable
    private MethodHandle findSetter(String fieldName, Field field, Class<?>... types) {
        String name = Strings.addLeading(fieldName, "set");
        return attachTo(params -> {
            String n = name;
            MethodHandle h = findAccessor(n, void.class, params);
            if (h != null) {
                // Method is setFieldname
                return h;
            }
            if (types.length >= 1 && (types[0] == boolean.class || types[0] == Boolean.class)) {
                // Check whether the name starts with "is", but the setter just starts with "set"
                n = Strings.removeLeading(fieldName, "is");
                if (!n.equals(fieldName)) {
                    n = Strings.addLeading(n, "set");
                    h = findAccessor(n, void.class, params);
                    if (h != null) {
                        // Method is isFieldname
                        return h;
                    }
                }
            }
            // Look for a method with the exact same name
            return findAccessor(fieldName, void.class, params);
        }, types, () -> {
            // Try to find the direct field setter
            try {
                return lookup.findSetter(type, fieldName, types[0]);
            } catch (NoSuchFieldException ex) {
                // Then there is no such field
                return null;
            } catch (IllegalAccessException ex) {
                // Try to make it accessible
                return tryUnreflectField(fieldName, types[0], field, lookup::unreflectSetter, t -> t.parameterType(1));
            }
        });
    }

    @FunctionalInterface
    private interface Unreflector {
        MethodHandle unreflect(Field f) throws IllegalAccessException;
    }

    private MethodHandle tryUnreflectField(String fieldName, Class<?> fieldType, Field field, Unreflector unreflector, Function<MethodType, Class<?>> typeChecker) {
        if (field == null) {
            try {
                field = type.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                // Could be if class itself was not accessible
                return null;
            }
        }
        if (Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        if (makeAccessible(field)) {
            MethodHandle setter;
            try {
                setter = unreflector.unreflect(field);
            } catch (IllegalAccessException ex) {
                throw new AssertionError(fieldName + " was made accessible but it's still not", ex);
            }
            if (!typeChecker.apply(setter.type()).equals(fieldType)) {
                // Could be if class itself was not accessible
                return null;
            }
            return setter;
        }

        Methods.warn(fieldType.getName() + "." + fieldName + " is not accessible");
        return null; // Couldn't be made accessible
    }

    @Nullable
    private MethodHandle findAccessor(String name, Class<?> returnType, Class<?>[] parameterTypes) {
        try {
            return lookup.findVirtual(type, name, methodType(returnType, parameterTypes));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            // Then try next
            return null;
        }
    }

    private boolean makeAccessible(Field field) {
        try {
            return AccessController.doPrivileged((PrivilegedAction<Boolean>)() -> {
                field.setAccessible(true);
                return true;
            });
        } catch (SecurityException ex) {
            return false;
        }
    }

    /**
     * Finds the method from an interface that is referenced by the finder argument.
     *
     * @param type   The interface type to look in
     * @param finder Used to identify the method
     * @param <T>    The interface type
     * @return The found MethodHandle
     */
    public static <T> MethodHandle findUsing(Class<T> type, MethodFinderCallback<T> finder) {
        return findUsing(publicLookup().in(type), type, finder);
    }

    /**
     * Finds the method from an interface that is referenced by the finder argument.
     *
     * @param lookup The lookup
     * @param type   The interface type to look in
     * @param finder Used to identify the method
     * @param <T>    The interface type
     * @return The found MethodHandle
     */
    public static <T> MethodHandle findUsing(Lookup lookup, Class<T> type, MethodFinderCallback<T> finder) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Can only find in interfaces.");
        }
        Object proxy = createProxy(type);
        return fromMethodByProxy(finder, type.cast(proxy)).map(m -> unreflectMethod(lookup, m)).orElseThrow(
                () -> new IllegalArgumentException(finder + " did not return anything on " + type.getName()));
    }

    private static <T> Optional<Method> fromMethodByProxy(MethodFinderCallback<T> finder, T proxy) {
        try {
            finder.callMethodFrom(proxy);
        } catch (MethodFoundException ex) {
            return Optional.of(ex.getMethod());
        } catch (Throwable ex) {
            throw new IllegalStateException("Prototype " + finder + " threw exception.", ex);
        }
        return Optional.empty();
    }

    private static MethodHandle unreflectMethod(Lookup lookup, Method method) {
        if (method == null) {
            return null;
        }
        try {
            return lookup.unreflect(method);
        } catch (IllegalAccessException ex) {
            throw new AssertionError("Cannot access " + method, ex);
        }
    }

    /**
     * Finds the method from all implemented interfaces of some particular argument instance.
     * The result will already be bound to that instance.
     *
     * @param target The instance
     * @param finder Used to identify the method
     * @return The found MethodHandle
     */
    public static <T> MethodHandle bindUsing(T target, MethodFinderCallback<T> finder) {
        return bindUsing(publicLookup().in(target.getClass()), target, finder);
    }

    /**
     * Finds the method from all implemented interfaces of some particular argument instance.
     * The result will already be bound to that instance.
     *
     * @param lookup The lookup
     * @param target The instance
     * @param finder Used to identify the method
     * @return The found MethodHandle
     */
    public static <T> MethodHandle bindUsing(Lookup lookup, T target, MethodFinderCallback<T> finder) {
        Class<?>[] interfaces = target.getClass().getInterfaces();
        @SuppressWarnings("unchecked")
        T proxy = (T) createProxy(interfaces);
        return fromMethodByProxy(finder, proxy).map(m -> unreflectMethod(lookup, m)).map(h -> h.bindTo(target)).
                orElseThrow(() -> new IllegalArgumentException(target.getClass() + " does not implement the requested method in its interfaces"));
    }

    private static Object createProxy(Class<?>... types) {
        if (types.length == 0) {
            throw new IllegalArgumentException("No interface implemented!");
        }
        return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), types, (p, m, a) -> {
            throw new MethodFoundException(m, a);
        });
    }

    /**
     * Checks whether some method or field would be visible in the given {@link Lookup},
     * i.e. a call to unreflect() would be successful even without setting the
     * assignable flag in the method.
     *
     * @param memberType The Method, Field, or Constructor
     * @return true if unreflect() can be called without overwriting m.setAssignable()
     */
    public boolean wouldBeVisible(AccessibleObject memberType) {
        Class<?> declaringClass = getDeclaringClassOf(memberType);
        int modifiers = getModifiersOf(memberType);
        if (!Modifier.isPublic(declaringClass.getModifiers())) {
            modifiers &= ~Modifier.PUBLIC;
        }
        int allowed = lookup.lookupModes();
        if (allowed == 0) {
            return false;
        }
        if (Modifier.isPublic(modifiers)) {
            return true;
        }
        if (allowed == Lookup.PUBLIC) {
            return false;
        }
        Class<?> lookupClass = lookup.lookupClass();

        if ((allowed & Lookup.PACKAGE) != 0 && !Modifier.isPrivate(modifiers)
                && isSamePackage(declaringClass, lookupClass)) {
            return true;
        }
        if ((allowed & Lookup.PROTECTED) != 0) {
            if (Modifier.isProtected(modifiers)) {
                // Also allow protected access
                // This means ALL_MODES
                return declaringClass.isAssignableFrom(lookupClass);
            }
        }
        return lookup.hasPrivateAccess()
            // Private methods allowed
            && (declaringClass == lookupClass || declaringClass.getEnclosingClass() == lookupClass);
    }

    private int getModifiersOf(AccessibleObject memberType) {
        if (memberType instanceof Executable) {
            return ((Executable) memberType).getModifiers();
        }
        if (memberType instanceof Field) {
            return ((Field) memberType).getModifiers();
        }
        throw new IllegalArgumentException("" + memberType);
    }

    private Class<?> getDeclaringClassOf(AccessibleObject memberType) {
        if (memberType instanceof Executable) {
            return ((Executable) memberType).getDeclaringClass();
        }
        if (memberType instanceof Field) {
            return ((Field) memberType).getDeclaringClass();
        }
        throw new IllegalArgumentException("" + memberType);
    }

    private boolean isSamePackage(Class<?> a, Class<?> b) {
        return a.getPackage().equals(b.getPackage());
    }

    private abstract class MethodSpliterator extends Spliterators.AbstractSpliterator<MethodInfo> {
        private final MethodFetcher fetcher = new MethodFetcher(type);
        Class<?> c = type;

        MethodSpliterator() {
            super(Long.MAX_VALUE, Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.IMMUTABLE);
        }

        @Override
        public boolean tryAdvance(Consumer<? super MethodInfo> action) {
            while (c != null) {
                Method m;
                while ((m = fetcher.next()) != null) {
                    if (wouldBeVisible(m)) {
                        action.accept(new MyMethodInfo(m));
                        return true;
                    }
                }
                c = nextClass();
                fetcher.reset(c);
            }

            return false;
        }

        @Nullable
        abstract Class<?> nextClass();
    }

    static class MethodFetcher {
        private final List<Method> usedMethods = new ArrayList<>();
        private Method[] methods;
        private int i;

        MethodFetcher(Class<?> type) {
            reset(type);
        }

        void reset(@Nullable Class<?> c) {
            methods = c == null ? new Method[0] : Methods.getDeclaredMethodsFrom(c);
            i = 0;
        }

        Method next() {
            outer:
            while (i < methods.length) {
                Method m = methods[i++];
                for (Method u : usedMethods) {
                    if (isOverriding(m, u)) {
                        continue outer;
                    }
                }
                usedMethods.add(m);
                return m;
            }

            return null;
        }
    }

    /**
     * Returns a stream of {@link MethodInfo} objects describing each method in the locator's type which is
     * visible from the locator's lookup instance.
     *
     * The resulting stream visits both static and instance methods.
     *
     * @return A non-parallel stream of all visible methods
     */
    public Stream<MethodInfo> methods() {
        return StreamSupport.stream(type.isInterface() ? new MethodSpliterator() {
            private final Set<Class<?>> classesToIterate = new HashSet<>();

            @Nullable
            @Override
            Class<?> nextClass() {
                Collections.addAll(classesToIterate, c.getInterfaces());
                Iterator<Class<?>> iterator = classesToIterate.iterator();
                if (iterator.hasNext()) {
                    Class<?> next = iterator.next();
                    iterator.remove();
                    return next;
                }
                return null;
            }
        } : new MethodSpliterator() {
            @Nullable
            @Override
            Class<?> nextClass() {
                return c == Object.class ? null : c.getSuperclass();
            }
        }, false);
    }

    private final class MyMethodInfo implements MethodInfo {
        private final Method method;

        MyMethodInfo(Method method) {
            this.method = method;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public MethodType getType() {
            return methodType(method.getReturnType(), method.getParameterTypes());
        }

        @Override
        public MethodHandle getHandle() {
            try {
                return lookup.unreflect(method);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(method + " should be visible for " + lookup, ex);
            }
        }

        @Override
        public <T> T lambdafy(Class<T> functionalType, Supplier<Object> instanceFactory, Object... additionalInitializers) {
            if (Modifier.isStatic(getModifiers())) {
                return Methods.lambdafy(lookup, getHandle(), functionalType, additionalInitializers);
            }
            Object[] initializers = new Object[additionalInitializers.length + 1];
            System.arraycopy(additionalInitializers, 0, initializers, 1, additionalInitializers.length);
            initializers[0] = instanceFactory.get();
            return Methods.lambdafy(lookup, getHandle(), functionalType, initializers);
        }

        @Override
        public Class<?> getDeclaringClass() {
            return method.getDeclaringClass();
        }

        @Override
        public int getModifiers() {
            return method.getModifiers();
        }

        @Override
        public boolean isSynthetic() {
            return method.isSynthetic();
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return method.getAnnotation(annotationClass);
        }

        @Override
        public Annotation[] getAnnotations() {
            return method.getAnnotations();
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return method.getDeclaredAnnotations();
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return method.isAnnotationPresent(annotationClass);
        }

        @Override
        public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
            return method.getAnnotationsByType(annotationClass);
        }

        @Override
        public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
            return method.getDeclaredAnnotation(annotationClass);
        }

        @Override
        public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
            return method.getDeclaredAnnotationsByType(annotationClass);
        }

        @Override
        public String toString() {
            return "Method info for " + method;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyMethodInfo that = (MyMethodInfo) o;
            return method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return method.hashCode() * 11;
        }
    }

    /**
     * Iterates over the full class hierarchy of my type, but without Object itself.
     * Starts with the given type and then traverses over the superclass hierarchy until some value is found.
     *
     * @return The return value of the last action call, or null if all actions returned null
     */
    public Stream<Class<?>> hierarchy() {
        return StreamSupport.stream(new Spliterator<>() {
            private Class<?> c = type;

            @Override
            public boolean tryAdvance(Consumer<? super Class<?>> action) {
                if (c == null) return false;
                action.accept(c);
                c = c.getSuperclass();
                if (c == Object.class) c = null;
                return true;
            }

            @Override
            public Spliterator<Class<?>> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.IMMUTABLE | Spliterator.NONNULL;
            }
        }, false);
    }

    /**
     * Finds the method to the only unique method that fits to the given method type.
     * <p>
     * A method is a unique method if it is the only one with the given type within one class. If there are more
     * in its super classes, then it doesn't matter.
     * <p>
     * A method matches if their arguments are either equal to the given method type, or more generic, or more specific,
     * in that order. A method is unique if it is unique within the best matching comparison. For instance,
     * if there is one method that is more generic, and another one is more specific, then uniqueness still
     * is given and the more generic one is chosen.
     *
     * @param reference The method type to look for
     * @return The found method handle, or Optional.empty()
     * @throws AmbiguousMethodException If there are multiple methods matching the searched type
     */
    public Optional<Method> findMethod(MethodType reference) {
        return Optional.ofNullable(findSingleMethodOf(reference));
    }

    private static final class MethodLocatorInvariant {
        Method bestMatch;
        Comparison matchingRank;
        boolean ambiguous;

        MethodLocatorInvariant chooseMatching(MethodLocatorInvariant other) {
            if (bestMatch == null || matchingRank == null) {
                return other;
            }
            if (other.bestMatch == null || other.matchingRank == null) {
                return this;
            }
            accept(other.bestMatch, other.matchingRank);
            return this;
        }

        boolean accept(Method m, Comparison comp) {
            switch (comp) {
                case EQUAL:
                    if (matchingRank == Comparison.EQUAL) {
                        ambiguous = true;
                        return true;
                    }
                    matchingRank = Comparison.EQUAL;
                    bestMatch = m;
                    ambiguous = false;
                    break;
                case MORE_SPECIFIC:
                    if (matchingRank != null && matchingRank != Comparison.MORE_SPECIFIC) {
                        break;
                    }
                case MORE_GENERIC: // or MORE_SPECIFIC
                    if (matchingRank != comp) {
                        matchingRank = comp;
                        ambiguous = false;
                    } else {
                        ambiguous = bestMatch != null;
                    }
                    bestMatch = m;
                    break;
                default:
                    // Do nothing then
            }

            return false;
        }
    }

    private Method findSingleMethodOf(MethodType reference) {
        return hierarchy().reduce(new MethodLocatorInvariant(), (inv, c) -> {
            Method[] methods = Methods.getDeclaredMethodsFrom(c);
            for (Method m : methods) {
                if (!wouldBeVisible(m)) {
                    continue;
                }
                Comparison compare = Methods.compare(reference, m);
                if (inv.accept(m, compare)) break;
            }

            if (inv.ambiguous) {
                throw new AmbiguousMethodException("Multiple methods found with type " + reference + " in " + type.getName());
            }
            return inv;
        }, MethodLocatorInvariant::chooseMatching).bestMatch;
    }

    private static final int INVOKE_STATIC = Modifier.STATIC | Modifier.PRIVATE;

    private static boolean isOverriding(Method fromSub, Method fromSuper) {
        if ((fromSub.getModifiers() & INVOKE_STATIC) != 0) {
            return false;
        }
        if (!fromSub.getName().equals(fromSuper.getName())) {
            return false;
        }
        if (fromSub.getParameterCount() != fromSuper.getParameterCount()) {
            return false;
        }
        Class<?>[] subParameterTypes = fromSub.getParameterTypes();
        Class<?>[] superParameterTypes = fromSuper.getParameterTypes();

        for (int i = 0; i < subParameterTypes.length; i++) {
            if (!subParameterTypes[i].equals(superParameterTypes[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodLocator that = (MethodLocator) o;
        return lookup.equals(that.lookup) &&
                type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lookup, type);
    }

    @Override
    public String toString() {
        return (lookup().hasPrivateAccess() && lookup().lookupClass().equals(getType()) ? "Private" : "Public") + " locator on " + getType().getName();
    }
}
