package org.commcare.utils;

/**
 * Map types defined for GoogleMap object
 * https://developers.google.com/android/reference/com/google/android/gms/maps/GoogleMap#setMapType(int)
 */
public enum MapLayer {

    // Basic map
    NORMAL(1),
    // Satellite imagery without labels
    SATELLITE(2),
    // Topographic data
    TERRAIN(3),
    // Satellite imagery with roads and labels
    HYBRID(4);

    private final int value;

    MapLayer(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
