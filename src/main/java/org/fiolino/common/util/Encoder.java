package org.fiolino.common.util;

/**
 * Encoding and decoding method for indexer to allow arbitrary names as Solr field names.
 * <p>
 * Names are encoded like this:
 * <p>
 * First come all valid characters;
 * if there were invalid ones, then a dollar sign is added, and for each invalid character,
 * a two-char hexadecimal value of its position plus a four-char hexadecimal value of the character follows.
 * <p>
 * Created by kuli on 09.02.16.
 */
public class Encoder {

    public static final Encoder ALL_LETTERS
            = new Encoder("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", '$');
    public static final Encoder ALL_LETTERS_AND_DOT
            = new Encoder("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.", '$');

    private final CharSet validCharacters;
    private final char delimiter;

    /**
     * Instantiates with a given limitation.
     *
     * @param validCharacters These are the allowed characters. They're being extended by all digits since
     *                        they're necessary to encode the others anyway.
     * @param delimiter       This delimits between allowed text and encoded remainders
     */
    public Encoder(CharSet validCharacters, char delimiter) {
        CharSet withDigits = validCharacters.union(CharSet.of("0123456789abcdef"));
        if (withDigits.contains(delimiter)) {
            throw new IllegalArgumentException("Valid characters " + withDigits.allCharactersAsString()
                    + " should not contain delimiter " + delimiter);
        }
        this.validCharacters = withDigits;
        this.delimiter = delimiter;
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

    private void appendHex(StringBuilder sb, int val, int digits) {
        String hex = Integer.toHexString(val);
        int l = hex.length();
        if (l > digits) {
            throw new IllegalArgumentException("Hexadecimal value " + hex + " has more characters than the allowed " + digits);
        }
        while (l++ < digits) {
            sb.append('0');
        }
        sb.append(hex);
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
        int[] encodings = new int[n];
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (!validCharacters.contains(c)) {
                if (c == ' ') {
                    c = '_';
                } else {
                    encodings[i] = c + 1;
                    continue;
                }
            }
            sb.append(c);
        }

        boolean hasEncoding = false;
        for (int i = 0; i < n; i++) {
            int x = encodings[i];
            if (x > 0) {
                if (!hasEncoding) {
                    sb.append(delimiter);
                    hasEncoding = true;
                }
                appendHex(sb, i, 2);
                appendHex(sb, x - 1, 4);
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
        for (int i = 0; i < n; i++) {
            char c = encoded.charAt(i);
            if (c == '_' && !validCharacters.contains(' ')) {
                c = ' ';
            } else if (c == delimiter) {
                return insertEncodedChars(sb, encoded, i);
            }
            sb.append(c);
        }

        return sb.toString();
    }

    private String insertEncodedChars(StringBuilder sb, String encoded, int start) {
        int i = start + 1;
        while (i < encoded.length()) {
            String hex = encoded.substring(i, i + 2);
            int pos = Integer.parseInt(hex, 16);
            hex = encoded.substring(i + 2, i + 6);
            int c = Integer.parseInt(hex, 16);
            sb.insert(pos, (char) c);
            i += 6;
        }

        return sb.toString();
    }
}
