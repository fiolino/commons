package org.fiolino.common.processing;

import org.fiolino.common.util.Types;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * Created by kuli on 17.02.15.
 */
public enum Cardinality {
    TO_ONE {
        @Override
        public Cardinality join(Type type) {
            Class<?> rawType = Types.getRawType(type);
            if (Collection.class.isAssignableFrom(rawType)) {
                return TO_MANY;
            }
            if (Map.class.isAssignableFrom(rawType)) {
                return join(Types.getGenericArgument(type, Map.class, 1));
            }
            return TO_ONE;
        }
    },
    TO_MANY {
        @Override
        public Cardinality join(Type type) {
            return TO_MANY;
        }
    };

    public abstract Cardinality join(Type type);
}
