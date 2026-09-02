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

/** Where an install has got to. A null state means nothing is in flight. */
sealed interface InstallState {
    data class Downloading(
        val percent: Int,
    ) : InstallState

    /** Resources are on the device; media still has to be verified before the app can be launched. */
    data object Installed : InstallState

    data object Verifying : InstallState

    data class Failed(
        val message: String,
        val recovery: InstallFailureRecovery,
    ) : InstallState
}

/** The two install failures that need an Activity to recover from, and everything else. */
sealed interface InstallFailureRecovery {
    data object None : InstallFailureRecovery

    data class ApkUpdate(
        val versionRequired: String,
        val versionAvailable: String,
    ) : InstallFailureRecovery

    data object TargetMismatch : InstallFailureRecovery
}

/** The app an install is working towards, and which screen is driving it. */
data class InstallTarget(
    val appId: String,
    val isLearning: Boolean,
    val popSelfOnLaunch: Boolean,
    /** Identifies the screen that started the install, so only it acts on the result. */
    val ownerKey: String,
)

/**
 * Downloads an opportunity's learn or delivery app and reports progress through [installState].
 *
 * Scoped to the hosting activity rather than a single screen, because only one app can download at
 * a time: a second screen must see the install already in flight instead of starting its own and
 * waiting forever on a download that was never begun. Living above the fragments also lets an
 * install outlive rotation and the recycling of a pager page.
 */
class ConnectAppInstallViewModel(
    application: Application,
) : AndroidViewModel(application),
    ResourceEngineListener {
    private val _installState = MutableLiveData<InstallState?>()
    val installState: LiveData<InstallState?> = _installState

    var target: InstallTarget? = null
        private set

    /** Guards the one-shot handling of a failure, which otherwise replays on every recreation. */
    private var failureHandled = false

    val isInstalling get() = _installState.value.let { it is InstallState.Downloading || it is InstallState.Verifying }

    /**
     * Starts installing [target]'s app. Answers false when an install is already running, leaving
     * the state describing that install untouched.
     */
    fun install(
        target: InstallTarget,
        installUrl: String?,
    ): Boolean {
        if (isInstalling) {
            return false
        }
        // Set before starting so a callback arriving immediately has somewhere to land.
        this.target = target
        failureHandled = false
        _installState.value = InstallState.Downloading(0)

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
        _installState.value = InstallState.Verifying
    }

    fun verificationFailed() {
        _installState.value =
            InstallState.Failed(
                getApplication<Application>().getString(R.string.connect_app_install_unknown_error),
                InstallFailureRecovery.None,
            )
    }

    /** Clears the state once the screen has acted on it, so re-observing does not replay it. */
    fun clear() {
        target = null
        failureHandled = false
        _installState.value = null
    }

    override fun updateResourceProgress(
        done: Int,
        pending: Int,
        phase: Int,
    ) {
        val percent = if (pending > 0) done * 100 / pending else 0
        _installState.value = InstallState.Downloading(percent.coerceIn(0, 100))
    }

    override fun reportSuccess(isNewInstall: Boolean) {
        _installState.value = InstallState.Installed
    }

    override fun failMissingResource(
        ure: UnresolvedResourceException,
        statusMissing: AppInstallStatus,
    ) = fail(statusMissing)

    override fun failInvalidResource(
        e: InvalidResourceException,
        statusMissing: AppInstallStatus,
    ) = fail(statusMissing)

    override fun failInvalidReference(
        e: InvalidReferenceException,
        status: AppInstallStatus,
    ) = fail(status)

    override fun failUnknown(statusFailUnknown: AppInstallStatus) = fail(statusFailUnknown)

    override fun failBadReqs(
        vReq: String,
        vAvail: String,
        majorIsProblem: Boolean,
    ) = fail(AppInstallStatus.IncompatibleReqs, InstallFailureRecovery.ApkUpdate(vReq, vAvail))

    override fun failTargetMismatch() = fail(AppInstallStatus.IncorrectTargetPackage, InstallFailureRecovery.TargetMismatch)

    /** An app already on the device needs no download, only the media verification that follows one. */
    override fun failWithNotification(statusFailState: AppInstallStatus) {
        if (statusFailState == AppInstallStatus.DuplicateApp) {
            _installState.value = InstallState.Installed
        } else {
            fail(statusFailState)
        }
    }

    override fun failWithNotification(
        statusFailState: AppInstallStatus,
        buttonAction: NotificationActionButtonInfo.ButtonAction,
    ) = fail(statusFailState)

    private fun fail(
        status: AppInstallStatus,
        recovery: InstallFailureRecovery = InstallFailureRecovery.None,
    ) {
        _installState.value = InstallState.Failed(installErrorMessage(status), recovery)
    }

    /** Not every status carries a localized title — [AppInstallStatus.IncorrectTargetPackage] has no key at all. */
    private fun installErrorMessage(status: AppInstallStatus): String =
        try {
            Localization.get(status.localeKeyBase + ".title")
        } catch (e: LocaleTextException) {
            unknownErrorMessage()
        } catch (e: NoLocalizedTextException) {
            unknownErrorMessage()
        }

    private fun unknownErrorMessage() = getApplication<Application>().getString(R.string.connect_app_install_unknown_error)
}
