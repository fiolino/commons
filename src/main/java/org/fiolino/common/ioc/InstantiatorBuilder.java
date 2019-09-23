package org.fiolino.common.ioc;

import org.fiolino.annotations.Provider;
import org.fiolino.common.util.Types;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.text.DateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.Temporal;
import java.util.*;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

public class InstantiatorBuilder {
    private FactoryFinder current;

    private InstantiatorBuilder(FactoryFinder start) {
        current = start;
    }

    public static InstantiatorBuilder withDefaults() {
        return new InstantiatorBuilder(Defaults.DEFAULT);
    }

    public static InstantiatorBuilder withDefaults(MethodHandles.Lookup lookup) {
        return new InstantiatorBuilder(Defaults.DEFAULT.withLookup(lookup));
    }

    public static InstantiatorBuilder in(MethodHandles.Lookup lookup) {
        return new InstantiatorBuilder(FactoryFinder.forLookup(lookup));
    }

    public FactoryFinder build() {
        return current;
    }

    public InstantiatorBuilder withHandleProvider(MethodHandleProvider p) {
        current = current.addMethodHandleProvider(p);
        return this;
    }

    public InstantiatorBuilder withProvider(Object p) {
        current = current.addProviders(p);
        return this;
    }

    public InstantiatorBuilder formatting(Class<? extends Temporal> type, DateTimeFormatter formatter) {
        MethodHandle parse, format;
        try {
            parse = publicLookup().findStatic(type, "parse", methodType(type, CharSequence.class, DateTimeFormatter.class));
            format = publicLookup().findVirtual(type, "format", methodType(String.class, DateTimeFormatter.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalArgumentException(type.getName() + " cannot use the formatter", ex);
        }

        parse = insertArguments(parse, 1, formatter);
        format = insertArguments(format, 1, formatter);
        current = current.addProviders(parse, format);
        return this;
    }

    public InstantiatorBuilder formatting(Class<? extends Temporal> type, String dateTimeFormat) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateTimeFormat);
        return formatting(type, formatter);
    }

    public InstantiatorBuilder formatting(Class<? extends Temporal> type, String dateTimeFormat, Locale locale) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateTimeFormat, locale);
        return formatting(type, formatter);
    }

    public InstantiatorBuilder formatting(DateFormat dateFormat) {
        MethodHandle parse, format;
        try {
            parse = publicLookup().findVirtual(DateFormat.class, "parse", methodType(Date.class, String.class));
            format = publicLookup().findVirtual(DateFormat.class, "format", methodType(String.class, Date.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }

        current = current.addHandleWithInitializers(parse, dateFormat).addHandleWithInitializers(format, dateFormat);
        return this;
    }

    public InstantiatorBuilder formatting(FormatStyle style, Locale locale) {
        formatting(LocalDate.class, DateTimeFormatter.ofLocalizedDate(style).localizedBy(locale));
        formatting(LocalTime.class, DateTimeFormatter.ofLocalizedTime(style).localizedBy(locale));
        formatting(LocalDateTime.class, DateTimeFormatter.ofLocalizedDateTime(style).localizedBy(locale));

        int oldStyle = FormatStyleConverter.oldDateFormatStyleFor(style);
        DateFormat df = DateFormat.getDateTimeInstance(oldStyle, oldStyle, locale);
        return formatting(df);
    }

    public InstantiatorBuilder formatting(FormatStyle style) {
        return formatting(style, Locale.getDefault(Locale.Category.FORMAT));
    }

    private static class FormatStyleConverter {
        private static final Map<FormatStyle, Integer> styleMapping = new EnumMap<>(FormatStyle.class);

        static {
            Lookup l = publicLookup().in(DateFormat.class);
            try {
                for (FormatStyle fs : FormatStyle.values()) {
                    VarHandle constant = l.findStaticVarHandle(DateFormat.class, fs.name(), int.class);
                    int value = (int) constant.get();
                    styleMapping.put(fs, value);
                }
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }

        static int oldDateFormatStyleFor(FormatStyle fs) {
            return styleMapping.get(fs);
        }
    }

    static class Defaults {
        static final FactoryFinder DEFAULT;

        static {
            MethodHandle getTime, numberToString, dateToInstant, instantToDate;
            try {
                getTime = publicLookup().findVirtual(Date.class, "getTime", methodType(long.class));
                numberToString = publicLookup().findVirtual(Number.class, "toString", methodType(String.class));
                dateToInstant = publicLookup().findVirtual(Date.class, "toInstant", methodType(Instant.class));
                instantToDate = publicLookup().findStatic(Date.class, "from", methodType(Date.class, Instant.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new AssertionError("getTime", ex);
            }

            DEFAULT = FactoryFinder.withProviders(lookup().dropLookupMode(MethodHandles.Lookup.MODULE),
                    (MethodHandleProvider)(i, t) -> i.getLookup().findStatic(t.returnType(), "valueOf", t),

                    (MethodHandleProvider)(i, t) -> {
                        if (t.parameterCount() == 1) {
                            Class<?> returnType = t.returnType();
                            if (returnType.isPrimitive()) {
                                Class<?> argumentType = t.parameterType(0);
                                String name = returnType.getName();
                                if (String.class.equals(argumentType)) {
                                    // Finds something like Integer::parseInt
                                    return i.getLookup().findStatic(Types.toWrapper(returnType), "parse" + Character.toUpperCase(name.charAt(0)) + name.substring(1), t);
                                } else if (Number.class.isAssignableFrom(argumentType)) {
                                    // Finds something like Integer::longValue
                                    return i.getLookup().findVirtual(argumentType, name + "Value", methodType(returnType));
                                }
                            }
                        }
                        return null;
                    },

                    (MethodHandleProvider)(i, t) -> {
                        if (String.class.equals(t.returnType()) && t.parameterCount() == 1) {
                            Class<?> argumentType = t.parameterType(0);
                            if (argumentType.isEnum()) {
                                return i.getLookup().findVirtual(argumentType, "name", methodType(String.class));
                            }
                        }
                        return null;
                    },

                    getTime,
                    numberToString,
                    dateToInstant,
                    instantToDate,

                    new Object() {
                        @Provider
                        @SuppressWarnings("unused")
                        char charFromString(String value) {
                            if (value.isEmpty()) return (char) 0;
                            return value.charAt(0);
                        }

                        @Provider
                        @SuppressWarnings("unused")
                        char charFromBool(boolean value) {
                            return value ? 't' : 'f';
                        }

                        @Provider @SuppressWarnings("unused")
                        java.sql.Date sqlTypeFromDate(Date origin) {
                            return new java.sql.Date(origin.getTime());
                        }

                        @Provider @SuppressWarnings("unused")
                        java.sql.Time sqlTimeFromDate(Date origin) {
                            return new java.sql.Time(origin.getTime());
                        }

                        @Provider @SuppressWarnings("unused")
                        java.sql.Timestamp sqlTimestampFromDate(Date origin) {
                            return new java.sql.Timestamp(origin.getTime());
                        }

                        @Provider @SuppressWarnings("unused")
                        LocalDateTime localDateFor(Date date) {
                            return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
                        }

                        @Provider @SuppressWarnings("unused")
                        Date dateFrom(LocalDateTime dateTime) {
                            return Date.from(dateTime.toInstant(ZoneOffset.UTC));
                        }
                    });
        }


    }
}
