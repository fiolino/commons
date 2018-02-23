package org.fiolino.common.util;

import java.text.CharacterIterator;
import java.util.function.IntPredicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by kuli on 07.11.16.
 */
class CharSetTest {
    @Test
    void contains() {
        CharSet cs = CharSet.empty();
        for (int i = 0; i < 1024; i++) {
            assertFalse(cs.contains((char) i));
        }

        cs = CharSet.of("abc");
        assertTrue(cs.contains('a'));
        assertTrue(cs.contains('b'));
        assertTrue(cs.contains('c'));
        assertFalse(cs.contains('d'));
    }

    @Test
    void remove() {
        CharSet cs = CharSet.of("abc");
        assertTrue(cs.contains('b'));
        assertEquals(3, cs.size());
        cs = cs.remove('b');
        assertEquals(CharSet.of("ac"), cs);
        assertTrue(cs.contains('a'));
        assertFalse(cs.contains('b'));
        assertTrue(cs.contains('c'));
        assertEquals(2, cs.size());

        CharSet equal = cs.remove('Z');
        assertEquals(2, equal.size());
        assertTrue(cs.equals(equal));
    }

    @Test
    void add() {
        CharSet cs = CharSet.of("abc");
        assertFalse(cs.contains('z'));
        assertEquals(3, cs.size());
        cs = cs.add('z');
        assertEquals(CharSet.of("abcz"), cs);
        assertTrue(cs.contains('z'));
        assertEquals(4, cs.size());

        CharSet equal = cs.add('a');
        assertEquals(4, equal.size());
        assertTrue(cs.equals(equal));
    }

    @Test
    void isContainedIn() {
        CharSet cs = CharSet.of("abc");
        assertFalse(cs.isContainedIn("Your mother"));
        assertTrue(cs.isContainedIn("The quick brown fox jumps over the lazy dog."));
    }

    @Test
    void size() {
        assertEquals(0, CharSet.empty().size());
        CharSet cs = CharSet.of("Your life!");
        assertEquals(10, cs.size());
    }

    @Test
    void isEmpty() {
        assertTrue(CharSet.empty().isEmpty());
        assertTrue(CharSet.of("").isEmpty());
        assertFalse(CharSet.of("abc").isEmpty());
        assertFalse(CharSet.empty().add('x').isEmpty());
        assertTrue(CharSet.of("abc").remove('a').remove('b').remove('c').isEmpty());
    }

    @Test
    void iterator() {
        CharSet cs = CharSet.empty();
        CharacterIterator it = cs.iterator();
        assertEquals(CharacterIterator.DONE, it.current());
        assertEquals(CharacterIterator.DONE, it.next());
        assertEquals(CharacterIterator.DONE, it.previous());
        assertEquals(CharacterIterator.DONE, it.first());
        assertEquals(CharacterIterator.DONE, it.last());

        cs = cs.add('x');
        it = cs.iterator();
        assertEquals(0, it.getBeginIndex());
        assertEquals(1, it.getEndIndex());
        assertEquals('x', it.first());
        assertEquals('x', it.last());
        assertEquals('x', it.current());
        assertEquals(CharacterIterator.DONE, it.next());
        assertEquals(CharacterIterator.DONE, it.current());

        cs = CharSet.of("freakshow");
        it = cs.iterator();
        assertEquals(0, it.getIndex());
        assertEquals('a', it.current());
        assertEquals(0, it.getIndex());
        assertEquals('e', it.next());
        assertEquals(1, it.getIndex());
        assertEquals('f', it.next());
        assertEquals(2, it.getIndex());
        assertEquals('h', it.next());
        assertEquals(3, it.getIndex());
        assertEquals('k', it.next());
        assertEquals(4, it.getIndex());
        assertEquals('o', it.next());
        assertEquals(5, it.getIndex());
        assertEquals('r', it.next());
        assertEquals(6, it.getIndex());
        assertEquals('s', it.next());
        assertEquals(7, it.getIndex());
        assertEquals('w', it.next());
        assertEquals(8, it.getIndex());
        assertEquals(CharacterIterator.DONE, it.next());
        assertEquals(9, it.getIndex());
        assertEquals(CharacterIterator.DONE, it.next());
        assertEquals(9, it.getIndex());
        assertEquals('w', it.previous());
        assertEquals(8, it.getIndex());
        assertEquals('s', it.previous());
        assertEquals(7, it.getIndex());

        assertEquals(0, it.getBeginIndex());
        assertEquals(9, it.getEndIndex());

        assertEquals('a', it.first());
        assertEquals(0, it.getIndex());
        assertEquals(CharacterIterator.DONE, it.previous());
        assertEquals(0, it.getIndex());
        assertEquals('w', it.last());
        assertEquals(8, it.getIndex());

        assertEquals('o', it.setIndex(5));
        assertEquals(5, it.getIndex());
        assertEquals('r', it.next());
        assertEquals('h', it.setIndex(3));
        assertEquals('s', it.setIndex(7));
        assertEquals('r', it.setIndex(6));
        assertEquals('r', it.setIndex(6));
        assertEquals('e', it.setIndex(1));
    }

    @Test
    void union() {
        CharSet first = CharSet.of("abcdef");
        CharSet second = CharSet.of("acegij");
        CharSet combined = first.union(second);
        assertEquals(CharSet.of("abcdefgij"), combined);

        CharSet subset = CharSet.of("acde");
        combined = first.union(subset);
        assertEquals(first, combined);

        combined = CharSet.empty().union(first);
        assertEquals(first, combined);
    }

    @Test
    void intersection() {
        CharSet first = CharSet.of("abcdef");
        CharSet second = CharSet.of("acegij");
        CharSet reduced = first.intersection(second);
        assertEquals(CharSet.of("ace"), reduced);

        CharSet different = CharSet.of("xyz");
        reduced = first.intersection(different);
        assertEquals(CharSet.empty(), reduced);

        reduced = CharSet.empty().intersection(first);
        assertEquals(CharSet.empty(), reduced);

        reduced = first.intersection(first);
        assertEquals(first, reduced);

        reduced = first.intersection(CharSet.of('b'));
        assertEquals(CharSet.of('b'), reduced);
        reduced = first.intersection(CharSet.of('.'));
        assertEquals(CharSet.empty(), reduced);
    }

    @Test
    void allCharactersAsString() {
        CharSet cs = CharSet.empty();
        String s = cs.allCharactersAsString();
        assertEquals("\"\"", s);

        cs = CharSet.of('z');
        s = cs.allCharactersAsString();
        assertEquals("\"z\"", s);

        cs = CharSet.of("freakshow \"'");
        s = cs.allCharactersAsString();
        assertEquals("\" \\\"\\'aefhkorsw\"", s);
    }

    @Test
    void nextIndexOf() {
        CharSet cs = CharSet.empty();
        int index = cs.nextIndexIn("Any text.");
        assertEquals(-1, index);

        cs = CharSet.of("0123456789");
        String text = "Pi is a number between 3 and 4, I'd say.";
        index = cs.nextIndexIn(text);
        assertEquals("Pi is a number between ".length(), index);
        index = cs.nextIndexIn(text, index+1);
        assertEquals("Pi is a number between 3 and ".length(), index);
        index = cs.nextIndexIn(text, index+1);
        assertEquals(-1, index);
    }
}
