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

/**
 * Downloads an opportunity's learn or delivery app and reports progress through [installState].
 *
 * Owning the install here rather than in a fragment is what lets it outlive a rotation: the screen
 * that started it re-observes the state it left behind instead of restarting the download.
 */
class ConnectAppInstallViewModel(
    application: Application,
) : AndroidViewModel(application),
    ResourceEngineListener {
    private val _installState = MutableLiveData<InstallState?>()
    val installState: LiveData<InstallState?> = _installState

    val isInstalling get() = _installState.value.let { it is InstallState.Downloading || it is InstallState.Verifying }

    fun install(installUrl: String?) {
        if (isInstalling) {
            return
        }
        _installState.value = InstallState.Downloading(0)
        ConnectAppUtils.downloadApp(installUrl, this)
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
