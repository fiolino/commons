package org.fiolino.common.util;

import org.fiolino.annotations.SerialFieldIndex;
import org.fiolino.annotations.SerializeEmbedded;

/**
 * Created by kuli on 25.07.16.
 */
public class C {

    @SerialFieldIndex(0)
    private String name;
    @SerializeEmbedded(1)
    private A a;
    @SerialFieldIndex(2)
    private String text;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public A getA() {
        return a;
    }

    public void setA(A a) {
        this.a = a;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
