package org.fiolino.common.util;

import org.fiolino.data.annotation.SerialFieldIndex;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Michael Kuhlmann on 25.01.2016.
 */
public class B extends A {
    private long longValue;
    private Double doubleValue;
    private List<Integer> intList;
    private List<TimeUnit> enumList;

    public long getLongValue() {
        return longValue;
    }

    @SerialFieldIndex(4)
    public void setLongValue(long longValue) {
        this.longValue = longValue;
    }

    public Double getDoubleValue() {
        return doubleValue;
    }

    @SerialFieldIndex(5)
    public void setDoubleValue(Double doubleValue) {
        this.doubleValue = doubleValue;
    }

    public List<Integer> getIntList() {
        return intList;
    }

    @SerialFieldIndex(6)
    public void setIntList(List<Integer> intList) {
        this.intList = intList;
    }

    public List<TimeUnit> getEnumList() {
        return enumList;
    }

    @SerialFieldIndex(7)
    public void setEnumList(List<TimeUnit> enumList) {
        this.enumList = enumList;
    }
}
