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
     * installing an app targetting LTS on normal Commcare flavour
     */
    IncorrectTargetPackage("notification.install.incorrect.target.package"),

    /**
     * installing an app on LTS that is targetting normal Commcare flavour
     */
    IncorrectTargetPackageLTS("notification.install.incorrect.target.package.lts"),


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
    InvalidResource("notification.install.invalid"),
    IncompatibleReqs("notification.install.badreqs"),
    UnknownFailure("notification.install.unknown"),
    NoLocalStorage("notification.install.nolocal"),
    NoConnection("notification.install.no.connection"),
    BadCertificate("notification.install.badcert"),

    /**
     * A catch-all MessageTag to use for reporting app update failures to the notifications bar
     */
    UpdateFailedGeneral("notification.update.failed.general");

    AppInstallStatus(String root) {
        this.root = root;
    }

    private final String root;

    @Override
    public String getLocaleKeyBase() {
        return root;
    }

    public boolean canReusePartialUpdateTable() {
        return (this == UnknownFailure || this == NoLocalStorage);
    }

    public boolean isUpdateInCompletedState() {
        return (this == UpdateStaged || this == UpToDate);
    }

    @Override
    public String getCategory() {
        return "install_update";
    }
}
