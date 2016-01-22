package org.commcare.dalvik.activities;

/**
 * Enum representing the different logical/UI states that you can be in while on the login screen
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public enum LoginMode {

    PASSWORD("password-mode"),
    PIN("pin-mode"),
    PRIMED("primed-mode");

    private String stringVersion;

    LoginMode(String s) {
        this.stringVersion = s;
    }

    @Override
    public String toString() {
        return this.stringVersion;
    }

    public static LoginMode fromString(String s) {
        switch(s) {
            case "password-mode":
                return PASSWORD;
            case "pin-mode":
                return PIN;
            case "primed-mode":
                return PRIMED;
            default:
                return null;
        }
    }
}