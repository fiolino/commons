package org.fiolino.common.processing;

import org.fiolino.common.FieldType;

/**
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
public interface NamingPolicy {

    String DEFAULT_NAME = "default";

    /**
     * Gets the name for a specific field.
     *
     * @param names            The raw names of the field.
     * @param prefix           The prefix.
     * @param valueDescription The field's description
     * @param fieldType        The type of the field.
     * @param filtered         whether it's filtered or not.
     * @param cardinality      The cardinality
     * @param hidden           If this fied's value is not used, i.w. it doesn't need to be stored
     * @return The name of the Solr field
     */
    String[] names(String[] names, Prefix prefix, ValueDescription valueDescription,
                   FieldType fieldType, Filtered filtered, Cardinality cardinality, boolean hidden);
}
