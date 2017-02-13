package org.fiolino.common.analyzing;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by kuli on 26.11.15.
 */
public enum AnalyzedElement {
    FIELD(Field.class),
    METHOD(Method.class);

    private final Class<? extends AccessibleObject> accessibleType;

    AnalyzedElement(Class<? extends AccessibleObject> accessibleType) {
        this.accessibleType = accessibleType;
    }

    public Class<? extends AccessibleObject> getAccessibleType() {
        return accessibleType;
    }
}
