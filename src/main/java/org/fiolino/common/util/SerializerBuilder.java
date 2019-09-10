package org.fiolino.common.util;

import org.fiolino.annotations.SerialFieldIndex;
import org.fiolino.annotations.SerializeEmbedded;
import org.fiolino.common.analyzing.ClassWalker;
import org.fiolino.common.ioc.Instantiator;
import org.fiolino.common.reflection.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Serializes instances into Strings.
 * <p>
 * The serialized class should have annotations @{@link SerialFieldIndex} or @{@link SerializeEmbedded}
 * on its fields or getters/setters.
 * <p>
 * Individual values are separated by a colon.
 * <p>
 * Created by kuli on 29.12.15.
 */
public class SerializerBuilder {

    private static final Logger logger = Logger.getLogger(SerializerBuilder.class.getName());

    public static final Function<MethodHandles.Lookup, MethodHandle> BY_ANNOTATION_PROVIDER = Registry.<Function<MethodHandles.Lookup, MethodHandle>>buildForFunctionalType(l -> {
        SerializerBuilder b = new SerializerBuilder(l);
        b.analyze();
        return b.buildSerializingHandle();
    }).getAccessor();

    private void analyze() {
        ClassWalker<RuntimeException> walker = new ClassWalker<>();
        walker.onField(f -> {
            SerialFieldIndex fieldAnno = f.getAnnotation(SerialFieldIndex.class);
            SerializeEmbedded embedAnno = f.getAnnotation(SerializeEmbedded.class);
            if (fieldAnno == null && embedAnno == null) {
                return;
            }
            MethodHandle getter = MethodLocator.findGetter(lookup, f);
            if (getter == null) {
                logger.log(Level.WARNING, () -> "No getter for " + f);
                return;
            }
            if (fieldAnno != null) {
                addSerialField(getter, fieldAnno.value());
            }
            if (embedAnno != null) {
                Class<?> embeddedType = f.getType();
                MethodHandle s = BY_ANNOTATION_PROVIDER.apply(lookup.in(embeddedType));
                addAppender(s);
                addSerialField(getter, embedAnno.value());
            }
        });

        walker.onMethod(m -> {
            SerialFieldIndex fieldAnno = m.getAnnotation(SerialFieldIndex.class);
            SerializeEmbedded embedAnno = m.getAnnotation(SerializeEmbedded.class);
            if (fieldAnno == null && embedAnno == null) {
                return;
            }
            if (m.getParameterCount() != 0 || m.getReturnType() == void.class) {
                logger.fine(() -> "Ignoring " + m + " because it's not a getter.");
                return;
            }
            MethodHandle getter;
            try {
                getter = lookup.unreflect(m);
            } catch (IllegalAccessException e) {
                logger.log(Level.WARNING, () -> m + " is not accessible!");
                return;
            }
            if (fieldAnno != null) {
                addSerialField(getter, fieldAnno.value());
            }
            if (embedAnno != null) {
                Class<?> embeddedType = m.getReturnType();
                MethodHandle s = BY_ANNOTATION_PROVIDER.apply(lookup.in(embeddedType));
                addAppender(s);
                addSerialField(getter, embedAnno.value());
            }
        });

        walker.analyze(getType());
        validateNotEmpty();
    }

    private static final CharSet QUOTED_CHARACTERS = CharSet.of(":,()");
    private static final MethodHandle DATE_GETTIME;
    private static final List<MethodHandle> INITIAL_APPENDERS;

    static {
        try {
            MethodHandle getTime = publicLookup().findVirtual(Date.class, "getTime", methodType(long.class));
            MethodHandle valueOf = publicLookup().findStatic(String.class, "valueOf", methodType(String.class, long.class));
            DATE_GETTIME = MethodHandles.filterReturnValue(getTime, valueOf);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }

        INITIAL_APPENDERS = addAppendersFrom(Appenders.getLookup(), new ArrayList<>(), Appenders.class);
    }

    private static <T extends Collection<MethodHandle>> T addAppendersFrom(MethodHandles.Lookup lookup, T appenders, Class<?> appenderContainer) {
        Instantiator i = Instantiator.forLookup(lookup);
        MethodLocator.forLocal(lookup, appenderContainer).methods()
                .filter(info -> info.getMethod().getReturnType() == void.class)
                .filter(info -> info.getMethod().getParameterCount() == 2)
                .filter(info -> info.getMethod().getParameterTypes()[0].equals(StringBuilder.class))
                .map(info -> info.getStaticHandle(() -> Instantiator.forLookup(lookup).instantiate(appenderContainer)))
                .forEach(appenders::add);
        return appenders;
    }

    private static boolean isAppender(Method m) {
        return m.getParameterCount() == 2 && StringBuilder.class.equals(m.getParameterTypes()[0]);
    }

    private static boolean isAppender(MethodHandle h) {
        return h.type().parameterCount() == 2 && StringBuilder.class.equals(h.type().parameterType(0));
    }

    private MethodHandle[] getters = new MethodHandle[0];
    private final MethodHandles.Lookup lookup;
    private final ConverterLocator converterLocator;
    private final List<MethodHandle> appenders;

