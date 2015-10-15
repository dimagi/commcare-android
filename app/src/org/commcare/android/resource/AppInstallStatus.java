package org.commcare.android.resource;

import org.commcare.android.models.notifications.MessageTag;

public enum AppInstallStatus implements MessageTag {
    // Statuses unique to app installation
    Installed("notification.install.installed"),
    /**
     * Error when attempting to install an app that is already installed
     */
    DuplicateApp("notification.install.duplicate"),

    // Statuses unique to app updating
    /**
     * Update has been downloaded into update table
     */
    UpdateStaged("notification.install.updatestaged"),

    /**
     * Update attempt resulted in no new updates being found.
     */
    UpToDate("notification.install.uptodate"),

    // Error states shared by both app installation and updating:
    MissingResources("notification.install.missing"),
    MissingResourcesWithMessage("notification.install.missing.withmessage"),
    IncompatibleReqs("notification.install.badreqs"),
    UnknownFailure("notification.install.unknown"),
    NoLocalStorage("notification.install.nolocal"),
    NoConnection("notification.install.no.connection"),
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

    public boolean isUpdateInCompletedState() {
        return (this == UpdateStaged || this == UpToDate);
    }

    public String getCategory() {
        return "install_update";
    }
}
