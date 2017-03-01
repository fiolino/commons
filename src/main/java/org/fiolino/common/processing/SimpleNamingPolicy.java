package org.fiolino.common.processing;

import org.fiolino.common.FieldType;
import org.fiolino.common.ioc.Component;

/**
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
@Component(SimpleNamingPolicy.IDENTIFIER)
public class SimpleNamingPolicy implements NamingPolicy {

    public static final String IDENTIFIER = "simple";

    public String[] names(String[] names, Prefix prefix, ValueDescription valueDescription, FieldType fieldType, Filtered filtered, Cardinality cardinality, boolean hidden) {
        return prefix.isInitial() ? names : null;
    }
}
