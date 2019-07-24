package org.fiolino.common.reflection;


import org.fiolino.common.ioc.Instantiator;
import org.fiolino.common.util.Strings;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

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
            MethodHandle h = findGetter0(n, fieldType, onTop);
            if (h == null && (fieldType == boolean.class || fieldType == Boolean.class)) {
                n = fieldName;
                if (!n.startsWith("is") || n.length() > 2 && Character.isLowerCase(n.charAt(2))) {
                    n = Strings.addLeading(fieldName, "is");
                }
                h = findGetter0(n, fieldType, onTop);
            }
            if (h == null) {
                // Look for a method with the exact same name
                h = findGetter0(fieldName, fieldType, onTop);
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
            MethodHandle h = findSetter0(n, params);
            if (h != null) {
                // Method is setFieldname
                return h;
            }
            if (types.length >= 1 && (types[0] == boolean.class || types[0] == Boolean.class)) {
                // Check whether the name starts with "is", but the setter just starts with "set"
                n = Strings.removeLeading(fieldName, "is");
                if (!n.equals(fieldName)) {
                    n = Strings.addLeading(n, "set");
                    h = findSetter0(n, params);
                    if (h != null) {
                        // Method is isFieldname
                        return h;
                    }
                }
            }
            // Look for a method with the exact same name
            return findSetter0(fieldName, params);
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
    private MethodHandle findSetter0(String name, Class<?>... types) {
        try {
            return lookup.findVirtual(type, name, methodType(void.class, types));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            // Then there is none
            return null;
        }
    }

    @Nullable
    private MethodHandle findGetter0(String name, Class<?> varType, Class<?>[] additionalTypes) {
        try {
            return lookup.findVirtual(type, name, methodType(varType, additionalTypes));
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
     * Finds all methods that are annotated either with {@link MethodFinder}, {@link ExecuteDirect} or {@link MethodHandleFactory}.
     *
     * @param prototype Contains all methods
     * @param visitor   The callback for each found method
     */
    public static <V> V findUsing(Object prototype, @Nullable V initialValue, MethodVisitor<V> visitor) {
        return findUsing(publicLookup().in(prototype.getClass()), initialValue, visitor);
    }

    /**
     * Finds all methods that are annotated either with {@link MethodFinder}, {@link ExecuteDirect} or {@link MethodHandleFactory}.
     *
     * @param lookup    The lookup
     * @param prototype Contains all methods
     * @param visitor   The callback for each found method
     */
    public static <V> V findUsing(Lookup lookup, Object prototype,
                                  @Nullable V initialValue, MethodVisitor<V> visitor) {
        return visitMethodsWithStaticContext(lookup, prototype, initialValue, (value, l, m, handleSupplier) -> {
            if (m.isAnnotationPresent(MethodFinder.class)) {
                return visitor.visit(value, l, m, () -> findHandleByProxy(lookup, handleSupplier.get()));
            }
            if (m.isAnnotationPresent(ExecuteDirect.class) || m.isAnnotationPresent(MethodHandleFactory.class)) {
                return visitor.visit(value, l, m, handleSupplier);
            }
            return value;
        });
    }

    /**
     * Creates the method handle of some interface that is invoked in a finder method.
     *
     * @param lookup The used lookup
     * @param finder This method should accept exactly one argument which must be an interface.
     *               The return type is irrelevant.
     * @return The first called method with that finder, or null if there is no such method called.
     */
    private static MethodHandle findHandleByProxy(Lookup lookup, MethodHandle finder) {
        if (finder.type().parameterCount() != 1) {
            throw new IllegalArgumentException(finder + " should accept exactly one argument.");
        }
        Class<?> proxyType = finder.type().parameterType(0);
        if (!proxyType.isInterface()) {
            throw new IllegalArgumentException(finder + " should only accept one interface.");
        }
        Object proxy = createProxy(proxyType);
        Method found = null;
        try {
            finder.invoke(proxy);
        } catch (MethodFoundException ex) {
            found = ex.getMethod();
        } catch (Throwable t) {
            throw new IllegalStateException("Prototype " + finder + " threw exception.", t);
        }
        if (found == null) {
            return null;
        }
        try {
            return lookup.unreflect(found);
        } catch (IllegalAccessException ex) {
            throw new AssertionError("Cannot access " + found, ex);
        }
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

    /**
     * Iterates over all methods of my type, including all super types.
     * The visitor's MethodHandle will be the plain converted method, without any modifications.
     * <p>
     * This will visit all methods that the given lookup is able to find.
     * If some method overrides another, then only the overriding method gets visited.
     *
     * @param initialValue The visitor's first value
     * @param visitor      The method visitor
     * @return The return value of the last visitor's run
     */
    public <V> V visitAllMethods(@Nullable V initialValue, MethodVisitor<V> visitor) {
        return iterateOver(type, initialValue, visitor, (m) -> {
            try {
                return lookup.unreflect(m);
            } catch (IllegalAccessException ex) {
                throw new AssertionError(m + " was tested for visibility; huh? Lookup: " + lookup, ex);
            }
        });
    }

    /**
     * Iterates over all methods of my type, including all super types.
     * The visitor's MethodHandle will be based on instances, that means, each MethodHandle has an instance
     * of the given class type as the first parameter, which will be the instance.
     * <p>
     * If the Method is an instance method, then the MethodHandle will just be used, except that the
     * instance argument will be casted to the given type.
     * <p>
     * If the Method is static, then the MethodHandle will still expect an instance of type as the first
     * parameter. If the static method has a parameter of that type as the first argument, then it will
     * be kept, otherwise it will be discarded.
     * <p>
     * That means, the visitor doesn't have to care whether the method is static or not.
     * <p>
     * This will visit all methods that the given lookup is able to find.
     * If some method overrides another, then only the overriding method gets visited.
     *
     * @param initialValue The visitor's first value
     * @param visitor      The method visitor
     * @return The return value of the last visitor's run
     */
    public <V> V visitMethodsWithInstanceContext(@Nullable V initialValue, MethodVisitor<V> visitor) {
        return iterateOver(type, initialValue, visitor, (m) -> {
            MethodHandle handle;
            try {
                handle = lookup.unreflect(m);
            } catch (IllegalAccessException ex) {
                throw new AssertionError(m + " is not accessible in " + lookup, ex);
            }
            if (Modifier.isStatic(m.getModifiers()) &&
                    (m.getParameterCount() == 0 || !m.getParameterTypes()[0].isAssignableFrom(type))) {
                return MethodHandles.dropArguments(handle, 0, type);
            }
            return handle.asType(handle.type().changeParameterType(0, type));
        });
    }

    /**
     * Iterates over all methods of my type, including all super types.
     * The visitor's MethodHandle will be static, that means, all MethodHandles are exactly of the Method's type
     * without any instance context.
     * <p>
     * If the Method is an instance method, then the given reference is bound to the handle.
     * <p>
     * That means, the visitor doesn't have to care whether the method is static or not.
     * <p>
     * The reference can either be a Class; in that case, the visitor creates a new instance as a reference to
     * the MethodHandle as soon as the first instance method is unreflected. That means, the class needs an
     * empty constructor visible from the given lookup unless all visited methods are static.
     * <p>
     * Or it can be any other object, which is the method's context then.
     * <p>
     * This will visit all methods that the given lookup is able to find.
     * If some method overrides another, then only the overriding method gets visited.
     *
     * @param reference    Either a Class or a prototype instance
     * @param initialValue The visitor's first value
     * @param visitor      The method visitor
     * @return The return value of the last visitor's run
     */
    public static <V> V visitMethodsWithStaticContext(Object reference, @Nullable V initialValue, MethodVisitor<V> visitor) {
        return visitMethodsWithStaticContext0(publicLookup(), reference, initialValue, visitor);
    }

    /**
     * Iterates over all methods of the given type.
     * The visitor's MethodHandle will be static, that means, all MethodHandles are exactly of the Method's type
     * without any instance context.
     * <p>
     * If the Method is an instance method, then the given reference is bound to the handle.
     * <p>
     * That means, the visitor doesn't have to care whether the method is static or not.
     * <p>
     * The reference can either be a Class; in that case, the visitor creates a new instance as a reference to
     * the MethodHandle as soon as the first instance method is unreflected. That means, the class needs an
     * empty constructor visible from the given lookup unless all visited methods are static.
     * <p>
     * Or it can be any other object, which is the method's context then.
     * <p>
     * This will visit all methods that the given lookup is able to find.
     * If some method overrides another, then only the overriding method gets visited.
     *
     * @param lookup       The lookup
     * @param reference    Either a Class or a prototype instance
     * @param initialValue The visitor's first value
     * @param visitor      The method visitor
     * @return The return value of the last visitor's run
     */
    public static <V> V visitMethodsWithStaticContext(Lookup lookup, Object reference,
                                                      @Nullable V initialValue, MethodVisitor<V> visitor) {
        return visitMethodsWithStaticContext0(lookup, reference, initialValue, visitor);
    }

    private static <T, V> V visitMethodsWithStaticContext0(Lookup lookup, T reference,
                                                           V initialValue, MethodVisitor<V> visitor) {
        Class<? extends T> type;
        Supplier<T> factory;
        if (reference instanceof Class) {
            @SuppressWarnings("unchecked")
            Class<T> t = (Class<T>) reference;
            factory = Instantiator.getDefault().createSupplierFor(t);
            type = t;
        } else {
            @SuppressWarnings("unchecked")
            Class<? extends T> t = (Class<? extends T>) reference.getClass();
            type = t;
            factory = () -> reference;
        }
        Lookup l = lookup.in(type);
        return forLocal(l).visitMethodsWithStaticContext(factory, initialValue, visitor);
    }

    /**
     * Iterates over all methods of my type, including all super types.
     * The visitor's MethodHandle will be static, that means, all MethodHandles are exactly of the Method's type
     * without any instance context.
     * <p>
     * If the Method is an instance method, then the given factory should create an instance of the given type.
     * This is bound to that handle then.
     * <p>
     * That means, the visitor doesn't have to care whether the method is static or not.
     * <p>
     * This will visit all methods that the given lookup is able to find.
     * If some method overrides another, then only the overriding method gets visited.
     *
     * @param factory      Used to instantiate a member if the created method handle is of an instance method
     * @param initialValue The visitor's first value
     * @param visitor      The method visitor
     * @return The return value of the last visitor's run
     */
    public <T, V> V visitMethodsWithStaticContext(Supplier<T> factory,
                                                  @Nullable V initialValue, MethodVisitor<V> visitor) {
        Object[] instance = new Object[1];
        return iterateOver(type, initialValue, visitor, (m) -> {
            MethodHandle handle;
            try {
                handle = lookup.unreflect(m);
            } catch (IllegalAccessException ex) {
                throw new AssertionError(m + " is not accessible in " + lookup, ex);
            }
            if (Modifier.isStatic(m.getModifiers())) {
                return handle;
            }
            Object ref = instance[0];
            if (ref == null) {
                ref = factory.get();
                instance[0] = ref;
            }
            return handle.bindTo(ref);
        });
    }

    /**
     * Iterates over the full class hierarchy of my type, but without Object itself.
     * Starts with the given type and then traverses over the superclass hierarchy until some value is found.
     *
     * @param action What to do for each class - will continue with the next superclass only if this returns null
     * @return The return value of the last action call, or null if all actions returned null
     */
    @Nullable
    public <T> T doInClassHierarchy(Function<? super Class<?>, T> action) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            T result = action.apply(c);
            if (result != null) {
                return result;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Iterates over all methods of my type, including all super types.
     */
    private <V> V iterateOver(Class<?> type, V initialValue, MethodVisitor<V> visitor,
                              Function<Method, MethodHandle> handleFactory) {
        Class<?> c = type;
        List<Method> usedMethods = new ArrayList<>();
        V value = initialValue;
        do {
            Method[] methods = Methods.getDeclaredMethodsFrom(c);
            outer:
            for (Method m : methods) {
                for (Method used : usedMethods) {
                    if (isOverriding(used, m)) {
                        continue outer;
                    }
                }
                usedMethods.add(m);
                if (wouldBeVisible(m)) {
                    value = visitor.visit(value, lookup(), m, () -> handleFactory.apply(m));
                }
            }

            if (c.isInterface()) {
                for (Class<?> extended : c.getInterfaces()) {
                    value = iterateOver(extended, value, visitor, handleFactory);
                }
                return value;
            }
        } while ((c = c.getSuperclass()) != null);

        return value;
    }

    private static final int INVOKE_STATIC = Modifier.STATIC | Modifier.PRIVATE;

    private boolean isOverriding(Method fromSub, Method fromSuper) {
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

}
