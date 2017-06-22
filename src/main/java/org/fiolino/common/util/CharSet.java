package org.fiolino.common.util;

import java.io.Serializable;
import java.text.CharacterIterator;
import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntPredicate;

/**
 * A CharSet is like an immutable {@link java.util.Set} of char values.
 * <p>
 * It can be constructed from a String or a char array, and you can use it to quickly check
 * whether a given char value is one of these.
 * <p>
 * CharSets are immutable and therefore thread safe. Methods like intersection() or union() create new
 * instances.
 */
public abstract class CharSet implements Serializable {

    private static final long serialVersionUID = -841531812351L;

    private CharSet() {
    }

    /**
     * Returns true if the charset contains the given char.
     *
     * @param ch The character to test
     * @return true if it is included in this set
     */
    public abstract boolean contains(char ch);

    /**
     * Returns the CharSet as an IntPredicate, handling all contained charactersd as their int counterparts.
     */
    public IntPredicate asPredicate() {
        return c -> contains((char) c);
    }

    /**
     * Returns a CharSet where the given character is not part of.
     *
     * @param ch Some character to remove
     * @return This or a clone of myself where ch is not part of
     */
    public final CharSet remove(char ch) {
        if (!contains(ch)) {
            return this;
        }
        return removeExisting(ch);
    }

    abstract CharSet removeExisting(char ch);

    /**
     * Returns a CharSet where the given character is added.
     *
     * @param ch Some character to add
     * @return This or a clone of myself where ch is part of
     */
    public final CharSet add(char ch) {
        if (contains(ch)) {
            return this;
        }
        return addNonExisting(ch);
    }

    abstract CharSet addNonExisting(char ch);

