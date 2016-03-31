package org.commcare.engine.resource;

import org.commcare.views.notifications.MessageTag;

public enum AppInstallStatus implements MessageTag {
    // Statuses unique to app installation
    Installed("notification.install.installed"),
    /**
     * Error when attempting to install an app that is already installed
     */
    DuplicateApp("notification.install.duplicate"),

    /**
     * Error caused by attempting to install an app that is not multiple apps-compatible, with
     * other apps already installed on the phone
     */
    MultipleAppsViolation_New("notification.install.multapp.violation.new"),

    /**
     * Error caused by attempting to install an app while there are 1 or more apps already installed
     * that are not multiple-apps compatible
     */
    MultipleAppsViolation_Existing("notification.install.multapp.violation.existing"),

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
