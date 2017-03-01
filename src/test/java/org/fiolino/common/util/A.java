package org.fiolino.common.util;

import org.fiolino.data.annotation.SerialFieldIndex;

import java.util.concurrent.TimeUnit;

/**
 * Created by Michael Kuhlmann on 25.01.2016.
 */
public class A {

    @SerialFieldIndex(0)
    private String string;
    @SerialFieldIndex(1)
    private int intValue;
    @SerialFieldIndex(2)
    private Integer integerValue;
    @SerialFieldIndex(3)
    private TimeUnit enumValue;

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }

    public int getIntValue() {
        return intValue;
    }

    public void setIntValue(int intValue) {
        this.intValue = intValue;
    }

    public Integer getIntegerValue() {
        return integerValue;
    }

    public void setIntegerValue(Integer integerValue) {
        this.integerValue = integerValue;
    }

    public TimeUnit getEnumValue() {
        return enumValue;
    }

    public void setEnumValue(TimeUnit enumValue) {
        this.enumValue = enumValue;
    }
}
