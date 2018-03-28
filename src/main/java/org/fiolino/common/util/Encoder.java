package org.fiolino.common.util;

import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * Encoding and decoding method for indexer to allow arbitrary names as Solr field names.
 *
 * Names are encoded like this:
 *
 * First come all valid characters;
 * if there were invalid ones, then a delimiter is added, and for each invalid character,
 * a hexadecimal value of its position plus a hexadecimal value of the character code follows.
 *
 *
 *
 * Created by kuli on 09.02.16.
 */
public class Encoder {

    private static final CharSet HEXADECIMAL_CHARACTERS = CharSet.of("0123456789abcdef");

    private final IntPredicate validCharacters;
    private final int delimiter;

    /**
     * Instantiates with a given limitation.
     *
     * @param validCharacters These are the allowed characters. They're being extended by all digits since
     *                        they're necessary to encode the others anyway.
     * @param delimiter       This delimits between allowed text and encoded remainders
     */
    public Encoder(IntPredicate validCharacters, int delimiter) {
        IntPredicate withDigits = validCharacters.or(HEXADECIMAL_CHARACTERS);
        this.delimiter = delimiter;
        if (withDigits.test(delimiter)) {
            if (HEXADECIMAL_CHARACTERS.test(delimiter)) {
                throw new IllegalArgumentException("Delimiter " + delimiter + " must not be a character of hexadecimal numbers");
            }
            withDigits = withDigits.and(x -> x != this.delimiter);
        }
        this.validCharacters = withDigits;
    }

    /**
     * Instantiates with a given limitation.
     *
     * @param validCharacters These are the allowed characters. They're being extended by all digits since
     *                        they're necessary to encode the others anyway.
     * @param delimiter       This delimits between allowed text and encoded remainders
     */
    public Encoder(String validCharacters, char delimiter) {
        this(CharSet.of(validCharacters), delimiter);
    }

    private void appendHex(StringBuilder sb, int val) {
        int following;
        do {
            int next7Bits = val & 0x7F;
            following = val >>> 7;
            if (following != 0) {
                next7Bits |= 0x80;
            }
            String hex = Integer.toHexString(next7Bits);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        } while ((val = following) != 0);
    }

    /**
     * Encodes the input string.
     *
     * @param input Any text
     * @return Text suitable for Solr field names
     */
    public String encode(String input) {
        int n = input.length();
        StringBuilder sb = new StringBuilder(n);
        boolean hasEncoding = false;
        int[] encodings = new int[n];
        Arrays.fill(encodings, -1);

        for (int i = 0; i < n; ) {
            int c = input.codePointAt(i);
            if (!validCharacters.test(c)) {
                if (c == ' ' && validCharacters.test('_')) {
                    c = '_';
                } else {
                    hasEncoding = true;
                    encodings[i] = c;
                    i += Character.charCount(c);
                    continue;
                }
            }
            sb.appendCodePoint(c);
            i += Character.charCount(c);
        }

        if (hasEncoding) {
            sb.appendCodePoint(delimiter);
            for (int i = 0; i < n; i++) {
                int x = encodings[i];
                if (x != -1) {
                    appendHex(sb, i);
                    appendHex(sb, x);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Decodes the input string.
     */
    public String decode(String encoded) {
        int n = encoded.length();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; ) {
            int c = encoded.codePointAt(i);
            i += Character.charCount(c);
            if (c == '_' && !validCharacters.test(' ')) {
                c = ' ';
            } else if (c == delimiter) {
                return insertEncodedChars(sb, encoded, i);
            }
            sb.appendCodePoint(c);
        }

        return sb.toString();
    }

    private String insertEncodedChars(StringBuilder sb, String encoded, int start) {
        Position pos = new Position(encoded, start);
        while (pos.hasNext()) {
            int charIndex = pos.nextHex();
            int c = pos.nextHex();
            if (Character.isSupplementaryCodePoint(c)) {
                char[] chars = Character.toChars(c);
                sb.insert(charIndex, chars);
            } else {
                sb.insert(charIndex, (char) c);
            }
        }

        return sb.toString();
    }
}

final class Position {
    private final String text;
    int index;

    Position(String text, int index) {
        this.text = text;
        this.index = index;
    }

    boolean hasNext() {
        return index < text.length();
    }

    int nextHex() {
        int value = 0;
        int shift = 0;
        for (;;) {
            int nextByte = nextByte();
            if ((nextByte & 0x80) == 0) {
                return value | (nextByte << shift);
            }
            value |= (nextByte & 0x7F) << shift;
            shift += 7;
        }
    }

    private int nextByte() {
        if (index >= text.length() - 1) {
            throw new IllegalStateException("Cannot read hex value from #" + index + " of " + text);
        }
        int upper = next4Bits() << 4;
        int lower = next4Bits();
        return upper | lower;
    }

    private static final int DIFF_BETWEEN_LETTERS_AND_DIGITS = 'a' - ('0' + 10);

    private int next4Bits() {
        int c = text.charAt(index++);
        c -= '0';
        if (c >= 10) {
            c -= DIFF_BETWEEN_LETTERS_AND_DIGITS;
        }
        return c;
    }

    @Override
    public String toString() {
        return text + " at #" + index;
    }
}
