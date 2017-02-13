package org.fiolino.common.processing;

import java.util.Arrays;

/**
 * Created by kuli on 15.01.16.
 */
public abstract class Prefix {

  final String concatenator;

  Prefix(String concatenator) {
    this.concatenator = concatenator;
  }

  public abstract String[] createNames(String... fieldNames);
  public abstract boolean isInitial();

  public Prefix newSubPrefix(String... subNames) {
    return new NamedPrefix(createNames(subNames), concatenator);
  }

  private static class NamedPrefix extends Prefix {
    private final String[] heads;

    NamedPrefix(String[] heads, String concatenator) {
      super(concatenator);
      this.heads = heads;
    }

    @Override
    public String[] createNames(String... fieldNames) {
      int n = heads.length * fieldNames.length;
      String[] result = new String[n];
      int i=0;
      for (String h : heads) {
        for (String f : fieldNames) {
          result[i++] = h + concatenator + f;
        }
      }
      return result;
    }

    @Override
    public boolean isInitial() {
      return false;
    }

    @Override
    public String toString() {
      return Arrays.toString(heads);
    }
  }

  private static class EmptyPrefix extends Prefix {

    EmptyPrefix(String concatenator) {
      super(concatenator);
    }

    @Override
    public String[] createNames(String... fieldNames) {
      return fieldNames;
    }

    @Override
    public boolean isInitial() {
      return true;
    }

    @Override
    public String toString() {
      return "Prefix.EMPTY";
    }
  }

  public static Prefix empty(String concatenator) {
    return new EmptyPrefix(concatenator);
  }

  public static final Prefix EMPTY = new EmptyPrefix("-");
}
