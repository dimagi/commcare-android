package org.commcare.fragments.personalId

import android.view.View
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import org.commcare.connect.ConnectConstants
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import kotlin.math.max

abstract class BasePersonalIdFragment : Fragment() {
    fun handleCommonSignupFailures(failureCode: PersonalIdOrConnectApiErrorCodes): Boolean =
        when (failureCode) {
            PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR -> {
                onConfigurationFailure(
                    AnalyticsParamValue.START_CONFIGURATION_LOCKED_ACCOUNT_FAILURE,
                    getString(R.string.personalid_configuration_locked_account),
                )
                true
            }

            else -> {
                false
            }
        }

    protected fun onConfigurationFailure(
        failureCause: String,
        errorMessage: String,
    ) {
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(failureCause)
        navigateToMessageDisplay(
            getString(R.string.personalid_configuration_process_failed_title),
            errorMessage,
            false,
            ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED,
            R.string.ok,
        )
    }

    protected open fun getNavController(): NavController = NavHostFragment.findNavController(this)

    protected abstract fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    )

    protected fun setupKeyboardScrollListener(scrollView: ScrollView) {
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)

        val appBar = requireActivity().findViewById<View?>(R.id.include_tool_bar)
        if (appBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(
                appBar
            ) { v: View?, insets: WindowInsetsCompat? ->
                val topInset = insets!!.getInsets(WindowInsetsCompat.Type.systemBars()).top
                v!!.setPadding(0, topInset, 0, 0)
                insets
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            scrollView
        ) { v: View?, insets: WindowInsetsCompat? ->
            val imeInsets = insets!!.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = imeInsets.bottom
            v!!.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                bottomInset
            )
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                scrollView.post({
                    scrollView.smoothScrollTo(
                        0,
                        scrollView.getChildAt(0).bottom
                    )
                })
            }
            insets
        }
    }

    protected fun destroyKeyboardScrollListener(scrollView: ScrollView) {
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, null)
        val appBar = requireActivity().findViewById<View?>(R.id.include_tool_bar)
        if (appBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBar, null)
            appBar.setPadding(0, 0, 0, 0)
        }
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
    }
}