    /**
     * Checks whether some of my values is inside the given string sequence.
     *
     * @param string Some string
     * @return true is at least one of my characters is inside the string
     */
    public boolean isContainedIn(CharSequence string) {
        for (int i = 0, n = string.length(); i < n; i++) {
            if (contains(string.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the position of the next occurrence of any of my contained characters in the given string.
     *
     * @see String::indexOf
     *
     * @param string Where to look in
     * @return The position, or -1 if none of my characters are inside this string
     */
    public final int nextIndexIn(String string) {
        return nextIndexIn(string, 0);
    }

    /**
     * Gets the position of the next occurrence of any of my contained characters in the given string, starting from the second parameter.
     *
     * @see String::indexOf
     *
     * @param string Where to look in
     * @param start Start looking from here
     * @return The position, or -1 if none of my characters are inside this string after or including start
     */
    public int nextIndexIn(String string, int start) {
        for (int i = start, l = string.length(); i < l; i++) {
            char ch = string.charAt(i);
            if (contains(ch)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns the number of distinct characters in my set.
     */
    public abstract int size();

    /**
     * Returns true if I'm an empty set.
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns an iterator over my contained characters.
     */
    public abstract CharacterIterator iterator();

    /**
     * Returns a CharSet where all of my and the parameter's characters are contained.
     *
     * @param other Some CharSet
     * @return A Charset which size is at least min(my size, other size) and at most my size + other size.
     */
    public abstract CharSet union(CharSet other);

    /**
     * Returns a CharSet where only those characters are contained which are in both me and the other CharSet.
     *
     * @param other Some CharSet
     * @return A Charset which may be empty and whose size is at most min(my size, other size).
     */
    public abstract CharSet intersection(CharSet other);

    /**
     * Returns a String which contains all of my characters.
     */
    public String allCharactersAsString() {
        StringBuilder sb = new StringBuilder(size());
        CharacterIterator it = iterator();
        char next = it.current();
        while (next != CharacterIterator.DONE) {
            sb.append(next);
            next = it.next();
        }
        return Strings.quote(sb.toString());
    }

    @Override
    public String toString() {
        return "CharSet.of(" + allCharactersAsString() + ")";
    }

    CharSet unionFromSingleChar(SingleCharSet other) {
        return union(other);
    }

    CharSet unionFromBitSet(BitSetBasedCharSet other) {
        return union(other);
    }

    CharSet intersectionFromBitSet(BitSetBasedCharSet other) {
        return intersection(other);
    }

    private static final class EmptySet extends CharSet {

        private static final long serialVersionUID = -16813514584L;

        private static final CharacterIterator EMPTY_ITERATOR = new CharacterIterator() {

            @Override
            public char first() {
                return DONE;
            }

            @Override
            public char last() {
                return DONE;
            }

            @Override
            public char current() {
                return DONE;
            }

            @Override
            public char previous() {
                return DONE;
            }

            @Override
            public char setIndex(int position) {
                if (position == 0) {
                    return DONE;
                }
                throw new IllegalArgumentException("Text is empty. #" + position);
            }

            @Override
            public int getBeginIndex() {
                return 0;
            }

            @Override
            public int getEndIndex() {
                return 0;
            }

            @Override
            public int getIndex() {
                return 0;
            }

            @Override
            public Object clone() {
                return EMPTY_ITERATOR;
            }

            @Override
            public char next() {
                return DONE;
            }
        };

        @Override
        public boolean contains(char ch) {
            return false;
        }

        @Override
        public IntPredicate asPredicate() {
            return c -> false;
        }

        @Override
        public boolean isContainedIn(CharSequence string) {
            return false;
        }

        @Override
        public int nextIndexIn(String string, int start) {
            return -1;
        }

        @Override
        CharSet removeExisting(char ch) {
            throw new AssertionError(ch + " in an empty set?");
        }

        @Override
        CharSet addNonExisting(char ch) {
            return new SingleCharSet(ch);
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public CharacterIterator iterator() {
            return EMPTY_ITERATOR;
        }

        @Override
        public CharSet union(CharSet other) {
            return other;
        }

        @Override
        public CharSet intersection(CharSet other) {
            return this;
        }

        @Override
        public String toString() {
            return "CharSet.empty()";
        }

        @Override
        public String allCharactersAsString() {
            return "\"\"";
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }

        @Override
        public int hashCode() {
            return 638707685;
        }
    }

    private static final CharSet EMPTY_SET = new EmptySet();

    private static class SingleCharSet extends CharSet {

        private static final long serialVersionUID = 864531141684L;

        private final char ch;

        private SingleCharSet(char ch) {
            this.ch = ch;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean contains(char ch) {
            return this.ch == ch;
        }

        @Override
        public int nextIndexIn(String string, int start) {
            return string.indexOf(ch, start);
        }

        @Override
        CharSet removeExisting(char ch) {
            return EMPTY_SET;
        }

        @Override
        CharSet addNonExisting(char ch) {
            return this.ch < ch ? new BitSetBasedCharSet(this.ch, ch) : new BitSetBasedCharSet(ch, this.ch);
        }

        private static final class SingleCharIterator implements CharacterIterator {

            private final char ch;
            private boolean valid;

            private SingleCharIterator(char ch) {
                this(ch, true);
            }

            private SingleCharIterator(char ch, boolean valid) {
                this.ch = ch;
                this.valid = valid;
            }

            @Override
            public char first() {
                valid = true;
                return ch;
            }

            @Override
            public char last() {
                valid = true;
                return ch;
            }

            @Override
            public char current() {
                return valid ? ch : DONE;
            }

            @Override
            public char previous() {
                if (valid)
                    return DONE;
                valid = true;
                return ch;
            }

            @Override
            public char setIndex(int position) {
                switch (position) {
                    case 0:
                        return first();
                    case 1:
                        return next();
                    default:
                        throw new IllegalArgumentException("#" + position);
                }
            }

            @Override
            public int getBeginIndex() {
                return 0;
            }

            @Override
            public int getEndIndex() {
                return 1;
            }

            @Override
            public int getIndex() {
                return valid ? 0 : 1;
            }

            @Override
            public Object clone() {
                return new SingleCharIterator(ch, valid);
            }

            @Override
            public char next() {
                valid = false;
                return DONE;
            }
        }

        @Override
        public CharacterIterator iterator() {
            return new SingleCharIterator(ch);
        }

        @Override
        public CharSet union(CharSet other) {
            return other.unionFromSingleChar(this);
        }

        @Override
        CharSet unionFromSingleChar(SingleCharSet other) {
            int thisCH = (int) ch;
            int otherCH = (int) other.ch;

            if (otherCH == thisCH) {
                return this;
            }
            return new BitSetBasedCharSet(Math.min(thisCH, otherCH), Math.max(thisCH, otherCH));
        }

        @Override
        CharSet unionFromBitSet(BitSetBasedCharSet other) {
            return other.add(ch);
        }

        @Override
        public CharSet intersection(CharSet other) {
            if (other.contains(ch)) {
                return this;
            }
            return EMPTY_SET;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(SingleCharSet.class)
                    && ((SingleCharSet) obj).ch == ch;
        }

        @Override
        public int hashCode() {
            return Character.hashCode(ch) + 991785007;
        }
    }

    private static class BitSetBasedCharSet extends CharSet {

        private static final long serialVersionUID = 35641586188872351L;

        private final BitSet bitSet;
        private final int size;

        private BitSetBasedCharSet(int... sortedChars) {
            assert sortedChars.length > 1;
            bitSet = new BitSet(sortedChars[sortedChars.length - 1] + 1);
            for (int ch : sortedChars) {
                if (bitSet.get(ch)) {
                    throw new IllegalArgumentException("Character " + (char) ch + " is set twice!");
                }
                bitSet.set(ch);
            }
            size = sortedChars.length;
        }

        private BitSetBasedCharSet(BitSet bitSet, int size) {
            this.bitSet = bitSet;
            this.size = size;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(char ch) {
            return bitSet.get((int) ch);
        }

        @Override
        CharSet removeExisting(char ch) {
            if (size() == 2) {
                int r = bitSet.nextSetBit(0);
                if (r == ch) {
                    r = bitSet.nextSetBit(r + 1);
                }
                return new SingleCharSet((char) r);
            }

            BitSet remaining = (BitSet) bitSet.clone();
            remaining.clear(ch);
            return new BitSetBasedCharSet(remaining, size() - 1);
        }

        @Override
        CharSet addNonExisting(char ch) {
            BitSet remaining = (BitSet) bitSet.clone();
            remaining.set(ch);
            return new BitSetBasedCharSet(remaining, size() + 1);
        }

        @Override
        public CharacterIterator iterator() {
            return new BitSetBasedIterator(0);
        }

        private class BitSetBasedIterator implements CharacterIterator {

            private int index;
            private int ch;

            BitSetBasedIterator(int index) {
                this(index, bitSet.nextSetBit(index));
            }

            private BitSetBasedIterator(int index, int ch) {
                this.index = index;
                this.ch = ch;
            }

            @Override
            public char first() {
                index = 0;
                ch = bitSet.nextSetBit(0);
                return current();
            }

            @Override
            public char last() {
                index = getEndIndex() - 1;
                ch = bitSet.length() - 1;
                return current();
            }

            @Override
            public char current() {
                return ch == -1 ? DONE : (char) ch;
            }

            @Override
            public char previous() {
                if (index == 0) {
                    return DONE;
                }
                if (ch == -1) {
                    return last();
                }
                index--;
                ch = bitSet.previousSetBit(ch - 1);
                return current();
            }

            @Override
            public char setIndex(int position) {
                if (position == index) {
                    return current();
                }
                if (position < 0 || position > getEndIndex()) {
                    throw new IllegalArgumentException("size: " + size() + ", position: " + position);
                }
                char c;
                if (position < index) {
                    if (position < (index >> 1)) {
                        c = first();
                        while (index < position) {
                            c = next();
                        }
                        return c;
                    }
                    do {
                        c = previous();
                    } while (index > position);
                    return c;
                }
                // Raise
                if (position == getEndIndex()) {
                    index = position;
                    return DONE;
                }
                do {
                    c = next();
                } while (index < position);

                return c;
            }

            @Override
            public int getBeginIndex() {
                return 0;
            }

            @Override
            public int getEndIndex() {
                return size();
            }

            @Override
            public int getIndex() {
                return index;
            }

            @Override
            public Object clone() {
                return new BitSetBasedIterator(index, ch);
            }

            @Override
            public char next() {
                if (++index > size()) {
                    --index;
                    return DONE;
                }
                ch = bitSet.nextSetBit(ch + 1);
                return current();
            }
        }

        @Override
        public CharSet union(CharSet other) {
            return other.unionFromBitSet(this);
        }

        @Override
        CharSet unionFromBitSet(BitSetBasedCharSet other) {
            BitSet copy = (BitSet) bitSet.clone();
            copy.or(other.bitSet);
            int c = copy.cardinality();
            if (c == size()) {
                return this;
            }
            return new BitSetBasedCharSet(copy, c);
        }

        @Override
        public CharSet intersection(CharSet other) {
            return other.intersectionFromBitSet(this);
        }

        @Override
        CharSet intersectionFromBitSet(BitSetBasedCharSet other) {
            BitSet copy = (BitSet) bitSet.clone();
            copy.and(other.bitSet);
            int c = copy.cardinality();
            switch (c) {
                case 0:
                    return EMPTY_SET;
                case 1:
                    return new SingleCharSet((char) copy.nextSetBit(0));
                default:
                    return c == size() ? this : new BitSetBasedCharSet(copy, c);
            }
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this ||
                    obj != null && obj.getClass().equals(BitSetBasedCharSet.class)
                    && ((BitSetBasedCharSet) obj).bitSet.equals(bitSet);
        }

        @Override
        public int hashCode() {
            return bitSet.hashCode() + 171717;
        }
    }

    /**
     * Returns an empty char set.
     */
    public static CharSet empty() {
        return EMPTY_SET;
    }

    /**
     * Returns a char set where all these characters are contained.
     */
    public static CharSet of(char... chars) {
        if (chars.length == 0) {
            return EMPTY_SET;
        }
        if (chars.length == 1) {
            return new SingleCharSet(chars[0]);
        }

        int[] ints = new int[chars.length];
        for (int i = 0; i < chars.length; i++) {
            ints[i] = (int) chars[i];
        }
        Arrays.sort(ints);
        return new BitSetBasedCharSet(ints);
    }

    /**
     * Returns a char set where all the characters of this String are contained.
     */
    public static CharSet of(String stringContainingSetCharacters) {
        return of(stringContainingSetCharacters.toCharArray());
    }
}
