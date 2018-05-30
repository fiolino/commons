package org.fiolino.common.processing;

import org.fiolino.common.analyzing.ModelInconsistencyException;
import org.fiolino.common.container.Container;
import org.fiolino.common.reflection.MethodLocator;
import org.fiolino.common.reflection.Methods;
import org.fiolino.common.util.Strings;
import org.fiolino.common.util.Types;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 13.02.15.
 */
public class ModelDescription extends AbstractConfigurationContainer {
    private final Class<?> modelType;
    private final MethodHandles.Lookup lookup;
    private final Map<Object, FieldDescription> valueDescriptions = new HashMap<>();

    /**
     * Public constructor for initial creation of root model.
     */
    public ModelDescription(Class<?> modelType, Container configuration) {
        this(publicLookup(), modelType, configuration);
    }

    /**
     * Public constructor for initial creation of root model.
     */
    public ModelDescription(MethodHandles.Lookup lookup, Class<?> modelType, Container configuration) {
        super(configuration);
        this.modelType = modelType;
        this.lookup = lookup.in(modelType);
    }

    /**
     * Creates a sub-model description from a parent path.
     */
    private ModelDescription(FieldDescription relationHolder) throws ModelInconsistencyException {
        super(relationHolder.getConfiguration());
        ModelDescription parent = relationHolder.owningModel();
        this.modelType = relationHolder.getTargetType();
        this.lookup = parent.lookup.in(modelType);
    }

    public Class<?> getModelType() {
        return modelType;
    }

    public MethodHandle createConstructor(Class<?>... parameterTypes) {
        try {
            return lookup.findConstructor(modelType, methodType(void.class, parameterTypes));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("No public constructor in " + modelType.getName(), ex);
        }
    }

    public FieldDescription getValueDescription(Field field) {
        return valueDescriptions.computeIfAbsent(field, f -> new FieldValueDescription((Field) f));
    }

    private String getNameFor(Method method) {
        String n = method.getName();
        switch (method.getParameterTypes().length) {
            case 0:
                if (method.getReturnType() == void.class) {
                    return n;
                }
                if (n.startsWith("is") && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    return Strings.removeLeading(n, "is");
                }
                return Strings.removeLeading(n, "get");
            case 1:
                return Strings.removeLeading(n, "set");
            default:
                return n;
        }
    }

    public FieldDescription getValueDescription(Method method) throws ModelInconsistencyException {
        return valueDescriptions.computeIfAbsent(method, m -> {
            if (method.getReturnType() == void.class) {
                if (method.getParameterCount() == 0) {
                    throw new ModelInconsistencyException(method + " is neither a getter nor a setter.");
                }
                return new SetterMethodFieldDescription(method);
            }
            return new GetterMethodFieldDescription(method);
        });
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " for " + modelType.getName();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this ||
                obj != null && obj.getClass().equals(getClass()) && modelType.equals(((ModelDescription) obj).getModelType());
    }

    @Override
    public int hashCode() {
        return modelType.hashCode() + 101;
    }

    private abstract class AbstractFieldDescription extends AbstractConfigurationContainer implements FieldDescription {

        private ModelDescription target;

        AbstractFieldDescription() {
            super(ModelDescription.this.getConfiguration().createSubContainer());
        }

        @Override
        public final ModelDescription owningModel() {
            return ModelDescription.this;
        }

        @Override
        public ModelDescription getRelationTarget() throws ModelInconsistencyException {
            if (target == null) {
                target = new ModelDescription(this);
            }
            return target;
        }

        @Override
        public String toString() {
            return "Description of " + printString() + " in " + ModelDescription.this;
        }

        String printString() {
            return getName();
        }

