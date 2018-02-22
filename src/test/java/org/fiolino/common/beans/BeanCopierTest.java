package org.fiolino.common.beans;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Date;

import static java.lang.invoke.MethodHandles.lookup;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by kuli on 21.02.16.
 */
class BeanCopierTest {
    private static class A {
        private int intField;
        private String stringField;
        private Object objectField;
        private Object onlyIfBigDecimal;

        int getIntField() {
            return intField;
        }

        void setIntField(int intField) {
            this.intField = intField;
        }

        String getStringField() {
            return stringField;
        }

        void setStringField(String stringField) {
            this.stringField = stringField;
        }

        Object getObjectField() {
            return objectField;
        }

        void setObjectField(Object objectField) {
            this.objectField = objectField;
        }

        Object getOnlyIfBigDecimal() {
            return onlyIfBigDecimal;
        }

        void setOnlyIfBigDecimal(Object onlyIfBigDecimal) {
            this.onlyIfBigDecimal = onlyIfBigDecimal;
        }
    }

    private static class B extends A {
        private Date dateField;
        private Long longField;

        Date getDateField() {
            return dateField;
        }

        void setDateField(Date dateField) {
            this.dateField = dateField;
        }

        Long getLongField() {
            return longField;
        }

        void setLongField(Long longField) {
            this.longField = longField;
        }
    }

    private static class X {
        private Object intField;
        private String stringField;
        private Object objectField;
        private BigInteger onlyIfBigDecimal;
        private String anyField;
        private Date dateField;
        private long longField;

        Object getIntField() {
            return intField;
        }

        void setIntField(Object intField) {
            this.intField = intField;
        }

        String getStringField() {
            return stringField;
        }

        void setStringField(String stringField) {
            this.stringField = stringField;
        }

        Object getObjectField() {
            return objectField;
        }

        void setObjectField(Object objectField) {
            this.objectField = objectField;
        }

        BigInteger getOnlyIfBigDecimal() {
            return onlyIfBigDecimal;
        }

        void setOnlyIfBigDecimal(BigInteger onlyIfBigDecimal) {
            this.onlyIfBigDecimal = onlyIfBigDecimal;
        }

        String getAnyField() {
            return anyField;
        }

        void setAnyField(String anyField) {
            this.anyField = anyField;
        }

        Date getDateField() {
            return dateField;
        }

        void setDateField(Date dateField) {
            this.dateField = dateField;
        }

        long getLongField() {
            return longField;
        }

        void setLongField(long longField) {
            this.longField = longField;
        }
    }

    @Test
    void testFactory() {
        BeanCopier<A, X> copier = BeanCopier.copyFromTo(lookup(), A.class, X.class);
        A a = new A();
        Object identity = new Object();
        a.setIntField(123);
        a.setObjectField(identity);
        a.setStringField("Hello World");

        X x = copier.transform(a);
        assertEquals(123, x.getIntField());
        assertTrue(x.getObjectField() == identity);
        assertEquals("Hello World", x.getStringField());
    }

    @Test
    void testCopy() {
        BeanCopier<A, X> copier = BeanCopier.copyFromTo(lookup(), A.class, X.class);
        A a = new A();
        Object identity = new Object();
        a.setIntField(123);
        a.setObjectField(identity);
        a.setStringField("Hello World");

        X x = new X();
        x.setStringField("Something");
        x.setAnyField("Any");
        copier.copyFromTo(a, x);
        assertEquals(123, x.getIntField());
        assertTrue(x.getObjectField() == identity);
        assertEquals("Hello World", x.getStringField());
        assertEquals("Any", x.getAnyField());
    }

    @Test
    void testSubclass() {
        BeanCopier<B, X> copier = BeanCopier.copyFromTo(lookup(), B.class, X.class);
        B b = new B();
        Object identity = new Object();
        Date date = new Date();
        b.setIntField(123);
        b.setObjectField(identity);
        b.setStringField("Hello World");
        b.setDateField(date);
        b.setLongField(999999L);

        X x = new X();
        x.setStringField("Something");
        x.setAnyField("Any");
        copier.copyFromTo(b, x);
        assertEquals(123, x.getIntField());
        assertTrue(x.getObjectField() == identity);
        assertEquals("Hello World", x.getStringField());
        assertEquals("Any", x.getAnyField());
        assertEquals(date, x.getDateField());
        assertEquals(999999L, x.getLongField());
    }

