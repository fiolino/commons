package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Created by kuli on 09.03.17.
 */
public class MapRegistryBuilder implements Registry {
    private static final class TwoInts {
        private final int v1;
        private final int v2;

        private TwoInts(int v1, int v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == TwoInts.class
                    && ((TwoInts) obj).v1 == v1
                    && ((TwoInts) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(v1 * 31 + v2);
        }
    }

    private static final class TwoLongs {
        private final long v1;
        private final long v2;

        private TwoLongs(long v1, long v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == TwoLongs.class
                    && ((TwoLongs) obj).v1 == v1
                    && ((TwoLongs) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(v1 * 31 + v2);
        }
    }

    private static final class TwoDoubles {
        private final double v1;
        private final double v2;

        private TwoDoubles(double v1, double v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == TwoDoubles.class
                    && ((TwoDoubles) obj).v1 == v1
                    && ((TwoDoubles) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return Double.hashCode(v1) * 31 + Double.hashCode(v2);
        }
    }

    private static final class IntAndBoolean {
        private final int v1;
        private final boolean v2;

        private IntAndBoolean(int v1, boolean v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        private IntAndBoolean(boolean v2, int v1) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == IntAndBoolean.class
                    && ((IntAndBoolean) obj).v1 == v1
                    && ((IntAndBoolean) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return v2 ? v1 * 17 : v1;
        }
    }

    private static final class LongAndInt {
        private final long v1;
        private final int v2;

        private LongAndInt(long v1, int v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        private LongAndInt(int v2, long v1) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == LongAndInt.class
                    && ((LongAndInt) obj).v1 == v1
                    && ((LongAndInt) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(v1) * 31 + v2;
        }
    }

    private static final class LongAndBoolean {
        private final long v1;
        private final boolean v2;

        private LongAndBoolean(long v1, boolean v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        private LongAndBoolean(boolean v2, long v1) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == LongAndBoolean.class
                    && ((LongAndBoolean) obj).v1 == v1
                    && ((LongAndBoolean) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(v2 ? v1 * 17 : v1);
        }
    }

    private static final class DoubleAndBoolean {
        private final double v1;
        private final boolean v2;

        private DoubleAndBoolean(double v1, boolean v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        private DoubleAndBoolean(boolean v2, double v1) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == DoubleAndBoolean.class
                    && ((DoubleAndBoolean) obj).v1 == v1
                    && ((DoubleAndBoolean) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return v2 ? Double.hashCode(v1) * 17 : Double.hashCode(v1);
        }
    }

    private static final class TwoObjects {
        private final Object v1;
        private final Object v2;

        private TwoObjects(Object v1, Object v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == TwoObjects.class
                    && Objects.equals(v1, ((TwoObjects) obj).v1)
                    && Objects.equals(v2, ((TwoObjects) obj).v2);
        }

        @Override
        public int hashCode() {
            return (v1 == null ? 0 : v1.hashCode() * 31) + (v2 == null ? 0 : v2.hashCode());
        }
    }

    private static final class ObjectAndInt {
        private final Object v1;
        private final int v2;

        private ObjectAndInt(Object v1, int v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        private ObjectAndInt(int v2, Object v1) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == ObjectAndInt.class
                    && Objects.equals(v1, ((ObjectAndInt) obj).v1)
                    && ((ObjectAndInt) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return (v1 == null ? 0 : v1.hashCode() * 31) + v2;
        }
    }

    private static final class ObjectAndLong {
        private final Object v1;
        private final long v2;

        private ObjectAndLong(Object v1, long v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        private ObjectAndLong(long v2, Object v1) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == ObjectAndLong.class
                    && Objects.equals(v1, ((ObjectAndLong) obj).v1)
                    && ((ObjectAndLong) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return (v1 == null ? 0 : v1.hashCode() * 31) + Long.hashCode(v2);
        }
    }

    private static final class ObjectAndDouble {
        private final Object v1;
        private final double v2;

        private ObjectAndDouble(Object v1, double v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        private ObjectAndDouble(double v2, Object v1) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == ObjectAndDouble.class
                    && Objects.equals(v1, ((ObjectAndDouble) obj).v1)
                    && ((ObjectAndDouble) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return (v1 == null ? 0 : v1.hashCode() * 31) + Double.hashCode(v2);
        }
    }

    private static final class ObjectAndBoolean {
        private final Object v1;
        private final boolean v2;

        private ObjectAndBoolean(Object v1, boolean v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        private ObjectAndBoolean(boolean v2, Object v1) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == ObjectAndBoolean.class
                    && Objects.equals(v1, ((ObjectAndBoolean) obj).v1)
                    && ((ObjectAndBoolean) obj).v2 == v2;
        }

        @Override
        public int hashCode() {
            return (v1 == null ? 0 : v1.hashCode() * 31) + (v2 ? 17 : 0);
        }
    }

    private static final class MultipleObjects {
        private final Object[] array;

        private MultipleObjects(Object[] array) {
            this.array = array;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == MultipleObjects.class
                    && Arrays.equals(array, ((MultipleObjects) obj).array);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(array);
        }
    }

    private static MethodHandle modifyKeyAccess(MethodHandle target, int pos, Class<?>... argumentTypes) {
        assert argumentTypes.length > 1;
        MethodHandles.Lookup lookup = lookup();
        MethodHandle constructor = null;
        if (argumentTypes.length == 2) {
            // Try finding some internal class with a suitable constructor
            MethodType constructorType = methodType(void.class, argumentTypes);
            for (Class<?> inner : lookup.lookupClass().getDeclaredClasses()) {
                try {
                    constructor = lookup.findConstructor(inner, constructorType);
                    break;
                } catch (NoSuchMethodException | IllegalAccessException ex) {
                    // continue...
                }
            }
        }

        if (constructor == null) {
            try {
                constructor = lookup.findConstructor(MultipleObjects.class, methodType(void.class, Object[].class));
            } catch (IllegalAccessException | NoSuchMethodException ex) {
                throw new InternalError(ex);
            }
            constructor = constructor.asCollector(Object[].class, argumentTypes.length);
            constructor = constructor.asType(methodType(constructor.type().returnType(), argumentTypes));
        }
        return MethodHandles.collectArguments(target, pos, constructor);
    }

    private final MethodHandle functionFactory;
    private final Map<?, ?> map = new ConcurrentHashMap<>();

    public MapRegistryBuilder(MethodHandle functionFactory) {
        this.functionFactory = functionFactory;
    }

    @Override
    public void clear() {

    }

    @Override
    public MethodHandle getAccessor() {
        return null;
    }

    @Override
    public MethodHandle getUpdater() {
        return null;
    }
}
