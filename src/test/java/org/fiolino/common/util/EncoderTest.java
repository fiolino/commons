package org.fiolino.common.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by kuli on 09.02.16.
 */
public class EncoderTest {

  private static final CharSet VALID = CharSet.of("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._");
  private static final Encoder ENCODER = new Encoder(VALID, '$');

  private void checkValidCharacters(CharSet validChars, String t) {
    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      if (validChars.contains(c)) {
        continue;
      }
      fail("Illegal character " + c + " at #" + i + " in " + t);
    }
  }

  private void validate(String t) {
    String encoded = ENCODER.encode(t);
    checkValidCharacters(VALID.add('$'), encoded);
    String decoded = ENCODER.decode(encoded);
    assertEquals(t, decoded);
  }

  @Test
  public void testNormal() {
    validate("Hello");
    validate("Hello World!");
    validate("Viele Umlaute: äüöÄÜÖáàéè");
    validate("Text-mit-Bindestrichen");
    validate("!\"§$%&/()=123456789");
    validate("");
    validate("\u0000\u9999");
  }

  @Test
  public void testRestricted() {
    Encoder e = new Encoder("o", '!');
    String test = "The quick brown fox jumps over the lazy dog.";
    String encoded = e.encode(test);
    checkValidCharacters(CharSet.of("0123456789abcdefo!_"), encoded);
    assertTrue(encoded.length() > test.length());
    for (int c = (int)'g'; c < (int)'z'; c++) {
      if (c == (int) 'o') continue;
      int index = encoded.indexOf((char) c);
      assertTrue(index == -1);
    }
    assertTrue(encoded.indexOf('o') >= 0);
    assertTrue(encoded.indexOf('!') >= 0);
    String decoded = e.decode(encoded);
    assertEquals(test, decoded);
  }
}
