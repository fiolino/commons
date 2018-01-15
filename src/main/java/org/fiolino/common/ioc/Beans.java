package org.fiolino.common.ioc;

import org.fiolino.annotations.Component;
import org.fiolino.annotations.Factory;
import org.fiolino.annotations.Inject;
import org.fiolino.annotations.Property;
import org.fiolino.common.processing.Processor;
import org.fiolino.common.reflection.Converters;
import org.fiolino.common.util.Types;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by kuli on 24.03.15.
 */
public class Beans {

    private enum InitializationPlaceholder {
        VALUE
    }

    private static final Logger logger = Logger.getLogger(Beans.class.getName());

    private static final String[] PACKAGES_TO_SCAN = Properties.getMultipleEntries("org.fiolino.annotationscan.prefix");

    private static final Map<String, Map<Class<?>, Object>> beanCache = new HashMap<>();

    private static final Reflections REF = new Reflections((Object[]) PACKAGES_TO_SCAN);

    private Beans() {
    }


    /**
     * Reset this bean. Use only in test cases!
     */
    public static void reset() {
        beanCache.clear();
    }

    public static Reflections getReflections() {
        return REF;
    }

    private static boolean checkParameters(Class<?>[] types, Object[] values) {
        int n = values.length;
        if (types.length < n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            Object v = values[i];
            if (v == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!Types.isAssignableFrom(types[i], v.getClass())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new instance with the given parameter values as the first constructor arguments,
     * and then some additional optional parameters behind which must be annotated with @Property or @Inject.
     */
    public static <T> T instantiate(Class<T> type, Object... parameters) {
        Constructor<?> found = null;
        Object[] values = parameters;
        int n = parameters.length;

        outer:
        for (Constructor<?> c : type.getConstructors()) {
            Class<?>[] types = c.getParameterTypes();
            if (!checkParameters(types, parameters)) {
                continue;
            }
            Annotation[][] annotations = c.getParameterAnnotations();
            int numParameters = annotations.length;
            if (numParameters == n) {
                if (found == null) {
                    found = c;
                }
                continue;
            }

            Object[] thisValues = Arrays.copyOf(parameters, numParameters);
            for (int i = n; i < numParameters; i++) {
                Annotation[] paramAnnotations = annotations[i];
                Property p = getAnnotationOf(Property.class, paramAnnotations);
                if (p == null) {
                    Inject b = getAnnotationOf(Inject.class, paramAnnotations);
                    if (b == null) {
                        if (i > n) {
                            // There was already an annotated parameter
                            throw new BeanCreationException(type + " has constructor where not all parameters are annotated with @Property or @Inject.");
                        }
                        continue outer;
                    }
                    String name = b.value();
                    if ("".equals(name)) name = null;
                    Object bean = Beans.get(name, types[i]);
                    thisValues[i] = bean;
                    continue;
                }
                String key = p.value();
                String defaultValue = p.defaultValue();
                if (String[].class.equals(types[i])) {
                    thisValues[i] = "".equals(defaultValue) ? Properties.getMultipleEntries(key) : Properties.getMultipleEntries(key, new String[] {
                            defaultValue
                    });
                    continue;
                }
                if ("".equals(defaultValue)) {
                    defaultValue = null;
                }
                String propertyValue = Properties.getSingleEntry(key, defaultValue);
                thisValues[i] = propertyValue;
            }

            if (found != null && found.getParameterTypes().length > n) {
                throw new BeanCreationException(type + " has more than one constructor with @Property parameters!");
            }
            found = c;
            values = thisValues;
        }

        if (found == null) {
            throw new BeanCreationException(type + " has no suitable public constructor; must be either an empty one, or where all parameters are annotated with @Property.");
        }

        T bean = instantiate(type, found, values);
        return Processor.postProcess(bean);
    }

    private static <T> Factory<T> findFactoryFor(String name, Class<T> type) {
        Class<? extends Factory> factoryClass = findFactoryClassFor(name, type);
        if (factoryClass == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Factory<T> factory = get(factoryClass);
        return factory;
    }

    private static <T> Class<? extends Factory> findFactoryClassFor(String name, Class<T> type) {
        Class<? extends Factory> found = null;
        int foundDistance = Integer.MAX_VALUE;
        String nameToLookFor = name == null ? defaultBeanNameFor(type) : name;

        for (Class<? extends Factory> f : getReflections().getSubTypesOf(Factory.class)) {
            Component component = f.getAnnotation(Component.class);
            if (component == null) {
                continue;
            }
            String componentName = component.value();
            if (componentName.length() == 0) {
                if (name != null && !defaultBeanNameFor(f).equals(name)) {
                    continue;
                }
            } else {
                if (!componentName.equals(nameToLookFor)) {
                    continue;
                }
            }

            Class<?> argument = Types.erasedArgument(f, Factory.class, 0, Types.Bounded.UPPER);
            int distance = Types.distanceOf(argument, type);
            if (distance == 0) {
                return f;
            }
            if (distance == -1) {
                continue;
            }
            if (distance < foundDistance) {
                foundDistance = distance;
                found = f;
            }
        }

        return found;
    }

    private static <T> T instantiate(Class<T> type, Constructor<?> c, Object... parameters) {
        Class<?>[] parameterTypes = c.getParameterTypes();
        if (parameters.length != parameterTypes.length) {
            throw new BeanCreationException("Cannot instantiate " + c + " because it expects " + parameterTypes.length + " parameters, but " + parameters.length + " are given.");
        }
        Object[] values = convert(parameterTypes, parameters);

        MethodHandle constructor;
        try {
            constructor = MethodHandles.publicLookup().in(type).unreflectConstructor(c);
        } catch (IllegalAccessException ex) {
            throw new BeanCreationException("Cannot access " + c, ex);
        }

        try {
            Object instance = constructor.invokeWithArguments(values);
            return type.cast(instance);
        } catch (Throwable ex) {
            throw new BeanCreationException("Constructor of " + type.getName() + " threw exception.", ex);
        }
    }

    private static Object[] convert(Class<?>[] parameterTypes, Object... values) {
        int n = values.length;
        if (n == 0) {
            return values;
        }
        Object[] converted = new Object[n];
        for (int i = 0; i < n; i++) {
            Object convertedValue;
            Object v = values[i];
            if (v instanceof String) {
                Class<?> type = Types.toWrapper(parameterTypes[i]);
                convertedValue = Converters.convertValue(Converters.defaultConverters, v, type);
            } else {
                convertedValue = v;
            }
            converted[i] = convertedValue;
        }

        return converted;
    }

    private static <A extends Annotation> A getAnnotationOf(Class<A> annotationType, Annotation[] annotations) {
        for (Annotation a : annotations) {
            if (annotationType.isInstance(a)) {
                return annotationType.cast(a);
            }
        }
        return null;
    }

    public static <T> T get(Class<T> type) {
        return get(null, type);
    }

    private static String defaultBeanNameFor(Class<?> type) {
        String name = type.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public static <T> T get(String name, Class<T> type) {
        synchronized (type) {
            Map<Class<?>, Object> objectMap = beanCache.computeIfAbsent(name, n -> new HashMap<>());
            Object current = objectMap.get(type);
            if (current != null) {
                if (current == InitializationPlaceholder.VALUE) {
                    logger.log(Level.WARNING, () -> "Recursion detected in " + type + " with name " + name);
                    return null;
                }
                return type.cast(current);
            }
            T bean;
            Class<? extends T> found = null;
            objectMap.put(type, InitializationPlaceholder.VALUE);
            try {
                Factory<T> factory = findFactoryFor(name, type);
                if (factory != null) {
                    try {
                        bean = factory.create();
                    } catch (Throwable t) {
                        throw new BeanCreationException("Factory " + factory + " failed.", t);
                    }
                } else {
                    found = findImplementingClass(name, type);
                    bean = instantiate(found);
                }
            } finally {
                objectMap.remove(type);
            }
            objectMap.put(type, bean);
            if (found != null) {
                objectMap.put(found, bean);
            }
            return bean;
        }
    }

    public static <T> Class<? extends T> findImplementingClass(String name, Class<T> type) {
        String nameToLookFor = name == null ? defaultBeanNameFor(type) : name;

        Set<Class<? extends T>> implementors = getReflections().getSubTypesOf(type);
        implementors.add(type);

        Class<? extends T> single = null;
        Class<? extends T> found = null;
        boolean onlyOne = true;
        for (Class<? extends T> t : implementors) {
            if (t.isInterface() || Modifier.isAbstract(t.getModifiers()))
                continue;
            Component component = t.getAnnotation(Component.class);
            if (component == null) {
                continue;
            }
            if (onlyOne) {
                onlyOne = false;
                single = t;
            } else {
                single = null;
            }
            String componentName = component.value();
            if (componentName.equals("")) {
                componentName = defaultBeanNameFor(t);
            }
            if (nameToLookFor.equals(componentName)) {
                if (found != null) {
                    throw new BeanCreationException("Found at least two beans with name " + nameToLookFor + ": " + found.getName() + " and " + t.getName());
                }
                found = t;
            }
        }

        if (found == null) {
            found = single;
            if (found == null) {
                throw new NoSuchBeanException("No bean with name " + nameToLookFor + " and type " + type.getName() + " found.");
            }
        }
        return found;
    }
}
