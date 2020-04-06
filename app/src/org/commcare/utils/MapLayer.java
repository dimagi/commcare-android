package org.commcare.utils;

public enum MapLayer {

    NORMAL(1),
    SATELLITE(2),
    TERRAIN(3),
    HYBRID(4);

    private final int value;

    MapLayer(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
