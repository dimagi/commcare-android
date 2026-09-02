package org.commcare.views.connect

import org.commcare.connect.viewmodel.InstallState
import org.commcare.dalvik.R

/**
 * Renders an app install in the bottom action bar the user started it from: a progress ring while
 * it runs, and a dismissible message with the CTA restored when it fails.
 */
fun ConnectCtaBar.renderInstallState(
    state: InstallState?,
    isLearning: Boolean,
) {
    val subtitle =
        context.getString(
            if (isLearning) R.string.connect_downloading_learn else R.string.connect_downloading_delivery,
        )
    when (state) {
        is InstallState.Downloading -> showInstallProgress(state.percent, subtitle)
        // Verification and seating carry on after the download, so the bar stays busy through them.
        InstallState.Installed, InstallState.Verifying -> showInstallProgress(FULL_PROGRESS, subtitle)
        is InstallState.Failed -> showInstallFailure(state.message)
        null -> {
            clearInstallProgress()
            hideInstallFailure()
        }
    }
}

private const val FULL_PROGRESS = 100
