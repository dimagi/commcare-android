package org.commcare.dalvik.activities;

/**
 * Enum representing the different logical/UI states that you can be in while on the login screen
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public enum LoginMode {

    PASSWORD, PIN, PRIMED;

    public static LoginMode fromString(String s) {
        if (PASSWORD.toString().equals(s)) {
            return PASSWORD;
        } else if (PIN.toString().equals(s)) {
            return PIN;
        } else if (PRIMED.toString().equals(s)) {
            return PRIMED;
        } else {
            return null;
        }
    }
}