package org.fiolino.common.util;

import java.util.function.IntPredicate;

public final class Quoter extends AbstractQuoter<Quoter> {

    /**
     * This is a utility method to directly quote some text with the default settings.
     *
     * @param input The text to quote
     * @return The quoted text
     */
    public static String quote(CharSequence input) {
        Quoter q = createDefault();
        q.append(input);
        return q.toString();
    }

    /**
     * This is a utility method to directly quote some text with a given quoting character and the remaining default settings.
     *
     * @param quotingCharacter The character used as the quoting start and end
     * @param input The text to quote
     * @return The quoted text
     */
    public static String quote(int quotingCharacter, CharSequence input) {
        Quoter q = quoteWith(quotingCharacter, Character::isLetterOrDigit);
        q.append(input);
        return q.toString();
    }

    private final IntPredicate noQuotationNeeded;
    private final IntPredicate escapeToUnicode;
    private boolean quotationStarted;

    private Quoter(int quotationStart, int quotationEnd, int escapeCharacter, IntPredicate noQuotationNeeded, IntPredicate escapeToUnicode) {
        super(quotationStart, quotationEnd, escapeCharacter);
        this.noQuotationNeeded = noQuotationNeeded;
        this.escapeToUnicode = escapeToUnicode;
    }

    /**
     * Creates a new quoter with all options defined.
     *
     * @param quotationStart The character used to start quotation
     * @param quotationEnd The character used to end quotation
     * @param escapeCharacter The character used to escape special characters
     * @param noQuotationNeeded If this condition is met, or if the character is the escape character or the quotation end,
     *                       or it is a special character that gets escaped, then the text will be quoted.
     * @param escapeToUnicode If this is true, then the codePoint will be encoded to a hexadecimal unicode escape sequence
     * @return A new quoter
     */
    public static Quoter quoteWith(int quotationStart, int quotationEnd, int escapeCharacter, IntPredicate noQuotationNeeded, IntPredicate escapeToUnicode) {
        return new Quoter(quotationStart, quotationEnd, escapeCharacter, noQuotationNeeded, escapeToUnicode);
    }

    /**
     * Creates a new quoter with all options defined except the escape character, which will be the backslash.
     *
     * @param quotationStart The character used to start quotation
     * @param quotationEnd The character used to end quotation
     * @param noQuotationNeeded If this condition is met, or if the character is the escape character or the quotation end,
     *                       or it is a special character that gets escaped, then the text will be quoted.
     * @return A new quoter
     */
    public static Quoter quoteWith(int quotationStart, int quotationEnd, IntPredicate noQuotationNeeded, IntPredicate escapeToUnicode) {
        return quoteWith(quotationStart, quotationEnd, '\\', noQuotationNeeded, escapeToUnicode);
    }

    /**
     * Creates a new quoter with all options defined except the escape character, which will be the backslash.
     * Escape all codePoints below 32 to unicode escape sequences.
     *
     * @param quotationStart The character used to start quotation
     * @param quotationEnd The character used to end quotation
     * @param noQuotationNeeded If this condition is met, or if the character is the escape character or the quotation end,
     *                       or it is a special character that gets escaped, then the text will be quoted.
     * @return A new quoter
     */
    public static Quoter quoteWith(int quotationStart, int quotationEnd, IntPredicate noQuotationNeeded) {
        return quoteWith(quotationStart, quotationEnd, noQuotationNeeded, x -> x < 32);
    }

    /**
     * Creates a new quoter with all options defined except the escape character, which will be the backslash.
     * Start and end of quote will be the same.
     *
     * @param quotationChar The character used to encapsulate the text
     * @param noQuotationNeeded If this condition is met, or if the character is the escape character or the quotation end,
     *                       or it is a special character that gets escaped, then the text will be quoted.
     * @return A new quoter
     */
    public static Quoter quoteWith(int quotationChar, IntPredicate noQuotationNeeded) {
        return quoteWith(quotationChar, quotationChar, noQuotationNeeded);
    }

    /**
     * Creates a new quoter with the backslash as the escape character and the double quote as the quotation char.
     *
     * @param noQuotationNeeded If this condition is met, or if the character is the escape character or the quotation end,
     *                       or it is a special character that gets escaped, then the text will be quoted.
     * @return A new quoter
     */
    public static Quoter quoteWhen(IntPredicate noQuotationNeeded) {
        return quoteWith('"', noQuotationNeeded);
    }

    /**
     * Creates a new quoter with the backslash as the escape character, the double quote as the quotation char, and all
     * characters that are no letters or digits will be eligible for quotation.
     *
     * @return A new quoter
     */
    public static Quoter createDefault() {
        return quoteWhen(Character::isLetterOrDigit);
    }

    @Override
    void evaluate(int codePoint) {
        if (!quotationStarted && !noQuotationNeeded.test(codePoint)) {
            startQuoting();
        }
        if (codePoint == escapeCharacter || codePoint == quotationEnd) {
            startQuoting();
            sb.appendCodePoint(escapeCharacter);
        }
        if (codePoint < specialToEscaped.length) {
            int escaped = specialToEscaped[codePoint];
            if (escaped != 0) {
                startQuoting();
                sb.appendCodePoint(escapeCharacter).appendCodePoint(escaped);
                return;
            }
        }
        if (escapeToUnicode.test(codePoint)) {
            startQuoting();
            sb.appendCodePoint(escapeCharacter).append('u');
            String hex = Integer.toHexString(codePoint);
            for (int i=4; i > hex.length(); i--) {
                sb.append('0');
            }
            sb.append(hex);
            return;
        }
        sb.appendCodePoint(codePoint);
    }

    private void startQuoting() {
        if (quotationStarted) return;
        quotationStarted = true;
        if (Character.isSupplementaryCodePoint(quotationStart)) {
            char[] cp = new char[] {
                    Character.lowSurrogate(quotationStart), Character.highSurrogate(quotationStart)
            };
            sb.insert(0, cp);
        } else {
            sb.insert(0, (char) quotationStart);
        }
    }

    @Override
    public Quoter reset() {
        quotationStarted = false;
        return super.reset();
    }

    @Override
    public String toString() {
        if (!quotationStarted) {
            return super.toString();
        }
        StringBuilder sb = new StringBuilder(this.sb).appendCodePoint(quotationEnd);
        return sb.toString();
    }
}
