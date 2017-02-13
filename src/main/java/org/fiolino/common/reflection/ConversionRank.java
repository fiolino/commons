package org.fiolino.common.reflection;

/**
 * Created by Kuli on 6/17/2016.
 */
public enum ConversionRank {
  IMPOSSIBLE {
    @Override
    ConversionRank compareWithSource(ConversionRank source) {
      return IMPOSSIBLE;
    }
  },
  IN_HIERARCHY {
    @Override
    ConversionRank compareWithSource(ConversionRank source) {
      return IMPOSSIBLE; // Only source may be IN_HIERARCHY, not the target
    }
  },
  EXPLICITLY_CASTABLE,
  WRAPPABLE,
  IDENTICAL {
    @Override
    ConversionRank compareWithSource(ConversionRank source) {
      return source;
    }
  };

  ConversionRank compareWithSource(ConversionRank source) {
    return source == this ? this : IMPOSSIBLE;
  }
}