    @Test
    void testDontCopy() {
        BeanCopier<B, X> copier = BeanCopier.copyFromTo(lookup(), B.class, X.class);
        B b = new B();
        BigInteger toTest = new BigInteger("1111");
        b.setOnlyIfBigDecimal(toTest);
        X result = copier.transform(b);
        assertEquals(0, result.getIntField());
        assertEquals(toTest, result.getOnlyIfBigDecimal());
    }

    @Test
    void testCheckNull() {
        BeanCopier<B, X> copier = BeanCopier.copyFromTo(lookup(), B.class, X.class);
        B b = new B();
        b.setLongField(null);
        b.setObjectField(null);
        b.setDateField(null);
        X x = new X();
        x.setLongField(-1000L);
        x.setObjectField(this);

        copier.copyFromTo(b, x);
        assertEquals(-1000L, x.getLongField());
        assertEquals(this, x.getObjectField());
        assertNull(x.getDateField());
    }

    private static class C extends A {
        private Date dateField;
        private Long longField;

        Date getDateField() {
            return dateField;
        }

        void setDateField(Date dateField) {
            this.dateField = dateField;
        }

        Long getLongField() {
            return longField;
        }

        void setLongField(Long longField) {
            this.longField = longField;
        }
    }

    @Test
    void testSuperclassOwnersOnly() {
        BeanCopier<B, C> copier = BeanCopier.copyFromTo(lookup(), B.class, C.class, FieldMatcher.combinationOf(
                FieldMatcher.SAME_NAME, FieldMatcher.SAME_OWNER));

        B b = new B();
        Object identity = new Object();
        Date date = new Date();
        b.setIntField(123);
        b.setObjectField(identity);
        b.setStringField("Hello World");
        b.setDateField(date);
        b.setLongField(999999L);

        C c = new C();
        copier.copyFromTo(b, c);
        assertEquals(123, c.getIntField());
        assertTrue(c.getObjectField() == identity);
        assertEquals("Hello World", c.getStringField());
        assertNull(c.getDateField());
        assertNull(c.getLongField());
    }

    private static class ContainsSuperclass {
        private String copyThis;
        private Number number;

        String getCopyThis() {
            return copyThis;
        }

        void setCopyThis(String copyThis) {
            this.copyThis = copyThis;
        }

        Number getNumber() {
            return number;
        }

        void setNumber(Number number) {
            this.number = number;
        }
    }

    private static class ContainsSubclass {
        private String copyThis;
        private int number = -1;

        String getCopyThis() {
            return copyThis;
        }

        void setCopyThis(String copyThis) {
            this.copyThis = copyThis;
        }

        int getNumber() {
            return number;
        }

        void setNumber(int number) {
            this.number = number;
        }
    }

    @Test
    void testCopySubToSuper() {
        BeanCopier<ContainsSubclass, ContainsSuperclass> copier = BeanCopier.copyFromTo(lookup(), ContainsSubclass.class, ContainsSuperclass.class);
        ContainsSubclass sub = new ContainsSubclass();
        sub.setCopyThis("Hello!");
        sub.setNumber(150);

        ContainsSuperclass sup = copier.transform(sub);
        assertEquals("Hello!", sup.getCopyThis());
        assertEquals(150, sup.getNumber());
    }

    @Test
    void testCopySuperToSub() {
        BeanCopier<ContainsSuperclass, ContainsSubclass> copier = BeanCopier.copyFromTo(lookup(), ContainsSuperclass.class, ContainsSubclass.class);
        ContainsSuperclass sup = new ContainsSuperclass();
        sup.setCopyThis("Hello!");
        sup.setNumber(150);

        ContainsSubclass sub = copier.transform(sup);
        assertEquals("Hello!", sub.getCopyThis());
        assertEquals(150, sub.getNumber());
    }

    @Test
    void testDontCopySuperToSubIfNotMatching() {
        BeanCopier<ContainsSuperclass, ContainsSubclass> copier = BeanCopier.copyFromTo(lookup(), ContainsSuperclass.class, ContainsSubclass.class);
        ContainsSuperclass sup = new ContainsSuperclass();
        sup.setCopyThis("Hello!");
        sup.setNumber(2.3f);

        ContainsSubclass sub = copier.transform(sup);
        assertEquals("Hello!", sub.getCopyThis());
        assertEquals(-1, sub.getNumber());
    }
}
