package org.commcare.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.commcare.connect.ConnectAppUtils
import org.commcare.dalvik.R
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.resources.model.InvalidResourceException
import org.commcare.resources.model.UnresolvedResourceException
import org.commcare.tasks.ResourceEngineListener
import org.commcare.views.notifications.NotificationActionButtonInfo
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.services.locale.LocaleTextException
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.util.NoLocalizedTextException

sealed interface AppInstallState {
    /** Nothing is in flight, either because none has started or because the last one was consumed. */
    data object Idle : AppInstallState

    data class Downloading(
        val percent: Int,
        val status: String? = null,
    ) : AppInstallState

    /** Resources are on the device; media still has to be verified before the app can be launched. */
    data object Installed : AppInstallState

    data object Verifying : AppInstallState

    data object Launching : AppInstallState

    data class Failed(
        val message: String,
        val recovery: AppInstallFailureRecovery,
    ) : AppInstallState
}

/** The two install failures that need an Activity to recover from, and everything else. */
sealed interface AppInstallFailureRecovery {
    data object None : AppInstallFailureRecovery

    /**
     * The opportunity's app needs a newer CommCare than this device is running, so the recovery is
     * updating CommCare itself from the Play Store. Both versions are CommCare versions.
     */
    data class CommCareApkUpdate(
        val versionRequired: String,
        val versionAvailable: String,
    ) : AppInstallFailureRecovery

    data object TargetMismatch : AppInstallFailureRecovery
}

/** The app an install is working towards, and which screen is driving it. */
data class AppInstallTarget(
    val appId: String,
    val isLearning: Boolean,
    /** Identifies the screen that started the install, so only it acts on the result. */
    val ownerKey: String,
)

/**
 * Downloads an opportunity's learn or delivery app and reports progress through [installState].
 *
 * Activity-scoped because only one app installs at a time: a second screen has to see the install
 * already running rather than start its own. It also outlives rotation and pager recycling.
 */
class ConnectAppInstallViewModel(
    application: Application,
) : AndroidViewModel(application),
    ResourceEngineListener {
    private val _installState = MutableLiveData<AppInstallState>(AppInstallState.Idle)
    val installState: LiveData<AppInstallState> = _installState

    var target: AppInstallTarget? = null
        private set

    /** Guards the one-shot handling of a failure, which otherwise replays on every recreation. */
    private var failureHandled = false

    val isInstalling
        get() =
            _installState.value.let {
                it is AppInstallState.Downloading ||
                    it is AppInstallState.Verifying ||
                    it is AppInstallState.Launching
            }

    /**
     * Starts installing [target]'s app. Answers false when an install is already running, leaving
     * the state describing that install untouched.
     */
    fun install(
        target: AppInstallTarget,
        installUrl: String?,
    ): Boolean {
        if (isInstalling) {
            return false
        }
        // Set before starting so a callback arriving immediately has somewhere to land.
        this.target = target
        failureHandled = false
        _installState.value = AppInstallState.Downloading(0)

        if (!ConnectAppUtils.downloadApp(installUrl, this)) {
            clear()
            return false
        }
        return true
    }

    /**
     * Answers true the first time it is asked about the current failure, so a screen shows its
     * prompt or toast once rather than every time it re-observes.
     */
    fun consumeFailure(): Boolean {
        if (failureHandled) {
            return false
        }
        failureHandled = true
        return true
    }

    fun markVerifying() {
        _installState.value = AppInstallState.Verifying
    }

    fun markLaunching() {
        _installState.value = AppInstallState.Launching
    }

    /** Clears the state once the screen has acted on it, so re-observing does not replay it. */
    fun clear() {
        target = null
        failureHandled = false
        _installState.value = AppInstallState.Idle
    }

    /**
     * [totalResources] is the resource count to install, not the count still outstanding, and is -1
     * until the installer has counted them — early updates therefore report no progress.
     */
    override fun updateResourceProgress(
        completedResources: Int,
        totalResources: Int,
        phase: Int,
    ) {
        if (totalResources <= 0) {
            _installState.value = AppInstallState.Downloading(0)
            return
        }
        val percent = (completedResources * 100 / totalResources).coerceIn(0, 100)
        // Falls back to an Android string because the CommCare dictionary this reads may itself be
        // the resource currently being installed.
        val status =
            Localization.getWithDefault(
                "profile.found",
                arrayOf(completedResources.toString(), totalResources.toString()),
                getApplication<Application>().getString(R.string.connect_app_install_setting_up),
            )
        _installState.value = AppInstallState.Downloading(percent, status)
    }

    override fun reportSuccess(isNewInstall: Boolean) {
        _installState.value = AppInstallState.Installed
    }

    override fun failMissingResource(
        ure: UnresolvedResourceException,
        statusMissing: AppInstallStatus,
    ) = failInstall(statusMissing)

    override fun failInvalidResource(
        e: InvalidResourceException,
        statusMissing: AppInstallStatus,
    ) = failInstall(statusMissing)

    override fun failInvalidReference(
        e: InvalidReferenceException,
        status: AppInstallStatus,
    ) = failInstall(status)

    override fun failUnknown(statusFailUnknown: AppInstallStatus) = failInstall(statusFailUnknown)

    override fun failBadReqs(
        vReq: String,
        vAvail: String,
        majorIsProblem: Boolean,
    ) = failInstall(AppInstallStatus.IncompatibleReqs, AppInstallFailureRecovery.CommCareApkUpdate(vReq, vAvail))

    override fun failTargetMismatch() = failInstall(AppInstallStatus.IncorrectTargetPackage, AppInstallFailureRecovery.TargetMismatch)

    override fun failWithNotification(statusFailState: AppInstallStatus) {
        // An app already on the device needs no download, only the media verification that follows one.
        if (statusFailState == AppInstallStatus.DuplicateApp) {
            _installState.value = AppInstallState.Installed
        } else {
            failInstall(statusFailState)
        }
    }

    override fun failWithNotification(
        statusFailState: AppInstallStatus,
        buttonAction: NotificationActionButtonInfo.ButtonAction,
    ) = failInstall(statusFailState)

    private fun failInstall(
        status: AppInstallStatus,
        recovery: AppInstallFailureRecovery = AppInstallFailureRecovery.None,
    ) {
        _installState.value = AppInstallState.Failed(installErrorMessage(status), recovery)
    }

    /** Not every status carries a localized title — [AppInstallStatus.IncorrectTargetPackage] has no key at all. */
    private fun installErrorMessage(status: AppInstallStatus): String =
        try {
            Localization.get(status.localeKeyBase + ".title")
        } catch (_: LocaleTextException) {
            unknownErrorMessage()
        } catch (_: NoLocalizedTextException) {
            unknownErrorMessage()
        }

    private fun unknownErrorMessage() = getApplication<Application>().getString(R.string.connect_app_install_unknown_error)
}
