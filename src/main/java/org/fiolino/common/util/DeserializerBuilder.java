package org.fiolino.common.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by kuli on 07.01.16.
 */
public final class DeserializerBuilder {
    private static final Logger logger = Logger.getLogger(DeserializerBuilder.class.getName());

    private final Instantiator instantiator;
    private final Map<Class<?>, MethodHandle> deserializers = new HashMap<>();

    public DeserializerBuilder(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    /**
     * Creates a deserializer that accepts a String and returns the model's type with all fields filled that
     * are annotated with {@link org.fiolino.data.annotation.SerialFieldIndex}.
     */
    public MethodHandle getDeserializer(Class<?> type) {
        return deserializers.computeIfAbsent(type, t -> {
            MethodHandles.Lookup lookup = instantiator.getLookup().in(t);
            MethodHandle constructor = instantiator.findProvider(type);
            Deserializer deserializer = new Deserializer(constructor);
            analyze(lookup, t, deserializer);
            return deserializer.createDeserializer();
        });
    };

    private void analyze(MethodHandles.Lookup lookup, Class<?> type, Deserializer deserializer) {
//        ClassWalker<RuntimeException> walker = new ClassWalker<>();
//        walker.onField(f -> {
//            SerialFieldIndex fieldAnno = f.getAnnotation(SerialFieldIndex.class);
//            SerializeEmbedded embedAnno = f.getAnnotation(SerializeEmbedded.class);
//            if (fieldAnno == null && embedAnno == null) {
//                return;
//            }
//            MethodHandle setter = Methods.findSetter(lookup, f);
//            if (setter == null) {
//                logger.warn("No setter for " + f);
//                return;
//            }
//            if (fieldAnno != null) {
//                deserializer.setField(setter, f.getGenericType(), fieldAnno.value(), f::getName);
//            }
//            if (embedAnno != null) {
//                MethodHandle embedded = getDeserializer(f.getType());
//                deserializer.setEmbeddedField(setter, embedded, embedAnno.value(), f::getName);
//            }
//        });
//
//        walker.onMethod(m -> {
//            SerialFieldIndex fieldAnno = m.getAnnotation(SerialFieldIndex.class);
//            SerializeEmbedded embedAnno = m.getAnnotation(SerializeEmbedded.class);
//            if (fieldAnno == null && embedAnno == null) {
//                return;
//            }
//            if (m.getParameterCount() != 1) {
//                if (logger.isDebugEnabled()) {
//                    logger.debug("Ignoring " + m + " because it's not a setter.");
//                }
//                return;
//            }
//            MethodHandle setter;
//            try {
//                setter = lookup.unreflect(m);
//            } catch (IllegalAccessException e) {
//                logger.warn(m + " is not accessible!");
//                return;
//            }
//            if (fieldAnno != null) {
//                deserializer.setField(setter, m.getGenericParameterTypes()[0], fieldAnno.value(), m::getName);
//            }
//            if (embedAnno != null) {
//                MethodHandle embedded = deserializers.get(m.getParameterTypes()[0]);
//                deserializer.setEmbeddedField(setter, embedded, embedAnno.value(), m::getName);
//            }
//        });
//
//        walker.analyze(type);
    }
}
