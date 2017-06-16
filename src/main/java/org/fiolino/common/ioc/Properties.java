package org.fiolino.common.ioc;

import org.fiolino.common.util.CharSet;
import org.fiolino.common.util.Strings;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ConfigurationBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.*;
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

    static {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Reflections ref = new Reflections(new ConfigurationBuilder().addClassLoader(classLoader).
                    forPackages("org.fiolino").setScanners(new ResourcesScanner()));

        SINGLE_ENTRIES = new HashMap<>();
        ALL_COMBINED = new HashMap<>();
        INDIVIDUAL_TERMS = new HashSet<>();

        List<String> resources = new ArrayList<>(ref.getResources(Pattern.compile("^.*\\.properties$")));
        Collections.sort(resources);

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
        CharSet comments = CharSet.of("#/");
        CharSet delimiter = comments.add('=');
        CharSet valueDelimiter = comments.add(',');

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
                x = Strings.extractUntil(line, x.end + 1, valueDelimiter);
                if (x.stopSign == ',') {
                    // Multiple entries
                    List<String> values = new ArrayList<>();
                    addValue(x, values);
                    do {
                        x = Strings.extractUntil(line, x.end + 1, valueDelimiter);
                        addValue(x, values);
                    } while (x.stopSign == ',');

                    ALL_COMBINED.compute(key, (k, v) -> {
                        if (v == null) {
                            return values.toArray(new String[0]);
                        }
                        int i = v.length;
                        String[] newValue = Arrays.copyOf(v, i + values.size());
                        for (String s : values) {
                            newValue[i++] = s;
                        }
                        return newValue;
                    });
                } else {
                    // Single entry
                    String value = x.extraction;
                    ALL_COMBINED.compute(key, (k, v) -> {
                        if (v == null) return new String[] {value};
                        int i = v.length;
                        String[] newValue = Arrays.copyOf(v, i + 1);
                        newValue[i] = value;
                        return newValue;
                    });
                    SINGLE_ENTRIES.put(key, value);
                }
            } else {
                // Individual text line
                addValue(x, INDIVIDUAL_TERMS);
            }
        }
    }

    private static void addValue(Strings.Extract x, Collection<String> values) {
        if (x.wasQuoted || x.extraction.length() > 0) {
            values.add(x.extraction);
        }
    }

    public static String getSingleEntry(String key) {
        return SINGLE_ENTRIES.get(key);
    }

    public static String[] getMultipleEntries(String key) {
        return ALL_COMBINED.get(key);
    }

    public static boolean isIndividualTerm(String term) {
        return INDIVIDUAL_TERMS.contains(term);
    }
}
