package org.fiolino.common.util;

/**
 * Created by kuli on 29.12.15.
 */
interface SerialPresenter {
  void printInto(StringBuilder sb, Object value) throws Exception;
  void printDescription(StringBuilder sb);
}
