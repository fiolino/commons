package org.fiolino.common.beans;

import java.lang.reflect.Field;

/**
 * Created by kuli on 20.02.16.
 */
public interface FieldMatcher {

    int rank(Field reader, Field writer);

    FieldMatcher SAME_NAME = (r, w) -> r.getName().equals(w.getName()) ? 100 : -1;

    FieldMatcher SAME_OWNER = (r, w) -> r.getDeclaringClass().equals(w.getDeclaringClass()) ? 10 : -1;

    static FieldMatcher combinationOf(FieldMatcher... matchers) {
        if (matchers.length == 0) {
            throw new IllegalArgumentException("Expected at least one matcher.");
        }
        if (matchers.length == 1) {
            return matchers[0];
        }
        return (r, w) -> {
            int rank = 0;
            for (FieldMatcher m : matchers) {
                int inner = m.rank(r, w);
                if (inner < 0) {
                    return inner;
                }
                rank += inner;
            }
            return rank;
        };
    }
}
