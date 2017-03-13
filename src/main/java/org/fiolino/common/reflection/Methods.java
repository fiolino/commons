package org.fiolino.common.reflection;

import org.fiolino.common.analyzing.AmbiguousTypesException;
import org.fiolino.common.util.Instantiator;
import org.fiolino.common.util.Strings;
import org.fiolino.common.util.Types;
import org.fiolino.data.annotation.IndexedAs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

/**
 * Box of several arbitary utility methods around MethodHandles.
 * <p>
 * Created by Michael Kuhlmann on 14.12.2015.
 */
public class Methods {

    private static final Logger logger = LoggerFactory.getLogger(Methods.class);

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
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;)&lt;type&gt;, or null otherwise
     */
    @Nullable
    public static MethodHandle findGetter(Lookup lookup, Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        Class<?> type = field.getType();
        Class<?> owner = field.getDeclaringClass();
        return findGetter(lookup, owner, field.getName(), type);
    }

    /**
     * Finds the getter method for a given field in the given owner class.
     * Tries to find getters in this order:
     * 1. By looking up a method getFieldname with the certain return type
     * 2. If the field is of type boolean, tries to find a getter named isFieldname
     * 3. Looks for a getter method fieldname, i.e. with the exact same name as the field
     * 4. Tries to access the field of that name directly through this lookup.
     * <p>
     * If none of these apply, then it returns null.
     *
     * @param lookup    The lookup
     * @param owner     Where to look for getters
     * @param fieldName The plain field name
     * @param type      The getter's expected return type
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;)&lt;type&gt;, or null otherwise
     */
    @Nullable
    public static MethodHandle findGetter(Lookup lookup, Class<?> owner, String fieldName, Class<?> type) {
        String name = Strings.addLeading(fieldName, "get");
        MethodHandle handle = findGetter0(lookup, owner, name, type);
        if (handle == null && (type == boolean.class || type == Boolean.class)) {
            name = fieldName;
            if (!name.startsWith("is") || name.length() > 2 && Character.isLowerCase(name.charAt(2))) {
                name = Strings.addLeading(name, "is");
            }
            handle = findGetter0(lookup, owner, name, type);
        }
        if (handle == null) {
            // Look for a method with the exact same name
            handle = findGetter0(lookup, owner, fieldName, type);
        }
        if (handle == null) {
            // Look for the direct field getter
            try {
                return lookup.findGetter(owner, fieldName, type);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                // Then the field's just not accessible, or there is no such field
                return null;
            }
        }
        return handle;
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
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;,&lt;type&gt;)void, or null otherwise
     */
    @Nullable
    public static MethodHandle findSetter(Lookup lookup, Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        Class<?> type = field.getType();
        Class<?> owner = field.getDeclaringClass();
        return findSetter(lookup, owner, field.getName(), type);
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
     * @param lookup    The lookup
     * @param owner     Where to look for setters
     * @param fieldName The plain field name
     * @param types     The setter's expected argument types (can be multiple or none as well)
     * @return The found {@link MethodHandle} of type (&lt;owner&gt;,&lt;type&gt;)void, or null otherwise
     */
    @Nullable
    public static MethodHandle findSetter(Lookup lookup, Class<?> owner, String fieldName, Class<?>... types) {
        String name = Strings.addLeading(fieldName, "set");
        MethodHandle handle = findSetter0(lookup, owner, name, types);
        if (handle != null) {
            // Method is setFieldname
            return handle;
        }
        if (types.length >= 1 && (types[0] == boolean.class || types[0] == Boolean.class)) {
            name = Strings.removeLeading(fieldName, "is");
            if (!name.equals(fieldName)) {
                name = Strings.addLeading(name, "set");
                handle = findSetter0(lookup, owner, name, types);
                if (handle != null) {
                    // Method is isFieldname
                    return handle;
                }
            }
        }
        // Look for a method with the exact same name
        handle = findSetter0(lookup, owner, fieldName, types);
        if (handle != null) {
            // Method is fieldname
            return handle;
        }

        // Try to find the direct field setter
        if (types.length == 1) {
            try {
                return lookup.findSetter(owner, fieldName, types[0]);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                // Then the field's just not accessible, or there is no such field
                return null;
            }
        }
        // No handle found
        return null;
    }

    @Nullable
    private static MethodHandle findSetter0(Lookup lookup, Class<?> owner, String name, Class<?>... types) {
        try {
            return lookup.findVirtual(owner, name, methodType(void.class, types));
        } catch (NoSuchMethodException ex) {
            // Then there is none
            return null;
        } catch (IllegalAccessException ex) {
            throw new AssertionError("Cannot access setter for " + name, ex);
        }
    }

    @Nullable
    private static MethodHandle findGetter0(Lookup lookup, Class<?> owner, String name, Class<?> type) {
        try {
            return lookup.findVirtual(owner, name, methodType(type));
        } catch (NoSuchMethodException ex) {
            // Then try next
            return null;
        } catch (IllegalAccessException ex) {
            throw new AssertionError("Cannot access getter for " + name, ex);
        }
    }

    /**
     * Finds the method from an interface that is referenced by the finder argument.
     *
     * @param lookup The lookup
     * @param type   The interface type to look in
     * @param finder Used to identify the method
     * @param <T>    The interface type
     * @return The found MethodHandle, or null if there is no such
     */
    public static <T> MethodHandle findVia(Lookup lookup, Class<T> type, MethodFinderCallback<T> finder) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Can only find in interfaces.");
        }
        Object proxy = createProxy(type);
        Method found = null;
        try {
            finder.findVia(type.cast(proxy));
        } catch (MethodFoundException ex) {
            found = ex.getMethod();
        } catch (Throwable ex) {
            throw new IllegalStateException("Prototype " + finder + " threw exception.", ex);
        }
        return unreflectMethod(lookup, found);
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
     * @param lookup The lookup
     * @param target The instance
     * @param finder Used to identify the method
     * @return The found MethodHandle, or null if there is no such
     */
    public static MethodHandle bindVia(Lookup lookup, Object target, MethodFinderCallback<Object> finder) {
        Class<?>[] interfaces = target.getClass().getInterfaces();
        Object proxy = createProxy(interfaces);
        Method found = null;
        try {
            finder.findVia(proxy);
        } catch (MethodFoundException ex) {
            found = ex.getMethod();
        } catch (Throwable ex) {
            throw new IllegalStateException("Prototype " + finder + " threw exception.", ex);
        }
        MethodHandle handle = unreflectMethod(lookup, found);
        return handle == null ? null : handle.bindTo(target);
    }

    private static Object createProxy(Class<?>... types) {
        return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), types, (p, m, a) -> {
            throw new MethodFoundException(m, a);
        });
    }

    /**
     * Finds all methods that are annotated either with {@link MethodFinder}, {@link ExecuteDirect} or {@link MethodHandleFactory}.
     *
     * @param lookup    The lookup
     * @param prototype Contains all methods
     * @param visitor   The callback for each found method
     */
    public static <V> V findVia(final Lookup lookup, final Object prototype,
                                V initialValue, final MethodVisitor<V> visitor) {
        return visitMethodsWithStaticContext(lookup, prototype, initialValue, (value, m, handleSupplier) -> {
            if (m.isAnnotationPresent(MethodFinder.class)) {
                return visitor.visit(value, m, () -> findHandleByProxy(lookup, handleSupplier.get()));
            }
            if (m.isAnnotationPresent(ExecuteDirect.class) || m.isAnnotationPresent(MethodHandleFactory.class)) {
                return visitor.visit(value, m, handleSupplier);
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
    public static MethodHandle findHandleByProxy(Lookup lookup, MethodHandle finder) {
        if (finder.type().parameterCount() != 1) {
            throw new AssertionError(finder + " should accept exactly one argument.");
        }
        Class<?> proxyType = finder.type().parameterType(0);
        if (!proxyType.isInterface()) {
            throw new AssertionError(finder + " should only accept one interface.");
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
     * @param lookup     The lookup
     * @param memberType The Method, Field, or Constructor
     * @return true if unreflect() can be called without overwriting m.setAssignable()
     */
    public static boolean wouldBeVisible(Lookup lookup, AccessibleObject memberType) {
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
        if ((allowed & Lookup.PRIVATE) != 0) {
            // Private methods allowed
            if (declaringClass == lookupClass) {
                return true;
            }
        }
        return false;
    }

    private static int getModifiersOf(AccessibleObject memberType) {
        if (memberType instanceof Executable) {
            return ((Executable) memberType).getModifiers();
        }
        if (memberType instanceof Field) {
            return ((Field) memberType).getModifiers();
        }
        throw new IllegalArgumentException("" + memberType);
    }

    private static Class<?> getDeclaringClassOf(AccessibleObject memberType) {
        if (memberType instanceof Executable) {
            return ((Executable) memberType).getDeclaringClass();
        }
        if (memberType instanceof Field) {
            return ((Field) memberType).getDeclaringClass();
        }
        throw new IllegalArgumentException("" + memberType);
    }

    private static boolean isSamePackage(Class<?> a, Class<?> b) {
        return a.getPackage().equals(b.getPackage());
    }

    /**
     * Iterates over all visible methods of the given type.
     * The visitor's MethodHandle will be the plain converted method, without any modifications.
     * <p>
     * This will visit all methods that the given lookup is able to find.
     * If some method overrides another, then only the overriding method gets visited.
     *
     * @param lookup       The lookup
     * @param type         The type to look up, including all super types
     * @param initialValue The visitor's first value
     * @param visitor      The method visitor
     * @return The return value of the last visitor's run
     */
    public static <V> V visitAllMethods(Lookup lookup, Class<?> type,
                                        V initialValue, MethodVisitor<V> visitor) {
        return iterateOver(lookup, type, initialValue, visitor, (m) -> {
            try {
                return lookup.unreflect(m);
            } catch (IllegalAccessException ex) {
                throw new AssertionError(m + " was tested for visibility; huh? Lookup: " + lookup, ex);
            }
        });
    }

    /**
     * Iterates over all methods of the given type.
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
     * @param lookup       The lookup
     * @param type         Te type to look up, including all super types
     * @param initialValue The visitor's first value
     * @param visitor      The method visitor
     * @return The return value of the last visitor's run
     */
    public static <V> V visitMethodsWithInstanceContext(Lookup lookup, final Class<?> type,
                                                        V initialValue, MethodVisitor<V> visitor) {
        final Lookup l = lookup.in(type);
        return iterateOver(l, type, initialValue, visitor, (m) -> {
            MethodHandle handle;
            try {
                handle = l.unreflect(m);
            } catch (IllegalAccessException ex) {
                throw new AssertionError(m + " is not accessible in " + l, ex);
            }
            if (Modifier.isStatic(m.getModifiers()) &&
                    (m.getParameterCount() == 0 || !m.getParameterTypes()[0].isAssignableFrom(type))) {
                return MethodHandles.dropArguments(handle, 0, type);
            }
            return handle.asType(handle.type().changeParameterType(0, type));
        });
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
     * The reference can either by a Class; in that case, the visitor creates a new instance as a reference to
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
                                                      V initialValue, MethodVisitor<V> visitor) {
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
        return visitMethodsWithStaticContext(lookup, type, factory, initialValue, visitor);
    }

    /**
     * Iterates over all methods of the given type.
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
     * @param lookup       The lookup
     * @param type         Iterates over this type's methods
     * @param factory      Used to instantiate a member if the created method handle is of an instance method
     * @param initialValue The visitor's first value
     * @param visitor      The method visitor
     * @return The return value of the last visitor's run
     */
    public static <T, V> V visitMethodsWithStaticContext(Lookup lookup, Class<? extends T> type, Supplier<T> factory,
                                                         V initialValue, MethodVisitor<V> visitor) {
        Object[] instance = new Object[1];
        Lookup l = lookup.in(type);
        return iterateOver(l, type, initialValue, visitor, (m) -> {
            MethodHandle handle;
            try {
                handle = l.unreflect(m);
            } catch (IllegalAccessException ex) {
                throw new AssertionError(m + " is not accessible in " + l, ex);
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
     * Iterates over all methods of the given type.
     */
    private static <V> V iterateOver(Lookup lookup, Class<?> type, V initialValue, MethodVisitor<V> visitor,
                                     Function<Method, MethodHandle> handleFactory) {
        Class<?> c = type;
        List<Method> usedMethods = new ArrayList<>();
        V value = initialValue;
        do {
            Method[] methods = c.getDeclaredMethods();
            outer:
            for (final Method m : methods) {
                for (Method used : usedMethods) {
                    if (isOverriding(used, m)) {
                        continue outer;
                    }
                }
                usedMethods.add(m);
                if (wouldBeVisible(lookup, m)) {
                    value = visitor.visit(value, m, () -> handleFactory.apply(m));
                }
            }

            if (c.isInterface()) {
                for (Class<?> extended : c.getInterfaces()) {
                    value = iterateOver(lookup, extended, value, visitor, handleFactory);
                }
                return value;
            }
        } while ((c = c.getSuperclass()) != null);

        return value;
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

    private static final MethodHandle nullCheck;
    private static final MethodHandle notNullCheck;

    static {
        try {
            nullCheck = MethodHandles.lookup().findStatic(Objects.class, "isNull", methodType(boolean.class, Object.class));
            notNullCheck = MethodHandles.lookup().findStatic(Objects.class, "nonNull", methodType(boolean.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("Objects.isNull()", ex);
        }
    }

    public static MethodHandle nullCheck() {
        return nullCheck;
    }

    public static MethodHandle notNullCheck() {
        return notNullCheck;
    }

    /**
     * Creates a MethodHandle which accepts a String as the only parameter and returns an enum of type enumType.
     * It already contains a null check: If the input string is null, then the return value is null as well
     * without any complains.
     *
     * @throws IllegalArgumentException If the input string is invalid, i.e. none of the accepted enumeration values
     */
    public static <E extends Enum<E>> MethodHandle convertStringToEnum(Class<E> enumType) {
        return convertStringToEnum(enumType, null);
    }

    /**
     * Creates a MethodHandle which accepts a String as the only parameter and returns an enum of type enumType.
     * It already contains a null check: If the input string is null, then the return value is null as well
     * without any complains.
     * <p/>
     * If the input string is invalid, i.e. none of the accepted enumeration values, then the exceptionHandler is
     * called, which can either throw an exception again or return another enum (or null). If no exceptionHandler is
     * given, then the exception is not catched at all.
     */
    public static <E extends Enum<E>> MethodHandle convertStringToEnum(Class<E> enumType,
                                                                       ExceptionHandler<? super IllegalArgumentException> exceptionHandler) {
        assert enumType.isEnum();
        Map<String, Object> map = null;
        E[] enumConstants = enumType.getEnumConstants();
        if (enumConstants == null) {
            throw new IllegalArgumentException(enumType.getName() + " should be an enum.");
        }
        for (java.lang.reflect.Field f : enumType.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isPublic(m)) {
                continue;
            }
            if (!enumType.isAssignableFrom(f.getType())) {
                continue;
            }
            IndexedAs annotated = f.getAnnotation(IndexedAs.class);
            if (annotated == null) {
                continue;
            }
            Object v;
            try {
                v = f.get(null);
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Cannot access " + f, ex);
            }
            if (map == null) {
                map = new HashMap<>(enumConstants.length * 4);
            }
            put(map, annotated.value(), v);
            for (String a : annotated.alternatives()) {
                put(map, a, v);
            }
        }
        if (map == null) {
            // No @IndexedAs annotations
            return convertStringToNormalEnum(enumType, exceptionHandler);
        }
        // There are annotations, bind to Map.get() method
        for (E v : enumConstants) {
            String n = v.name();
            if (!map.containsKey(n)) {
                map.put(n, v);
            }
        }
        MethodHandle getFromMap;
        try {
            getFromMap = MethodHandles.publicLookup().bind(map, "get", methodType(Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("Map.get()", ex);
        }
        return getFromMap.asType(methodType(enumType, String.class));
    }

    private static MethodHandle convertStringToNormalEnum(Class<?> enumType,
                                                          ExceptionHandler<? super IllegalArgumentException> exceptionHandler) {
        Lookup lookup = publicLookup().in(enumType);
        MethodHandle valueOf;
        try {
            valueOf = lookup.findStatic(enumType, "valueOf", methodType(enumType, String.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("No valueOf in " + enumType.getName() + "?", ex);
        }

        if (exceptionHandler != null) {
            valueOf = wrapWithExceptionHandler(valueOf, 0, IllegalArgumentException.class, exceptionHandler);
        }

        return secureNull(valueOf);
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (map.containsKey(key)) {
            logger.warn("Key " + key + " was already defined for " + map.get(key));
            return;
        }
        map.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private static final MethodHandle exceptionHandlerCaller = findVia(MethodHandles.lookup(), ExceptionHandler.class,
            (h) -> h.handleNotExisting(null, null, null, null));

    public static <E extends Enum<E>> MethodHandle convertEnumToString(Class<E> type) {
        Map<E, String> map = new EnumMap<>(type);
        E[] enumConstants = type.getEnumConstants();
        if (enumConstants == null) {
            throw new IllegalArgumentException(type.getName() + " should be an enum.");
        }
        boolean isAnnotated = false;
        for (java.lang.reflect.Field f : type.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isPublic(m)) {
                continue;
            }
            if (!type.isAssignableFrom(f.getType())) {
                continue;
            }
            E v;
            try {
                v = type.cast(f.get(null));
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Cannot access " + f, ex);
            }
            IndexedAs annotated = f.getAnnotation(IndexedAs.class);
            if (annotated == null) {
                map.put(v, v.name());
                continue;
            }
            isAnnotated = true;
            String value = annotated.value();
            map.put(v, value.isEmpty() ? null : value);
        }
        if (isAnnotated) {
            MethodHandle getFromMap;
            try {
                getFromMap = MethodHandles.publicLookup().bind(map, "get", methodType(Object.class, Object.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new AssertionError("Map.get()", ex);
            }
            return getFromMap.asType(methodType(String.class, type));
        } else {
            // No @IndexedAs annotations
            try {
                return MethodHandles.publicLookup().findVirtual(type, "name", methodType(String.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new AssertionError("Map.get()", ex);
            }
        }
    }

    /**
     * Wraps the given method handle by an exception handler that is called then.
     *
     * @param target           The method handle to wrap.
     * @param argumentNumber   Which argument shall be transferred to the exception handler. If -1, then null value if given instead, and the input type is void.
     * @param exceptionType    Which type shall be catched.
     * @param exceptionHandler The handler.
     * @param <E>              The exception type
     * @return A method handle of the same type as the target, which won't throw E but call the handler instead.
     */
    public static <E extends Throwable> MethodHandle wrapWithExceptionHandler(MethodHandle target, int argumentNumber,
                                                                              Class<E> exceptionType,
                                                                              ExceptionHandler<? super E> exceptionHandler) {
        MethodType targetType = target.type();
        Class<?> outputType = targetType.returnType();
        MethodHandle warnNotExisting = exceptionHandlerCaller.bindTo(exceptionHandler);
        if (argumentNumber < 0) {
            warnNotExisting = MethodHandles.insertArguments(warnNotExisting, 1, void.class, outputType, null);
        } else {
            checkArgumentLength(targetType, argumentNumber);
            Class<?> inputType = targetType.parameterType(argumentNumber);
            warnNotExisting = warnNotExisting.asType(warnNotExisting.type().changeParameterType(3, inputType));
            if (argumentNumber > 0) {
                Class<?>[] firstParameterTypes = Arrays.copyOf(targetType.parameterArray(), argumentNumber);
                warnNotExisting = MethodHandles.dropArguments(warnNotExisting, 3, firstParameterTypes);
            }
            warnNotExisting = MethodHandles.insertArguments(warnNotExisting, 1, inputType, outputType);
        }
        warnNotExisting = returnEmptyValue(warnNotExisting, outputType);
        return MethodHandles.catchException(target, exceptionType, warnNotExisting);
    }

    static void checkArgumentLength(MethodType targetType, int argumentNumber) {
        if (targetType.parameterCount() <= argumentNumber) {
            throw new IllegalArgumentException("Target " + targetType + " must have at least "
                    + (argumentNumber + 1) + " input values.");
        }
    }

    /**
     * Drops all incoming arguments from referenceType starting with index.
     * The target handle must implement the referenceType except all arguments that start at index.
     *
     * @param target This will be exwecuted
     * @param referenceType Describes the returning handle's type
     * @return A handle that calls target but drops all trailing arguments
     */
    public static MethodHandle dropAllOf(MethodHandle target, MethodType referenceType) {
        return dropAllOf(target, referenceType, 0);
    }

    /**
     * Drops all incoming arguments from referenceType starting with index.
     * The target handle must implement the referenceType except all arguments that start at index.
     *
     * @param target This will be executed
     * @param referenceType Describes the returning handle's type
     * @param index In index in the referenceType's parameter array
     * @return A handle that calls target but drops all trailing arguments
     */
    public static MethodHandle dropAllOf(MethodHandle target, MethodType referenceType, int index) {
        if (index == referenceType.parameterCount()) {
            return target;
        }
        checkArgumentLength(referenceType, index);
        Class<?>[] parameterTypes;
        if (index == 0) {
            parameterTypes = referenceType.parameterArray();
        } else {
            parameterTypes = new Class<?>[referenceType.parameterCount() - index];
            System.arraycopy(referenceType.parameterArray(), index, parameterTypes, 0, parameterTypes.length);
        }
        return MethodHandles.dropArguments(target, index, parameterTypes);
    }

    /**
     * Modifies the given target handle so that it will return a value of the given type which will always be null.
     *
     * @param target Some target to execute = the return type must be void
     * @param returnType The type to return
     * @return A handle with the same arguments as the target, but which returns a null value or a zero=like value if returnType is a primitive
     */
    public static MethodHandle returnEmptyValue(MethodHandle target, Class<?> returnType) {
        if (target.type().returnType() != void.class) {
            throw new IllegalArgumentException("Expected void method, but it's " + target);
        }
        if (returnType == void.class) {
            return target;
        }
        MethodHandle returnHandle;
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) {
                returnHandle = MethodHandles.constant(boolean.class, false);
            } else {
                returnHandle = MethodHandles.constant(int.class, 0);
                returnHandle = MethodHandles.explicitCastArguments(returnHandle, methodType(returnType));
            }
        } else {
            returnHandle = MethodHandles.constant(returnType, null);
        }
        return MethodHandles.filterReturnValue(target, returnHandle);
    }

    /**
     * A handle that checks the given argument (which is of type Object) for being an instance of the given type.
     *
     * @param check The type to check
     * @return A handle of type (Object)boolean
     */
    public static MethodHandle instanceCheck(Class<?> check) {
        if (check.isPrimitive()) {
            throw new IllegalArgumentException(check.getName() + " must be a reference type.");
        }
        if (Object.class.equals(check)) {
            return notNullCheck;
        }
        try {
            return publicLookup().in(check).bind(check, "isInstance", methodType(boolean.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("No isInstance for " + check.getName() + "?");
        }

    }

    public static final MethodHandle FALSE = MethodHandles.constant(boolean.class, false);
    public static final MethodHandle TRUE = MethodHandles.constant(boolean.class, true);

    /**
     * Combines a list of conditions by and.
     * Given is an array of MethodHandle instances that return boolean, and whose parameter types are identical
     * except that leading handles may have fewer types than following handles.
     * <p/>
     * The returned handle will accept the maximum number of input parameters, which is the parameters of the last
     * given handle. If no handle was given at all, then a MethodHandle returning a constant TRUE value without any
     * incoming parameters, is returned.
     * <p/>
     * Each handle starting from the second one is invoked only if all previous handles returned true.
     */
    public static MethodHandle and(MethodHandle... handles) {
        if (handles.length == 0) {
            return TRUE;
        }
        return and(handles, 0);
    }

    private static MethodHandle and(MethodHandle[] handles, int start) {
        if (start == handles.length - 1) {
            return handles[start];
        }
        MethodHandle remaining = and(handles, start + 1);
        return MethodHandles.guardWithTest(handles[start], remaining, dropAllOf(FALSE, remaining.type(), 0));
    }

    /**
     * Combines a list of conditions by or.
     * Given is an array of MethodHandle instances that return boolean, and whose parameter types are identical
     * except that leading handles may have fewer types than following handles.
     * <p/>
     * The returned handle will accept the maximum number of input parameters, which is the parameters of the last
     * given handle. If no handle was given at all, then a MethodHandle returning a constant FALSE value without any
     * incoming parameters, is returned.
     * <p/>
     * Each handle starting from the second one is invoked only if all previous handles returned false.
     */
    public static MethodHandle or(MethodHandle... handles) {
        if (handles.length == 0) {
            return FALSE;
        }
        return or(handles, 0);
    }

    private static MethodHandle or(MethodHandle[] handles, int start) {
        if (start == handles.length - 1) {
            return handles[start];
        }
        MethodHandle remaining = or(handles, start + 1);
        return MethodHandles.guardWithTest(handles[start], dropAllOf(TRUE, remaining.type(), 0), remaining);
    }

    /**
     * Secures a given MethodHandle so that it will only invoked if none of its parameters is null.
     * The returned MethodHandle is of the same type as the given target. If the return type is an Object,
     * then the return value is null if any input value was null. If the return type is a primitive, then
     * the return value will be 0 or false in that case. If the target handle is of type void, the it will
     * simply not being called at all if there is a null value.
     */
    public static MethodHandle secureNull(MethodHandle target) {
        return secureNull(target, (BitSet) null);
    }

    /**
     * Secures a given MethodHandle so that it will only invoked if none of its specified parameters is null.
     * The returned MethodHandle is of the same type as the given target. If the return type is an Object,
     * then the return value is null if any input value was null. If the return type is a primitive, then
     * the return value will be 0 or false in that case. If the target handle is of type void, the it will
     * simply not being called at all if there is a null value.
     */
    public static MethodHandle secureNull(MethodHandle target, int... argumentIndexes) {
        int n = target.type().parameterCount();
        BitSet toCheck = new BitSet(n);
        for (int i : argumentIndexes) {
            if (i >= n) {
                throw new IllegalArgumentException("Index " + i + " is out of range: Method has only " + n + " arguments.");
            }
            toCheck.set(i);
        }
        return secureNull(target, toCheck);
    }

    private static MethodHandle secureNull(MethodHandle target, BitSet toCheck) {
        MethodType targetType = target.type();
        Class<?>[] parameterArray = targetType.parameterArray();
        MethodHandle[] checks = new MethodHandle[parameterArray.length];
        int targetCount = 0;
        int checkCount = 0;
        MethodType nullCheckType = targetType.changeReturnType(boolean.class);
        int i = 0;
        for (Class<?> p : parameterArray) {
            if ((toCheck != null && !toCheck.get(i++)) || p.isPrimitive()) {
                targetCount++;
                continue;
            }
            MethodHandle castedNullCheck = nullCheck.asType(methodType(boolean.class, p));
            MethodHandle checkArgI = MethodHandles.permuteArguments(castedNullCheck, nullCheckType, targetCount++);
            checks[checkCount++] = checkArgI;
        }
        if (checkCount == 0) {
            return target;
        }
        if (checkCount < targetCount) {
            checks = Arrays.copyOf(checks, checkCount);
        }
        MethodHandle checkNull = or(checks);
        return rejectIf(target, checkNull);
    }

    public static final MethodHandle DO_NOTHING = MethodHandles.constant(Void.class, null).asType(methodType(void.class));

    /**
     * Creates a method handle that invokes its target only if the guard returns false.
     * Guard and target must have the same argument types. Guard must return a boolean,
     * while the target may return any type.
     * <p/>
     * The resulting handle has exactly the same type, and if it returns a value, it will return null
     * or a zero-like value if the guard returned false.
     */
    public static MethodHandle rejectIf(MethodHandle target, MethodHandle guard) {
        MethodHandle constantNull = constantNullHandle(target.type());
        return MethodHandles.guardWithTest(guard, constantNull, target);
    }

    /**
     * Creates a method handle that invokes its target only if the guard returns true.
     * Guard and target must have the same argument types. Guard must return a boolean,
     * while the target may return any type.
     * <p/>
     * The resulting handle has exactly the same type, and if it returns a value, it will return null
     * or a zero-like value if the guard returned false.
     */
    public static MethodHandle invokeOnlyIf(MethodHandle target, MethodHandle guard) {
        MethodHandle constantNull = constantNullHandle(target.type());
        return MethodHandles.guardWithTest(guard, target, constantNull);
    }

    /**
     * Gets a method handle that has the same type as the given argument,
     * but does nothing and just returns a default value if the argument has a return type.
     *
     * @param type The expected type of the returned handle.
     * @return The no-op handle
     */
    public static MethodHandle constantNullHandle(MethodType type) {
        Class<?> returnType = type.returnType();
        MethodHandle constantNull;
        if (returnType == void.class) {
            constantNull = DO_NOTHING;
        } else if (returnType == int.class || returnType == long.class || returnType == short.class || returnType == byte.class || returnType == float.class || returnType == double.class) {
            constantNull = MethodHandles.constant(returnType, 0);
        } else if (returnType == char.class) {
            constantNull = MethodHandles.constant(returnType, (char) Character.UNASSIGNED);
        } else if (returnType == boolean.class) {
            constantNull = MethodHandles.constant(returnType, false);
        } else {
            constantNull = MethodHandles.constant(returnType, null);
        }
        constantNull = dropAllOf(constantNull, type, 0);
        return constantNull;
    }

    /**
     * Creates a method handle that doesn't execute its target if the guard returns true.
     * <p/>
     * The guard must accept exactly one argument with the type of the given argument of the target, and return a
     * boolean value.
     * <p/>
     * The resulting handle has the same type as the target, and returns a null value or zero if the guard doesn't
     * allow to execute.
     */
    public static MethodHandle rejectIfArgument(MethodHandle target, int argumentNumber, MethodHandle guard) {
        MethodType targetType = target.type();
        checkArgumentLength(targetType, argumentNumber);

        MethodHandle argumentGuard = argumentGuard(guard, targetType, argumentNumber);

        return rejectIf(target, argumentGuard);
    }

    /**
     * Creates a method handle that executes only if its target if the guard returns true.
     * <p/>
     * This methods is just the exact opposite to rejectIfArgument().
     * <p/>
     * The guard must accept exactly one argument with the type of the given argument of the target, and return a
     * boolean value.
     * <p/>
     * The resulting handle has the same type as the target, and returns a null value or zero if the guard doesn't
     * allow to execute.
     */
    public static MethodHandle invokeOnlyIfArgument(MethodHandle target, int argumentNumber,
                                                    MethodHandle... guards) {

        int n = guards.length;
        if (n == 0) {
            throw new IllegalArgumentException("No guards given.");
        }
        MethodType targetType = target.type();
        checkArgumentLength(targetType, argumentNumber + n - 1);

        MethodHandle handle = target;
        int a = argumentNumber;
        for (MethodHandle g : guards) {
            MethodHandle argumentGuard = argumentGuard(g, targetType, a++);

            handle = invokeOnlyIf(handle, argumentGuard);
        }
        return handle;
    }

    private static MethodHandle argumentGuard(MethodHandle guard, MethodType targetType, int argumentNumber) {
        MethodHandle argumentGuard = guard.asType(guard.type().changeParameterType(0, targetType.parameterType(argumentNumber)));
        if (argumentNumber > 0) {
            argumentGuard = MethodHandles.dropArguments(argumentGuard, 0, Arrays.copyOf(targetType.parameterArray(), argumentNumber));
        }
        return argumentGuard;
    }

    /**
     * Creates a method handle that executes its target, and then returns the argument of the given number.
     * <p/>
     * The resulting handle will have the same type as the target except that the return type is the same as the
     * one of the given argument.
     * <p/>
     * If the target returns a value, then this simply is discarded.
     */
    public static MethodHandle returnArgument(MethodHandle target, int argumentNumber) {

        MethodType targetType = target.type();
        checkArgumentLength(targetType, argumentNumber);
        MethodHandle dontReturnAnything = target.asType(targetType.changeReturnType(void.class));
        Class<?> parameterType = targetType.parameterType(argumentNumber);
        MethodHandle identityOfPos = MethodHandles.identity(parameterType);
        if (targetType.parameterCount() > 1) {
            identityOfPos = MethodHandles.permuteArguments(identityOfPos, targetType.changeReturnType(parameterType), argumentNumber);
        }
        return MethodHandles.foldArguments(identityOfPos, dontReturnAnything);
    }

    public static MethodHandle permute(MethodHandle target, MethodType type) {
        MethodType targetType = target.type();
        int parameterCount = targetType.parameterCount();
        int[] indexes = new int[parameterCount];
        Class<?>[] newParameterTypes = new Class<?>[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            Class<?> p = targetType.parameterType(i);
            int pos = findBestMatchingParameter(type, p, i);
            indexes[i] = pos;
            Class<?> original = type.parameterType(pos);
            newParameterTypes[i] = original;
        }

        return MethodHandles.permuteArguments(target.asType(methodType(type.returnType(), newParameterTypes)), type, indexes);
    }

    private static int findBestMatchingParameter(MethodType type, Class<?> lookFor, int pos) {
        int match = -1;
        int perfectMatch = -1;
        for (int i = 0; i < type.parameterCount(); i++) {
            Class<?> p = type.parameterType(i);
            if (p.equals(lookFor)) {
                if (i == pos) {
                    return i;
                }
                if (perfectMatch >= 0) {
                    throw new AmbiguousTypesException("Type " + lookFor.getName() + " is appearing twice in " + type);
                }
                perfectMatch = i;
                match = i;
            } else if (Types.isAssignableFrom(lookFor, p)) {
                if (i != pos && match >= 0) {
                    throw new AmbiguousTypesException("Type " + lookFor.getName() + " is matching twice in " + type);
                }
                match = i;
            }
        }

        if (match < 0) {
            throw new IllegalArgumentException(type + " does not contain parameter of type " + lookFor.getName());
        }
        return match;
    }

    /**
     * Compares an expected method type (the reference) with some other method type to check.
     *
     * @param reference The expected type
     * @param toCheck   This is checked
     * @return The comparison (@see {@link Comparison})
     */
    public static Comparison compare(MethodType reference, MethodType toCheck) {
        return compare(reference.returnType(), toCheck.returnType(),
                reference.parameterArray(), toCheck.parameterArray());
    }

    /**
     * Compares an expected method type (the reference) with some other method to check.
     *
     * @param reference The expected type
     * @param toCheck   This is checked
     * @return The comparison (@see {@link Comparison})
     */
    public static Comparison compare(MethodType reference, Method toCheck) {
        return compare(reference.returnType(), toCheck.getReturnType(),
                reference.parameterArray(), toCheck.getParameterTypes());
    }

    private static Comparison compare(Class<?> expectedReturnType, Class<?> checkedReturnType,
                                      Class<?>[] expectedArguments, Class<?>[] checkedArguments) {
        int expectedArgumentCount = expectedArguments.length;
        int checkedArgumentCount = checkedArguments.length;
        if (expectedArgumentCount > checkedArgumentCount) {
            return Comparison.LESS_ARGUMENTS;
        }
        if (expectedArgumentCount < checkedArgumentCount) {
            return Comparison.MORE_ARGUMENTS;
        }

        Comparison comparison;
        if (checkedReturnType.equals(expectedReturnType)) {
            comparison = Comparison.EQUAL;
        } else if (expectedReturnType == void.class || checkedReturnType == void.class) {
            return Comparison.INCOMPATIBLE;
        } else if (Types.isAssignableFrom(expectedReturnType, checkedReturnType) && !expectedReturnType.isPrimitive()) {
            comparison = Comparison.MORE_GENERIC;
        } else if (Types.isAssignableFrom(checkedReturnType, expectedReturnType)) {
            comparison = Comparison.MORE_SPECIFIC;
        } else {
            return Comparison.INCOMPATIBLE;
        }

        for (int i = 0; i < expectedArgumentCount; i++) {
            Class<?> expectedArgument = expectedArguments[i];
            Class<?> checkedArgument = checkedArguments[i];
            if (expectedArgument.equals(checkedArgument)) {
                continue;
            }
            if (Types.isAssignableFrom(expectedArgument, checkedArgument) && !expectedArgument.isPrimitive()) {
                switch (comparison) {
                    case MORE_GENERIC:
                        comparison = Comparison.CONVERTABLE;
                        break;
                    case EQUAL:
                        comparison = Comparison.MORE_SPECIFIC;
                }
            } else if (Types.isAssignableFrom(checkedArgument, expectedArgument)) {
                switch (comparison) {
                    case MORE_SPECIFIC:
                        comparison = Comparison.CONVERTABLE;
                        break;
                    case EQUAL:
                        comparison = Comparison.MORE_GENERIC;
                }
            } else {
                return Comparison.INCOMPATIBLE;
            }
        }

        return comparison;
    }

    /**
     * Finds the method handle to the only unique method that fits to the given method type.
     * <p>
     * A method is a unique method if it is the only one with the given type within one class. If there are more
     * in its super classes, then it doesn't matter.
     * <p>
     * A method matches if their arguments are either equal to the given method type, or more generic, or more specific,
     * in that order. A method is unique if it is unique within the best matching comparison. For instance,
     * if there is one method that is more generic, and another one is more specific, then uniqueness still
     * is given and the more generic one is chosen.
     * <p>
     * The object to look up may either be an instance: Then its class type will be searched. If the found method then
     * is static, it will simply be used, otherwise the given instance is bound to it.
     * <p>
     * If the object is a Class, and the found method is an instance method, then an empty constructor is expected
     * and a new instance is created now.
     * <p>
     * As a result, the returned handle will have exactly the given reference method type, without any additional
     * object instance.
     *
     * @param lookup    The lookup
     * @param object    The object (Class or some instance) to investigate
     * @param reference The method type to look for
     * @return The found method handle
     * @throws AmbiguousMethodException If there are multiple methods matching the searched type
     * @throws NoSuchMethodError        If no method was found
     */
    public static MethodHandle findMethodHandleOfType(Lookup lookup, Object object, MethodType reference) {
        MethodHandle handle = findSingleMethodHandle(lookup, object, reference);
        return handle.asType(reference);
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
     * @param lookup    The lookup
     * @param type      The reference type to investigate
     * @param reference The method type to look for
     * @return The found method handle
     * @throws AmbiguousMethodException If there are multiple methods matching the searched type
     * @throws NoSuchMethodError        If no method was found
     */
    public static Method findMethodOfType(Lookup lookup, Class<?> type, MethodType reference) {
        Method m = findSingleMethodIn(lookup, type, reference);
        if (m == null) {
            throw new NoSuchMethodError("No method " + reference + " in " + type.getName());
        }
        return m;
    }

    private static MethodHandle findSingleMethodHandle(Lookup lookup, Object object, MethodType reference) {
        Class<?> type;
        if (object instanceof Class) {
            type = (Class<?>) object;
        } else {
            if (MethodHandleProxies.isWrapperInstance(object)) {
                return MethodHandleProxies.wrapperInstanceTarget(object);
            }
            type = object.getClass();
        }
        Method m = findMethodOfType(lookup, type, reference);
        MethodHandle handle;
        try {
            handle = lookup.unreflect(m);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(m + " should be visible.", ex);
        }
        if (Modifier.isStatic(m.getModifiers())) {
            return handle;
        }
        if (object instanceof Class) {
            // Then inject a new instance now
            object = Instantiator.getDefault().instantiate((Class<?>) object);
        }
        return handle.bindTo(object);
    }

    /**
     * Iterates over the full class hierarchy, but without Object itself.
     * Starts with the given type and then traverses over the superclass hierarchy until some value is found.
     *
     * @param type At which class to start
     * @param action What to do for each class - will continue with the next superclass only if this returns null
     * @return The return value of the last action call, or null if all actions returned null
     */
    @Nullable
    public static <T> T doInClassHierarchy(Class<?> type, Function<? super Class<?>, T> action) {
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
     * Guards a method handle with a semaphore to assure bounded access to one single thread at a time.
     *
     * On each call to the target handle, a single permit is acquired, and it's released afterwards.
     *
     * The permit is acquired uninterruptibly, meaning that it will wait even if Thread.interrupt() is called.
     * Therefore the caller doesn't need to check for InterruptedExceptions.
     *
     * @param target The target handle to call
     * @return A handle with the same type as the target handle
     */
    public static MethodHandle synchronize(MethodHandle target) {
        return synchronize(target, 1);
    }

    /**
     * Guards a method handle with a semaphore to assure bounded access.
     *
     * On each call to the target handle, a single permit is acquired, and it's released afterwards.
     *
     * The permit is acquired uninterruptibly, meaning that it will wait even if Thread.interrupt() is called.
     * Therefore the caller doesn't need to check for InterruptedExceptions.
     *
     * @param target The target handle to call
     * @param concurrentAccesses How many threads may access this operation concurrently
     * @return A handle with the same type as the target handle
     */
    public static MethodHandle synchronize(MethodHandle target, int concurrentAccesses) {
        return synchronizeWith(target, new Semaphore(concurrentAccesses));
    }

    /**
     * Guards a method handle with a semaphore to assure bounded access.
     *
     * On each call to the target handle, a single permit is acquired, and it's released afterwards.
     *
     * The permit is acquired uninterruptibly, meaning that it will wait even if Thread.interrupt() is called.
     * Therefore the caller doesn't need to check for InterruptedExceptions.
     *
     * @param target The target handle to call
     * @param semaphore This is used to block gthe operation
     * @return A handle with the same type as the target handle
     */
    public static MethodHandle synchronizeWith(MethodHandle target, Semaphore semaphore) {
        MethodType type = target.type();
        Class<?> returnType = type.returnType();
        MethodHandle acquire, release;
        try {
            acquire = publicLookup().bind(semaphore, "acquireUninterruptibly", methodType(void.class));
            release = publicLookup().bind(semaphore, "release", methodType(void.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
        MethodHandle finallyBlock = MethodHandles.foldArguments(MethodHandles.throwException(returnType, Throwable.class),
                release);
        MethodHandle targetWithFinally = MethodHandles.catchException(target, Throwable.class, finallyBlock);
        MethodHandle acquireAndExecute = MethodHandles.foldArguments(targetWithFinally, acquire);
        if (returnType == void.class) {
            return MethodHandles.foldArguments(dropAllOf(release, type), acquireAndExecute);
        }
        MethodHandle returnValue = MethodHandles.identity(returnType);
        if (type.parameterCount() > 0) {
            returnValue = MethodHandles.dropArguments(returnValue, 1, type.parameterArray());
        }
        MethodHandle releaseAndReturn = MethodHandles.foldArguments(returnValue, release);
        return MethodHandles.foldArguments(releaseAndReturn, acquireAndExecute);
    }

    private static Method findSingleMethodIn(Lookup lookup, Class<?> type, MethodType reference) {
        return doInClassHierarchy(type, c -> {
            Method bestMatch = null;
            Comparison matchingRank = null;
            boolean ambiguous = false;
            loop:
            for (Method m : c.getDeclaredMethods()) {
                if (!wouldBeVisible(lookup, m)) {
                    continue;
                }
                Comparison compare = compare(reference, m);
                switch (compare) {
                    case EQUAL:
                        if (matchingRank == Comparison.EQUAL) {
                            ambiguous = true;
                            break loop;
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
                        if (matchingRank != compare) {
                            matchingRank = compare;
                            ambiguous = false;
                        } else {
                            ambiguous = bestMatch != null;
                        }
                        bestMatch = m;
                        break;
                    default:
                        // Do nothing then
                }
            }

            if (ambiguous) {
                throw new AmbiguousMethodException("Multiple methods found with type " + reference + " in " + type.getName());
            }
            return bestMatch;
        });
    }

    /**
     * Finds the only method in the given interface that doesn't have a default implementation, i.e. it's the functional interface's only method.
     * Fail if the given type is not a functional interface.
     *
     * @param lambdaType The interface type
     * @return The found method
     */
    public static Method findLambdaMethodOrFail(Class<?> lambdaType) {
        return findLambdaMethod(lambdaType).orElseThrow(() -> new IllegalArgumentException(lambdaType.getName() + " is not a functional interface."));
    }

    /**
     * Finds the only method in the given interface that doesn't have a default implementation, i.e. it's the functional interface's only method.
     * Will return only one or no methods at all.
     *
     * @param lambdaType The interface type
     * @return The found method
     */
    public static Optional<Method> findLambdaMethod(Class<?> lambdaType) {
        if (!lambdaType.isInterface()) {
            throw new IllegalArgumentException(lambdaType.getName() + " should be an interface!");
        }
        Method found = null;
        for (Method m : lambdaType.getMethods()) {
            int modifiers = m.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isAbstract(modifiers)) {
                continue;
            }
            if (found == null) {
                found = m;
            } else {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(found);
    }

    /**
     * Creates a lambda factory for the given type which will then call the given target method.
     *
     * @param lookup       Must be the caller's lookup according to LambdaMetaFactory's documentation
     * @param targetMethod This will be called in the created lambda - it must be a direct handle, otherwise this
     *                     method will return null
     * @param lambdaType   The interface that specifies the lambda. The lambda method's argument size n must not exceed
     *                     the target's argument size t, and their types must be convertible to the last n arguments of the
     *                     target.
     * @return A MethodHandle that accepts the first (t-n) arguments of the target and returns an instance of lambdaType.
     */
    @Nullable
    public static MethodHandle createLambdaFactory(Lookup lookup, MethodHandle targetMethod, Class<?> lambdaType) {

        Method m = findLambdaMethodOrFail(lambdaType);
        String name = m.getName();
        Class<?>[] calledParameters = m.getParameterTypes();
        MethodType calledType = methodType(m.getReturnType(), calledParameters);
        MethodType targetType = targetMethod.type();
        Class<?>[] targetParameters = targetType.parameterArray();
        int methodParamSize = calledParameters.length;
        if (targetParameters.length < methodParamSize) {
            throw new IllegalArgumentException("targetMethod has too few parameters: Expected at least " + methodParamSize + ", actual: " + targetParameters.length);
        }
        int additional = targetParameters.length - methodParamSize;
        Class<?>[] additionalParameters = Arrays.copyOf(targetParameters, additional);
        MethodType factoryType = methodType(lambdaType, additionalParameters);
        MethodType instantiatedType;
        if (additional == 0) {
            instantiatedType = targetType;
        } else {
            Class<?>[] params = new Class<?>[methodParamSize];
            System.arraycopy(targetParameters, additional, params, 0, methodParamSize);
            instantiatedType = methodType(targetType.returnType(), params);
        }

        CallSite callSite;
        try {
            callSite = LambdaMetafactory.metafactory(lookup, name, factoryType, calledType, targetMethod, instantiatedType);
        } catch (LambdaConversionException ex) {
            if (ex.getMessage().contains("Unsupported MethodHandle kind")) {
                // Ugly check, but how to do better?
                return null;
            }
            throw new IllegalArgumentException("Cannot call " + targetMethod + " on " + m, ex);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage().contains("not a direct method handle") || ex.getMessage().contains(" is private:")) {
                // Ugly check, but how to do better?
                return null;
            }
            throw ex;
        }

        return callSite.getTarget();
    }

    /**
     * Creates an instance of the given lambda interface that will call the given target method.
     * <p>
     * You can see this as an abbreviation to first creating a factory handle via createLambdaFactory() and then
     * creating that instance by inserting the initializer values. But this will also work when the target handle is
     * not a direct one by falling back to a proxy implementation.
     *
     * @param targetMethod This method will be called. It must accept at least that many arguments as the lambda method.
     * @param lambdaType   The instantiated type. Must be a functional interface.
     * @param initializers These can be used to fill up the first target handle's arguments
     * @param <T>          The type
     * @return An instance of the lambda type
     */
    public static <T> T lambdafy(MethodHandle targetMethod, Class<T> lambdaType, Object... initializers) {
        return lambdafy(lookup(), targetMethod, lambdaType, initializers);
    }

    /**
     * Creates an instance of the given lambda interface that will call the given target method.
     * <p>
     * You can see this as an abbreviation to first creating a factory handle via createLambdaFactory() and then
     * creating that instance by inserting the initializer values. But this will also work when the target handle is
     * not a direct one by falling back to a proxy implementation.
     *
     * @param lookup The caller's lookup
     * @param targetMethod This method will be called. It must accept at least that many arguments as the lambda method.
     * @param lambdaType   The instantiated type. Must be a functional interface.
     * @param initializers These can be used to fill up the first target handle's arguments
     * @param <T>          The type
     * @return An instance of the lambda type
     */
    public static <T> T lambdafy(Lookup lookup, MethodHandle targetMethod, Class<T> lambdaType, Object... initializers) {
        MethodHandle lambdaFactory = createLambdaFactory(lookup, targetMethod, lambdaType);
        MethodHandle handle;
        if (lambdaFactory == null) {
            // Not a direct handle - use normal interface creation
            if (initializers.length == 0) {
                handle = targetMethod;
            } else {
                handle = MethodHandles.insertArguments(targetMethod, 0, initializers);
            }
            return MethodHandleProxies.asInterfaceInstance(lambdaType, handle);
        }
        try {
            return lambdaType.cast(lambdaFactory.invokeWithArguments(initializers));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new AssertionError("Creating lambda " + lambdaType.getName() + " failed.", t);
        }
    }
}