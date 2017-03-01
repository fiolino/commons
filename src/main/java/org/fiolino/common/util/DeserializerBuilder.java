package org.fiolino.common.util;

import org.fiolino.common.analyzing.ClassWalker;
import org.fiolino.common.analyzing.ModelInconsistencyException;
import org.fiolino.common.reflection.Methods;
import org.fiolino.data.annotation.SerialFieldIndex;
import org.fiolino.data.annotation.SerializeEmbedded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Created by kuli on 07.01.16.
 */
public final class DeserializerBuilder {
    private static final Logger logger = LoggerFactory.getLogger(DeserializerBuilder.class);
    private static final ClassValue<Deserializer> deserializers = new ClassValue<Deserializer>() {
        @Override
        protected Deserializer computeValue(Class<?> type) {
            DeserializerBuilder builder = new DeserializerBuilder();
            MethodHandles.Lookup lookup = MethodHandles.publicLookup().in(type);
            builder.analyze(lookup);
            return builder.createDeserializer();
        }
    };
    private static final ClassValue<MethodHandle> deserializationHandles = new ClassValue<MethodHandle>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            Deserializer deserializer = deserializers.get(type);
            return deserializer.createDeserializerFor(type);
        }
    };

    /**
     * Creates a deserializer that accepts a String and returns the model's type with all fields filled that
     * are annotated with {@link org.fiolino.data.annotation.SerialFieldIndex}.
     */
    public static MethodHandle getDeserializer(Class<?> modelClass) throws ModelInconsistencyException {
        return deserializationHandles.get(modelClass);
    }

    private abstract static class SetterWithSomething {
        final MethodHandle setter;

        SetterWithSomething(MethodHandle setter) {
            this.setter = setter;
        }
    }

    private static class SetterWithType extends SetterWithSomething {
        final Type type;

        SetterWithType(MethodHandle setter, Type type) {
            super(setter);
            this.type = type;
        }
    }

    private static class SetterWithEmbedded extends SetterWithSomething {
        final Deserializer deserializer;

        SetterWithEmbedded(MethodHandle setter, Deserializer deserializer) {
            super(setter);
            this.deserializer = deserializer;
        }
    }

    private SetterWithSomething[] setters = new SetterWithSomething[0];

    private DeserializerBuilder() {
    }

    private void analyze(final MethodHandles.Lookup lookup) {
        ClassWalker<RuntimeException> walker = new ClassWalker<>();
        walker.onField(f -> {
            SerialFieldIndex fieldAnno = f.getAnnotation(SerialFieldIndex.class);
            SerializeEmbedded embedAnno = f.getAnnotation(SerializeEmbedded.class);
            if (fieldAnno == null && embedAnno == null) {
                return;
            }
            MethodHandle setter = Methods.findSetter(lookup, f);
            if (setter == null) {
                logger.warn("No setter for " + f);
                return;
            }
            if (fieldAnno != null) {
                setFieldNumber(f.getName(), setter, f.getGenericType(), fieldAnno.value());
            }
            if (embedAnno != null) {
                Deserializer deserializer = deserializers.get(f.getType());
                setFieldNumber(f.getName(), setter, deserializer, embedAnno.value());
            }
        });

        walker.onMethod(m -> {
            SerialFieldIndex fieldAnno = m.getAnnotation(SerialFieldIndex.class);
            SerializeEmbedded embedAnno = m.getAnnotation(SerializeEmbedded.class);
            if (fieldAnno == null && embedAnno == null) {
                return;
            }
            if (m.getParameterCount() != 1) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Ignoring " + m + " because it's not a setter.");
                }
                return;
            }
            MethodHandle setter;
            try {
                setter = lookup.unreflect(m);
            } catch (IllegalAccessException e) {
                logger.warn(m + " is not accessible!");
                return;
            }
            if (fieldAnno != null) {
                setFieldNumber(m.getName(), setter, m.getGenericParameterTypes()[0], fieldAnno.value());
            }
            if (embedAnno != null) {
                Deserializer deserializer = deserializers.get(m.getParameterTypes()[0]);
                setFieldNumber(m.getName(), setter, deserializer, embedAnno.value());
            }
        });

        walker.analyze(lookup.lookupClass());
    }

    private Deserializer createDeserializer() {
        Deserializer deserializer = new Deserializer();
        for (SetterWithSomething sws : setters) {
            if (sws == null) {
                deserializer.ignoreNext();
            } else if (sws instanceof SetterWithType) {
                deserializer.addSetter(sws.setter, ((SetterWithType) sws).type);
            } else {
                deserializer.addEmbedded(sws.setter, ((SetterWithEmbedded) sws).deserializer);
            }
        }
        return deserializer;
    }

    private void setFieldNumber(String name, MethodHandle setter, Type type, int fieldIndex) {
        checkSize(name, fieldIndex);
        setters[fieldIndex] = new SetterWithType(setter, type);
    }

    private void setFieldNumber(String name, MethodHandle setter, Deserializer deserializer, int fieldIndex) {
        checkSize(name, fieldIndex);
        setters[fieldIndex] = new SetterWithEmbedded(setter, deserializer);
    }

    private void checkSize(String name, int fieldIndex) {
        if (fieldIndex < setters.length) {
            if (setters[fieldIndex] != null) {
                logger.info("Overwriting serial field " + fieldIndex + " with " + name);
            }
        } else {
            setters = Arrays.copyOf(setters, fieldIndex + 1);
        }
    }
}
