package org.commcare.android.tasks;

import org.commcare.android.models.notifications.MessageTag;

public enum ResourceEngineOutcomes implements MessageTag {
    /**
     * App installed Succesfully
     */
    StatusInstalled("notification.install.installed"),

    /**
     * Missing resources could not be found during install
     */
    StatusMissing("notification.install.missing"),

    /**
     * Missing resources could not be found during install
     */
    StatusMissingDetails("notification.install.missing.withmessage"),

    /**
     * App is not compatible with current installation
     */
    StatusBadReqs("notification.install.badreqs"),

    /**
     * Unknown Error
     */
    StatusFailUnknown("notification.install.unknown"),

    /**
     * There's already an app installed
     */
    StatusFailState("notification.install.badstate"),

    /**
     * There's already an app installed
     */
    StatusNoLocalStorage("notification.install.nolocal"),

    /**
     * Install is fine
     */
    StatusUpToDate("notification.install.uptodate"),

    /**
     * Attempting to install an app that is already installed
     */
    StatusDuplicateApp("notification.install.duplicate"),

    /**
     * Certificate was bad
     */
    StatusBadCertificate("notification.install.badcert");


    ResourceEngineOutcomes(String root) {
        this.root = root;
    }

    private final String root;

    public String getLocaleKeyBase() {
        return root;
    }

    public String getCategory() {
        return "install_update";
    }
}
