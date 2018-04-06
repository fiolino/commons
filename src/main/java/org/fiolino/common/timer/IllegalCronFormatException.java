package org.fiolino.common.timer;

/**
 * This is thrown if a String argument given for parsing a cron expression is invalid.
 */
public final class IllegalCronFormatException extends IllegalArgumentException {
    IllegalCronFormatException(String s) {
        super(s);
    }
}
