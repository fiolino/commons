package org.fiolino.common.util;

abstract class AbstractQuoter<Q extends AbstractQuoter> {
    static final int[] escapedToSpecial, specialToEscaped;

    static {
        escapedToSpecial = new int[256];
        specialToEscaped = new int[32];
        for (int i=0; i < 256; i++) {
            escapedToSpecial[i] = i;
        }

        String translations = "\tt\bb\nn\rr\ff";
        for (int i=0; i < translations.length();) {
            char special = translations.charAt(i++);
            char esc = translations.charAt(i++);

            escapedToSpecial[(int) esc] = special;
            specialToEscaped[(int) special] = esc;
        }
    }

    final int quotationStart, quotationEnd, escapeCharacter;
    StringBuilder sb = new StringBuilder();
    private char lowSurrogate;

    AbstractQuoter(int quotationStart, int quotationEnd, int escapeCharacter) {
        this.quotationStart = quotationStart;
        this.quotationEnd = quotationEnd;
        this.escapeCharacter = escapeCharacter;
    }

    abstract void evaluate(int codePoint);

    final void evaluate(char value) {
        if (Character.isHighSurrogate(value)) {
            if (lowSurrogate == (char) 0) {
                throw new IllegalArgumentException("Unexpected high surrogate " + (int)value);
            }
            evaluate(Character.toCodePoint(value, lowSurrogate));
            lowSurrogate = (char) 0;
            return;
        }
        if (lowSurrogate != (char) 0) {
            throw new IllegalArgumentException("Unexpected low surrogate " + (int)lowSurrogate);
        }
        if (Character.isLowSurrogate(value)) {
            lowSurrogate = value;
            return;
        }
        evaluate((int) value);
    }

    /**
     * Appends a code point as part of the input.
     *
     * @param codePoint The code point
     * @return My own instance
     */
    @SuppressWarnings("unchecked")
    public final Q append(int codePoint) {
        evaluate(codePoint);
        return (Q) this;
    }

    /**
     * Appends a single character as part of the input.
     *
     * @param ch The character
     * @return My own instance
     */
    @SuppressWarnings("unchecked")
    public final Q append(char ch) {
        evaluate(ch);
        return (Q) this;
    }

    /**
     * Appends a text snippet.
     *
     * @param text The input text
     * @return My own instance
     */
    @SuppressWarnings("unchecked")
    public final Q append(CharSequence text) {
        // text.codePoints().forEach(this::evaluate); -- Iterating over each character is faster
        for (int i=0, n=text.length(); i < n; i++) {
            evaluate(text.charAt(i));
        }
        return (Q) this;
    }

    /**
     * Resets the input, but keeps the initializionts.
     *
     * @return Myself
     */
    @SuppressWarnings("unchecked")
    public Q reset() {
        sb = new StringBuilder();
        return (Q) this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
