package org.commcare.commcaresupportlibrary.identity;

public enum BiometricIdentifier {
    RIGHT_THUMB("right_thumb_template"),
    RIGHT_INDEX_FINGER("right_index_finger_template"),
    RIGHT_MIDDLE_FINGER("right_middle_finger_template"),
    RIGHT_RING_FINGER("right_ring_finger_template"),
    RIGHT_PINKY_FINGER("right_pinky_finger_template"),
    LEFT_THUMB("left_thumb_template"),
    LEFT_INDEX_FINGER("left_index_finger_template"),
    LEFT_MIDDLE_FINGER("left_middle_finger_template"),
    LEFT_RING_FINGER("left_ring_finger_template"),
    LEFT_PINKY_FINGER("left_pinky_finger_template"),
    FACE("face_template");

    private final String calloutResponseKey;

    BiometricIdentifier(String calloutResponseKey) {
        this.calloutResponseKey = calloutResponseKey;
    }

    public String getCalloutResponseKey() {
        return calloutResponseKey;
    }
}
