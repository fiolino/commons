package org.fiolino.common.var;

import org.fiolino.common.util.Strings;
import org.fiolino.data.annotation.Debugged;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.util.*;


final class ObjectToStringConverter {

    private static final int INVISIBLE_MODIFIERS = Modifier.STATIC | Modifier.TRANSIENT;

    private static final Map<Class<?>, MethodHandle> converterMethods;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        converterMethods = new HashMap<>();
        for (Method m : ObjectToStringConverter.class.getDeclaredMethods()) {
            // if (m.isAnnotationPresent(Appender.class)) { -- Java8
            if (m.getAnnotation(Appender.class) != null) {
                Class<?>[] argTypes = m.getParameterTypes();
                if (argTypes.length < 2) {
                    throw new AssertionError("Method " + m + " should have at least a parameter for the string builder and the conversion object");
                }

                MethodHandle handle;
                try {
                    handle = lookup.unreflect(m);
                } catch (IllegalAccessException ex) {
                    throw new AssertionError("Method " + m + " cannot be accessed.", ex);
                }
                if (argTypes.length == 2) {
                    handle = MethodHandles.dropArguments(handle, 3, int.class);
                }
                converterMethods.put(argTypes[1], handle);
            }
        }
    }

    private final Set<Object> alreadyVisited;
    private final int depth;
    private final int maxLength;

    public ObjectToStringConverter(int depth, int maxLength) {
        this.depth = depth;
        this.maxLength = maxLength;
        alreadyVisited = new HashSet<>();
    }

    StringBuilder append(StringBuilder stringBuilder, Object toAppend) {
        return append(stringBuilder, toAppend, 0);
    }

    StringBuilder append(StringBuilder stringBuilder, Object toAppend, int indent) {
        if (toAppend == null) {
            return stringBuilder.append("<null>");
        }

        try {
            MethodHandle handle = fetchConverterMethodFor(toAppend);
            if (handle != null) {
                handle.invoke(this, stringBuilder, toAppend, indent);
                return stringBuilder;
            }
            Class<?> c = toAppend.getClass();
            if (depth > 0) {
                if (c.isArray()) {
                    appendArray(stringBuilder, toAppend, indent);
                    return stringBuilder;
                }
                if (c.getPackage().getName().startsWith("java")) {
                    return stringBuilder.append('(').append(c.getSimpleName()).append(')')
                            .append(toAppend.toString());
                }
                if (!alreadyVisited.add(toAppend)) {
                    return stringBuilder.append("<link to ").append(c.getName()).append('>');
                }
                stringBuilder.append(c.getName()).append(": ");
                appendFields(stringBuilder, toAppend, c, indent);
                return indent(stringBuilder, indent);
            } else {
                return stringBuilder.append('(').append(c.getSimpleName()).append(')')
                        .append(toAppend.toString());
            }
        } catch (Throwable ex) {
            return stringBuilder.append("<<").append(ex.getMessage()).append(">>");
        }
    }

    private void appendFields(StringBuilder stringBuilder, Object toAppend, Class<?> clazz, int indent) {

        Class<?> currentClass = clazz;
        do {
            for (Field f : currentClass.getDeclaredFields()) {
                appendField(stringBuilder, toAppend, f, indent);
            }
            currentClass = currentClass.getSuperclass();
        } while (currentClass != null);
    }

    private void appendField(StringBuilder stringBuilder, Object toAppend, Field f, int indent) {

        Debugged debugState = f.getAnnotation(Debugged.class);
        int m = f.getModifiers();
        if (debugState == null) {
            if ((m & INVISIBLE_MODIFIERS) != 0) {
                return;
            }
        } else if (!debugState.value()) {
            return;
        }
        try {
            f.setAccessible(true);
            Object x2 = f.get(toAppend);
            if (x2 != null || debugState != null && debugState.printNull()) {
                indent(stringBuilder, indent + 1);
                appendModifier(stringBuilder, m);
                stringBuilder.append(f.getName()).append('=');
                if (f.getType().isPrimitive()) {
                    stringBuilder.append(x2).append(';');
                } else {
                    append(stringBuilder, x2, indent + 1).append(';');
                }
            }
        } catch (IllegalAccessException ex) {
            indent(stringBuilder, indent + 1);
            stringBuilder.append('<').append(f).append(" not accessible>");
        }
    }

    private StringBuilder appendModifier(StringBuilder stringBuilder, int modifierFlags) {
        if ((modifierFlags & Modifier.TRANSIENT) != 0) {
            stringBuilder.append('~');
        }
        if ((modifierFlags & Modifier.VOLATILE) != 0) {
            stringBuilder.append('^');
        }
        if ((modifierFlags & Modifier.FINAL) != 0) {
            stringBuilder.append('!');
        }
        return stringBuilder;
    }

    private MethodHandle fetchConverterMethodFor(Object object) {
        MethodHandle handle = converterMethods.get(object.getClass());
        if (handle != null) return handle;

        for (Map.Entry<Class<?>, MethodHandle> e : converterMethods.entrySet()) {
            if (e.getKey().isInstance(object)) {
                return e.getValue();
            }
        }
        return null;
    }

    private StringBuilder indent(StringBuilder sb, int tabs) {
        sb.append('\n');
        for (int i = tabs; i > 0; i--) {
            sb.append("  ");
        }
        return sb;
    }

    @SuppressWarnings("unused")
    @Appender
    private void appendDate(StringBuilder sb, Date toConvert) {
        sb.append(DateFormat.getDateTimeInstance().format(toConvert));
    }

    @SuppressWarnings("unused")
    @Appender
    private void appendEnum(StringBuilder sb, Enum<?> toAppend) {
        sb.append(toAppend.getDeclaringClass().getSimpleName()).append('.')
                .append(toAppend.toString());
    }

    @SuppressWarnings("unused")
    @Appender
    private void appendCollection(StringBuilder sb, Collection<?> toConvert, int indent) {
        int n = toConvert.size();
        sb.append('<').append(n).append(">[");
        int count = 0;
        boolean needsComma = false;
        for (Object x2 : toConvert) {
            count++;
            if (count > maxLength) {
                sb.append("... (").append(n - maxLength).append(" more)");
                break;
            }
            if (needsComma) {
                sb.append(',');
            } else {
                needsComma = true;
            }
            append(sb, x2, indent + 1);
        }
        sb.append(']');
    }

    @SuppressWarnings("unused")
    @Appender
    private void appendMap(StringBuilder sb, Map toConvert, int indent) {
        int n = toConvert.size();
        sb.append('<').append(n).append(">{");
        boolean needsComma = false;
        int count = 0;
        for (Map.Entry<?, ?> e : ((Map<?, ?>) toConvert).entrySet()) {
            count++;
            if (count > maxLength) {
                sb.append("... (").append(n - maxLength).append(" more)");
                break;
            }
            if (needsComma) {
                sb.append(',');
            } else {
                needsComma = true;
            }
            append(
                    sb.append(e.getKey()).append('='),
                    e.getValue(),
                    indent + 1);
        }
        sb.append('}');
    }

    private void appendArray(StringBuilder sb, Object toConvert, int indent) {

        int n = Array.getLength(toConvert);
        sb.append('<').append(n).append(">[");
        boolean needsComma = false;

        for (int i = 0; i < n; i++) {
            if (i > maxLength) {
                sb.append("... (").append(n - maxLength).append(" more)");
                break;
            }
            if (needsComma) {
                sb.append(',');
            } else {
                needsComma = true;
            }
            if (toConvert instanceof Object[]) {
                append(
                        sb,
                        Array.get(toConvert, i),
                        indent + 1);
            } else {
                sb.append(Array.get(toConvert, i));
            }
        }
        sb.append(']');
    }

    @SuppressWarnings("unused")
    @Appender
    private void appendString(StringBuilder sb, String toConvert) {
        Strings.appendQuotedString(sb, toConvert);
    }

    @SuppressWarnings("unused")
    @Appender
    private void appendClass(StringBuilder sb, Class<?> toConvert, int indent) {

        if (toConvert.isArray()) {
            append(
                    sb,
                    toConvert.getComponentType(),
                    indent).append("[]");
        } else {
            sb.append(toConvert.getName());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface Appender {
    }
}
