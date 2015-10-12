package org.commcare.android.resource;

import org.commcare.android.models.notifications.MessageTag;

public enum AppInstallStatus implements MessageTag {
    // Success states:
    Installed("notification.install.installed"),
    UpToDate("notification.install.uptodate"),
    /**
     * Update has been downloaded into update table
     */
    UpdateStaged("notification.install.updatestaged"),

    // Error states:
    MissingResources("notification.install.missing"),
    MissingResourcesWithMessage("notification.install.missing.withmessage"),
    IncompatibleReqs("notification.install.badreqs"),
    UnknownFailure("notification.install.unknown"),
    NoLocalStorage("notification.install.nolocal"),
    NoConnection("notification.install.no.connection"),

    /**
     * Attempting to install an app that is already installed
     */
    DuplicateApp("notification.install.duplicate"),

    BadCertificate("notification.install.badcert");


    AppInstallStatus(String root) {
        this.root = root;
    }

    private final String root;

    public String getLocaleKeyBase() {
        return root;
    }

    public boolean canReusePartialUpdateTable() {
        return (this == UnknownFailure || this == NoLocalStorage);
    }

    public String getCategory() {
        return "install_update";
    }
}
