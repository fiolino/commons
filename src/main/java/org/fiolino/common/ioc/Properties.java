package org.fiolino.common.ioc;

import org.fiolino.common.util.CharSet;
import org.fiolino.common.util.Strings;
import org.fiolino.common.util.SupplierWithException;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ConfigurationBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * This class allows static access to property files which may be used to configure behaviour in own libraries.
 *
 * Users may put their own configuration files into a subfolder "org/fiolino" in their resources classpath.
 *
 *
 * Created by kuli on 14.06.17.
 */
public final class Properties {

    private static final Logger logger = Logger.getLogger(Properties.class.getName());

    private static final Map<String, String> SINGLE_ENTRIES;
    private static final Map<String, String[]> ALL_COMBINED;
    private static final Set<String> INDIVIDUAL_TERMS;
    private static final String[] DEFAULT_MULTI_VALUE = new String[0];
    private static final CharSet VALUE_DELIMITER;

    static {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Reflections ref = new Reflections(new ConfigurationBuilder().addClassLoader(classLoader).
                    forPackages("org.fiolino").setScanners(new ResourcesScanner()));

        SINGLE_ENTRIES = new HashMap<>();
        ALL_COMBINED = new HashMap<>();
        INDIVIDUAL_TERMS = new HashSet<>();
        VALUE_DELIMITER = CharSet.of(",/#");

        List<String> resources = new ArrayList<>(ref.getResources(Pattern.compile("^.*\\.properties$")));
        resources.sort(Comparator.comparing(r -> r.substring(Math.max(0, r.lastIndexOf('/')))));

        for (String r : resources) {
            try (InputStream in = classLoader.getResourceAsStream(r)) {
                load(in, r);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Cannot read " + r);
            }

        }
    }

    private Properties() {
    }

    private static void load(InputStream in, String name) throws IOException {
        CharSet delimiter = CharSet.of("#/=");

        LineNumberReader r = new LineNumberReader(new InputStreamReader(in));
        String line;
        while ((line = r.readLine()) != null) {
            Strings.Extract x = Strings.extractUntil(line, 0, delimiter);
            if (x.stopSign == '=') {
                // Real key-value pair
                String key = x.extraction;
                if (key.isEmpty()) {
                    throw new IllegalStateException(name + " contains empty key in line " + r.getLineNumber());
                }
                String[] values = parseInput(line, x.end + 1, r::readLine);
                if (values.length == 1) {
                    SINGLE_ENTRIES.put(key, values[0]);
                }
                ALL_COMBINED.compute(key, (k, v) -> {
                    if (v == null) {
                        return values;
                    }
                    int i = v.length;
                    String[] newValue = Arrays.copyOf(v, i + values.length);
                    for (String s : values) {
                        newValue[i++] = s;
                    }
                    return newValue;
                });
            } else {
                // Individual text line
                addValue(x, INDIVIDUAL_TERMS);
            }
        }
    }

    private static <E extends Throwable> String[] parseInput(String input, int start, SupplierWithException<String, E> moreLines) throws E {
        Strings.Extract x = Strings.extractUntil(input, start, VALUE_DELIMITER, moreLines);
        if (x.stopSign == ',') {
            // Multiple entries
            List<String> values = new ArrayList<>();
            addValue(x, values);
            do {
                x = Strings.extractUntil(input, x.end + 1, VALUE_DELIMITER, moreLines);
                addValue(x, values);
            } while (x.stopSign == ',');

            return values.toArray(new String[0]);
        } else {
            // Single entry
            return new String[] {
                    x.extraction
            };
        }
    }

    private static void addValue(Strings.Extract x, Collection<String> values) {
        if (x.wasQuoted() || x.extraction.length() > 0) {
            values.add(x.extraction);
        }
    }

    /**
     * Gets a single entry of some property key.
     *
     * Actually it's the value of the last entry entry with only one value.
     * Multiple values (those with a comma separated list) are ignored.
     *
     * @param key The key of the property
     * @return The value, if there is one, or null otherwise
     */
    public static String getSingleEntry(String key) {
        return getSingleEntry(key, null);
    }

    /**
     * Gets a single entry of some property key.
     *
     * Actually it's the value of the last entry entry with only one value.
     * Multiple values (those with a comma separated list) are ignored.
     *
     * @param key The key of the property
     * @param defaultValue Return this if not defined
     * @return The value, if there is one, or null otherwise
     */
    public static String getSingleEntry(String key, String defaultValue) {
        return SINGLE_ENTRIES.getOrDefault(key, defaultValue);
    }

    /**
     * Get all valued of this key.
     *
     * For every entry in one of the property files, all values get added to existing ones,
     * so this is useful for properties there users shall be able to extend functionality.
     *
     * Returns an empty array if no property is defined for that key.
     *
     * @param key The key of the property
     * @return The values, if there are some, or an empty array if not
     */
    public static String[] getMultipleEntries(String key) {
        return getMultipleEntries(key, DEFAULT_MULTI_VALUE);
    }

    /**
     * Get all valued of this key.
     *
     * For every entry in one of the property files, all values get added to existing ones,
     * so this is useful for properties there users shall be able to extend functionality.
     *
     * @param key The key of the property
     * @param defaultValues Return this if not defined
     * @return The values, if there are some, or an empty array if not
     */
    public static String[] getMultipleEntries(String key, String[] defaultValues) {
        return ALL_COMBINED.getOrDefault(key, defaultValues);
    }

    /**
     * Get all valued of this key.
     *
     * For every entry in one of the property files, all values get added to existing ones,
     * so this is useful for properties there users shall be able to extend functionality.
     *
     * @param key The key of the property
     * @param defaultValue This will be parsed and used if there was no such key
     * @return The values, if there are some, or an empty array if not
     */
    public static String[] getMultipleEntries(String key, String defaultValue) {
        String[] result = ALL_COMBINED.get(key);
        if (result != null) return result;
        return parseInput(defaultValue, 0, () -> null);
    }

    /**
     * Checks whether the given term was mentioned somewhere in one of the property files as a single line,
     * without any equality sign.
     */
    public static boolean isIndividualTerm(String term) {
        return INDIVIDUAL_TERMS.contains(term);
    }
}
