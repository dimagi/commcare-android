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
     * installing an app targetting a different flavour than the one running
     */
    IncorrectTargetPackage(""),

    // Statuses unique to app updating
    /**
     * Update has been downloaded into update table
     */
    UpdateStaged("notification.install.updatestaged"),

    /**
     * Update attempt resulted in no new updates being found.
     */
    UpToDate("notification.install.uptodate"),

    // Update cancelled by user
    Cancelled("notification.install.cancel"),

    // Error states shared by both app installation and updating:
    MissingResources("notification.install.missing"),
    MissingResourcesWithMessage("notification.install.missing.withmessage"),
    InvalidResource("notification.install.invalid"),
    IncompatibleReqs("notification.install.badreqs"),
    UnknownFailure("notification.install.unknown"),
    NoLocalStorage("notification.install.nolocal"),
    NoConnection("notification.install.no.connection"),
    NetworkFailure("notification.install.network.failure"),
    RateLimited("notification.install.rate.limited"),
    BadCertificate("notification.install.badcert"),


    /**
     * A catch-all MessageTag to use for reporting app update failures to the notifications bar
     */
    UpdateFailedGeneral("notification.update.failed.general"),
    UpdateFailedResourceInit("notification.update.resource.init.fail"),
    CaptivePortal("connection.captive_portal"),

    ReinstallFromInvalidCcz("notification.reinstall.invalid.ccz");

    AppInstallStatus(String root) {
        this.root = root;
    }

    private final String root;

    @Override
    public String getLocaleKeyBase() {
        return root;
    }

    public boolean shouldDiscardPartialUpdateTable() {
        return this == MissingResources ||
                this == MissingResourcesWithMessage ||
                this == InvalidResource ||
                this == IncompatibleReqs;
    }

    public boolean isUpdateInCompletedState() {
        return (this == UpdateStaged || this == UpToDate);
    }

    public boolean shouldRetryUpdate() {
        return (this == NetworkFailure ||
                this == NoConnection);
    }

    @Override
    public String getCategory() {
        return "install_update";
    }

    // whether to include in the counter for update reset
    public boolean causeUpdateReset() {
        return !(this == Cancelled ||
                this == BadCertificate ||
                this == NoConnection ||
                this == RateLimited ||
                this == NetworkFailure);
    }
}
