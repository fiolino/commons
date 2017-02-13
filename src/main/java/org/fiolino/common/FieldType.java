package org.fiolino.common;

import org.fiolino.common.processing.Cardinality;
import org.fiolino.common.util.Types;
import org.fiolino.data.base.Text;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
public enum FieldType {

  BOOLEAN(Boolean.class, "b", "bi", "bh", "bx", "bix", "bhx"),
  INT(Integer.class, "i", "ii", "ih", "ix", "iix", "ihx"),
  LONG(Long.class, "l", "li", "lh", "lx", "lix", "lhx"),
  FLOAT(Float.class, "f", "fi", "fh", "fx", "fix", "fhx"),
  DOUBLE(Double.class, "d", "di", "dh", "dx", "dix", "dhx"),
  ENUM(Enum.class, "s", "si", "sh", "sx", "six", "shx"),
  STRING(String.class, "s", "si", "sh", "sx", "six", "shx"),
  TEXT(Text.class, "t", "t", "th", "tx", "tx", "thx") {

    @Override
    protected String getHiddenUnindexedSuffix(Cardinality cardinality) {
      return cardinality == Cardinality.TO_MANY ? "thx" : "th";
    }
  },
  DATE(Date.class, "dt", "dti", "dth", "dtx", "dtix", "dthx");

  FieldType(Class<?> type,
            String singleValueUnindexedSuffix, String singleValueFilterableSuffix,
            String singleValueHiddenSuffix,
            String multiValueUnindexedSuffix, String multiValueFilterableSuffix,
            String multiValueHiddenSuffix) {

    this.type = type;
    this.singleValueUnindexedSuffix = singleValueUnindexedSuffix;
    this.singleValueFilterableSuffix = singleValueFilterableSuffix;
    this.singleValueHiddenSuffix = singleValueHiddenSuffix;
    this.multiValueUnindexedSuffix = multiValueUnindexedSuffix;
    this.multiValueFilterableSuffix = multiValueFilterableSuffix;
    this.multiValueHiddenSuffix = multiValueHiddenSuffix;
  }

  private static final Map<Class<?>, FieldType> mapping;

  private final Class<?> type;

  private final String singleValueUnindexedSuffix;

  private final String singleValueFilterableSuffix;

  private final String singleValueHiddenSuffix;

  private final String multiValueUnindexedSuffix;

  private final String multiValueFilterableSuffix;

  private final String multiValueHiddenSuffix;

  public Class<?> getType() {
    return type;
  }

  public String getSuffix(Cardinality cardinality, boolean indexed, boolean hidden) {
    switch (cardinality){
      case TO_ONE:
        return indexed ? (hidden ? singleValueHiddenSuffix : singleValueFilterableSuffix)
                : (hidden ? getHiddenUnindexedSuffix(cardinality) : singleValueUnindexedSuffix);
      case TO_MANY:
        return indexed ? (hidden ? multiValueHiddenSuffix : multiValueFilterableSuffix)
                : (hidden ? getHiddenUnindexedSuffix(cardinality) : multiValueUnindexedSuffix);
      default:
        throw new AssertionError("Undefined cardinality " + cardinality);
    }
  }

  protected String getHiddenUnindexedSuffix(Cardinality cardinality) {
    return null;
  }

  static {
    mapping = new HashMap<>();
    for (FieldType ft : values()) {
      Class<?> key = ft.getType();
      mapping.put(key, ft);
      Class<?> primitive = Types.asPrimitive(key);
      if (primitive != null) {
        mapping.put(primitive, ft);
      }
    }
    // Make sure that STRING is preferred
    mapping.put(String.class, STRING);
  }

  public static FieldType getDefaultFor(Class<?> type) {
    FieldType fieldType = mapping.get(type);
    if (fieldType == null && type.isEnum()) {
      return ENUM;
    }
    return fieldType;
  }

  @Override
  public String toString() {
    return name() + "(" + singleValueUnindexedSuffix + ")";
  }
}
