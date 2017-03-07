package org.fiolino.common.processing;

import org.fiolino.common.analyzing.ModelInconsistencyException;
import org.fiolino.common.container.Container;
import org.fiolino.common.reflection.Methods;
import org.fiolino.common.util.Strings;
import org.fiolino.common.util.Types;
import org.fiolino.data.annotation.TargetType;

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
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 13.02.15.
 */
public class ModelDescription extends AbstractConfigurationContainer {
    private final Class<?> modelType;
    private final MethodHandles.Lookup lookup;
    private final Map<String, ValueDescription> valueDescriptions = new HashMap<>();

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
    private ModelDescription(ValueDescription relationHolder) throws ModelInconsistencyException {
        super(relationHolder.getConfiguration());
        ModelDescription parent = relationHolder.owningModel();
        this.modelType = relationHolder.getTargetType();
        this.lookup = parent.lookup.in(modelType);
        Class<?> valueType = relationHolder.getValueType();
        if (Map.class.isAssignableFrom(valueType)) {
            valueType = Types.rawArgument(relationHolder.getGenericType(), Map.class, 1, Types.Bounded.UPPER);
        }
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

    private ValueDescription getValueDescription(String name, Function<String, ValueDescription> factory) {
        return valueDescriptions.computeIfAbsent(name, factory);
    }

    public ValueDescription getValueDescription(final Field field) {
        String name = field.getName();
        return getValueDescription(name, n -> new FieldValueDescription(field));
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

    public ValueDescription getValueDescription(final Method method) throws ModelInconsistencyException {
        final String name = getNameFor(method);
        return getValueDescription(name, n -> {
            Class<?> valueType;
            Type type;
            Type[] parameterTypes = method.getGenericParameterTypes();
            switch (parameterTypes.length) {
                case 0:
                    type = method.getGenericReturnType();
                    valueType = method.getReturnType();
                    break;
                case 1:
                    type = parameterTypes[0];
                    valueType = method.getParameterTypes()[0];
                    break;
                default:
                    throw new AssertionError(method + " is neither a getter nor a setter.");
            }
            return new MethodValueDescription(name, type, valueType, method);
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

    private Class<?> getTargetTypeFor(Type genericType, AccessibleObject annotated) {
        Class<?> valueType = Types.rawType(genericType);
        if (Map.class.isAssignableFrom(valueType)) {
            if (!String.class.equals(Types.rawArgument(genericType, Map.class, 0, Types.Bounded.LOWER))) {
                throw new AssertionError("Bad type for " + annotated + ": Can only index Maps with String keys.");
            }
            genericType = Types.genericArgument(genericType, Map.class, 1);
            valueType = Types.rawType(genericType);
        }
        if (Collection.class.isAssignableFrom(valueType)) {
            valueType = Types.rawArgument(genericType, Collection.class, 0, Types.Bounded.UPPER);
        }
        TargetType annotation = annotated.getAnnotation(TargetType.class);
        if (annotation == null) {
            return valueType;
        }
        Class<?> redirect = annotation.value();
        if (!valueType.isAssignableFrom(redirect)) {
            throw new ModelInconsistencyException("Wrong annotation @" + TargetType.class.getSimpleName() + " on " + annotated
                    + ": " + redirect.getName() + " is not a subclass of " + valueType.getName());
        }
        return redirect;
    }

    private abstract class AbstractValueDescription extends AbstractConfigurationContainer implements ValueDescription {

        private final String name;
        private ModelDescription target;

        AbstractValueDescription(String name) {
            super(ModelDescription.this.getConfiguration().createSubContainer());
            this.name = name;
        }

        @Override
        public final ModelDescription owningModel() {
            return ModelDescription.this;
        }

        @Override
        public String getName() {
            return name;
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
    }

    private class FieldValueDescription extends AbstractValueDescription {
        private final Field field;

        public FieldValueDescription(Field f) {
            super(f.getName());
            this.field = f;
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
        public Class<?> getTargetType() throws ModelInconsistencyException {
            return getTargetTypeFor(getGenericType(), field);
        }

        @Override
        public MethodHandle createGetter() {
            return Methods.findGetter(lookup, field);
        }

        @Override
        public MethodHandle createSetter() {
            return Methods.findSetter(lookup, field);
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return field.getAnnotation(annotationType);
        }
    }

    private class MethodValueDescription extends AbstractValueDescription {
        private final Type type;
        private final Class<?> valueType;
        private final Method method;

        public MethodValueDescription(String name, Type type, Class<?> valueType, Method method) {
            super(name);
            this.type = type;
            this.valueType = valueType;
            this.method = method;
        }

        @Override
        public Class<?> getValueType() {
            return valueType;
        }

        @Override
        public Type getGenericType() {
            return type;
        }

        @Override
        public Class<?> getTargetType() {
            return getTargetTypeFor(type, method);
        }

        @Override
        public MethodHandle createGetter() {
            return Methods.findGetter(lookup, owningModel().getModelType(), getName(), getValueType());
        }

        @Override
        public MethodHandle createSetter() {
            return Methods.findSetter(lookup, owningModel().getModelType(), getName(), getValueType());
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return method.getAnnotation(annotationType);
        }
    }
}