    private SerializerBuilder(MethodHandles.Lookup lookup) {
        this(lookup, Converters.defaultConverters.register(DATE_GETTIME));
    }

    private SerializerBuilder(MethodHandles.Lookup lookup, ConverterLocator converterLocator) {
        this.lookup = lookup;
        this.converterLocator = converterLocator;

        appenders = new ArrayList<>(INITIAL_APPENDERS);
    }

    public void addAppenders(MethodHandles.Lookup lookup, Class<?> appenderContainer) {
        addAppendersFrom(lookup, appenders, appenderContainer);
    }

    public void addAppender(MethodHandle appender) {
        if (!isAppender(appender)) {
            throw new IllegalArgumentException(appender + " should accept a StringBuilder and a bean.");
        }
        appenders.add(appender);
    }

    private void validateNotEmpty() {
        if (getters.length == 0) {
            throw new IllegalStateException("No serialized fields in " + getType().getName());
        }
    }

    @Override
    public String toString() {
        return "SerializerBuilder for " + getType().getName();
    }

    public Class<?> getType() {
        return lookup().lookupClass();
    }

    private void addGetter(int pos, MethodHandle getter) {
        int n = getters.length;
        if (pos >= n) {
            getters = Arrays.copyOf(getters, pos + 1);
        }
        getters[pos] = getter;
    }

    /**
     * Adds some getter as a serial field.
     *
     * @param getter The getter handle
     * @param fieldIndex The index
     */
    public void addSerialField(MethodHandle getter, int fieldIndex) {
        MethodType getterType = getter.type();
        if (getterType.returnType() == void.class) {
            throw new IllegalArgumentException("Getter " + getter + " should return some value!");
        }
        if (getterType.parameterCount() != 1) {
            throw new IllegalArgumentException("Getter " + getter + " should accept some bean!");
        }
        if (!getterType.parameterType(0).isAssignableFrom(getType())) {
            throw new IllegalArgumentException("Getter " + getter + " should accept " + getType().getName());
        }
        getter = getter.asType(getterType.changeParameterType(0, getType()));
        addGetter(fieldIndex, getter);
    }

    MethodHandle buildSerializingHandle() {
        validateNotEmpty();

        MethodHandle all = null;
        for (MethodHandle g : getters) {
            if (g == null) continue;

            Class<?> r = g.type().returnType();
            MethodHandle h = findHandleFor(r);
            h = h.asType(methodType(void.class, StringBuilder.class, r));
            h = MethodHandles.filterArguments(h, 1, g);

            if (all == null) all = h;
            else {
                all = MethodHandles.foldArguments(h, all);
            }
        }

        return all;
    }

    public Serializer getSerializerFor(Class<?> type) {
        return new Serializer(type, buildSerializingHandle());
    }

    private MethodHandle findHandleFor(Class<?> type) {
        try {
            Converter converter = converterLocator.find(type, String.class, int.class, long.class, double.class, float.class, boolean.class);
            MethodHandle append = findAppendMethod(converter.getTargetType());
            MethodHandle c = converter.getConverter();
            if (c == null) {
                return append;
            }
            return MethodHandles.filterArguments(append, 1, c);
        } catch (NoMatchingConverterException ex) {
            return findAppendMethod(Object.class);
        }
    }

    private MethodHandle findAppendMethod(Class<?> printedType) {
        MethodHandle found = null;
        int d = Integer.MAX_VALUE;
        for (MethodHandle a : appenders) {
            Class<?> appenderType = a.type().parameterType(1);
            int distance = Types.distanceOf(appenderType, printedType);
            if (distance == 0) {
                return a;
            }
            if (distance > 0 && distance < d) {
                d = distance;
                found = a;
            }
        }
        return found == null ? findDirectAppendMethod(printedType) : found;
    }

    private MethodHandle findDirectAppendMethod(Class<?> printedType) {
        MethodHandle sbAppend;
        try {
            sbAppend = publicLookup().findVirtual(StringBuilder.class, "append", methodType(StringBuilder.class, printedType));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("Missing StringBuilder.append(" + printedType.getName() + ")", ex);
        }
        return sbAppend.asType(methodType(void.class, StringBuilder.class, printedType));
    }

    @SuppressWarnings("unused")
    private static class Appenders {
        static MethodHandles.Lookup getLookup() {
            return lookup();
        }

        private static void append(StringBuilder sb, Iterable<?> coll) {
            boolean first = true;
            for (Object v : coll) {
                if (first) {
                    first = false;
                } else {
                    sb.append(',');
                }
                if (v == null) {
                    continue;
                }
                append(sb, v);
            }
        }

        private static void append(StringBuilder sb, Object val) {
            if (val instanceof String) {
                append(sb, (String) val);
            } else {
                sb.append(val);
            }
        }

        private static void append(StringBuilder sb, String s) {
            if (shouldBeQuoted(s)) {
                Strings.appendQuotedString(sb, s);
            } else {
                sb.append(s);
            }
        }

        private static boolean shouldBeQuoted(String val) {
            return val.isEmpty() || QUOTED_CHARACTERS.isContainedIn(val) || val.charAt(0) == '"';
        }
    }
}