        @Override
        public final Class<?> getTargetType() throws ModelInconsistencyException {
            Class<?> valueType = getValueType();
            Type genericType = getGenericType();
            if (Map.class.isAssignableFrom(valueType)) {
                if (!String.class.equals(Types.erasedArgument(genericType, Map.class, 0, Types.Bounded.LOWER))) {
                    throw new AssertionError("Bad type for " + getName() + ": Can only index Maps with String keys.");
                }
                genericType = Types.genericArgument(genericType, Map.class, 1);
                valueType = Types.erasureOf(genericType);
            }
            if (Collection.class.isAssignableFrom(valueType)) {
                valueType = Types.erasedArgument(genericType, Collection.class, 0, Types.Bounded.UPPER);
            }
            return valueType;
        }

    }

    private class FieldValueDescription extends AbstractFieldDescription {
        private final Field field;

        FieldValueDescription(Field f) {
            super();
            this.field = f;
        }

        @Override
        public String getName() {
            return field.getName();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<?> getValueType() {
            return field.getType();
        }

        @Override
        public Type getGenericType() {
            return field.getGenericType();
        }

        @Override
        public AccessibleObject getAttachedInstance() {
            return field;
        }

        @Nullable
        @Override
        public MethodHandle createGetter(Class<?>... additionalTypes) {
            return MethodLocator.findGetter(lookup, field, additionalTypes);
        }

        @Nullable
        @Override
        public MethodHandle createSetter(Class<?>... additionalTypes) {
            return MethodLocator.findSetter(lookup, field, additionalTypes);
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return field.getAnnotation(annotationType);
        }
    }

    private abstract class MethodFieldDescription extends AbstractFieldDescription {
        private final String name;
        final Method method;

        MethodFieldDescription(Method method) {
            super();
            this.method = method;
            name = getNameFor(method);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public AccessibleObject getAttachedInstance() {
            return method;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return method.getAnnotation(annotationType);
        }

        MethodHandle transform(Class<?>[] additionalTypes, int leading) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            int numberOfMatching = parameterTypes.length - leading;

            for (int i=0; i < numberOfMatching; i++) {
                if (!additionalTypes[i].equals(parameterTypes[i + leading])) return null;
            }
            MethodHandle h;
            try {
                h = lookup.unreflect(method);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(lookup + " does not seem to be able to unreflect " + method, ex);
            }
            int numberOfIgnored = additionalTypes.length - numberOfMatching;
            if (numberOfIgnored == 0) {
                return h;
            }
            Class<?>[] ignoredArgument = new Class<?>[numberOfIgnored];
            System.arraycopy(additionalTypes, numberOfMatching, ignoredArgument, 0, numberOfIgnored);
            return MethodHandles.dropArguments(h, numberOfMatching + leading + 1, ignoredArgument);
        }
    }

    private class GetterMethodFieldDescription extends MethodFieldDescription {
        GetterMethodFieldDescription(Method method) {
            super(method);
            assert method.getReturnType() != void.class;
        }

        @Override
        public Class<?> getValueType() {
            return method.getReturnType();
        }

        @Override
        public Type getGenericType() {
            return method.getGenericReturnType();
        }

        @Nullable
        @Override
        public MethodHandle createGetter(Class<?>[] additionalTypes) {
            return transform(additionalTypes, 0);
        }

        @Nullable
        @Override
        public MethodHandle createSetter(Class<?>[] additionalTypes) {
            return null;
        }
    }

    private class SetterMethodFieldDescription extends MethodFieldDescription {
        SetterMethodFieldDescription(Method method) {
            super(method);
            assert method.getParameterCount() != 0;
            assert method.getReturnType() == void.class;
        }

        @Override
        public Class<?> getValueType() {
            return method.getParameterTypes()[0];
        }

        @Override
        public Type getGenericType() {
            return method.getGenericParameterTypes()[0];
        }

        @Nullable
        @Override
        public MethodHandle createGetter(Class<?>[] additionalTypes) {
            return null;
        }

        @Nullable
        @Override
        public MethodHandle createSetter(Class<?>[] additionalTypes) {
            return transform(additionalTypes, 1);
        }
    }
}
