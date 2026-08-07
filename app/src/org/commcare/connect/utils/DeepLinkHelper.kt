package org.commcare.connect.utils

import android.content.Context
import android.content.Intent
import org.commcare.activities.connect.ConnectActivity
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdManager
import org.commcare.dalvik.BuildConfig
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

object DeepLinkHelper {
    fun retrieveConnectOppInviteIntentIfPresent(
        context: Context,
        intent: Intent,
    ): Intent? {
        val data = intent.data
        if (Intent.ACTION_VIEW != intent.action || data == null) {
            return null
        }

        // Require https://<connect_server>
        if ("https" != data.scheme || BuildConfig.CCC_HOST != data.host) {
            return null
        }

        // Require /users/invite_redirect/<opp_uuid>
        val segments = data.pathSegments
        if (segments.size != 3 ||
            ("users" != segments[0]) ||
            ("invite_redirect" != segments[1])
        ) {
            return null
        }
        val uuid = segments[2]

        // Clear the URI immediately so future dispatch() doesn't reprocess this link
        intent.data = null

        val personalIdManager = PersonalIdManager.getInstance()
        personalIdManager.init(context)
        if (!personalIdManager.isloggedIn()) {
            FirebaseAnalyticsUtil.reportExternalAppLaunchEvent(
                AnalyticsParamValue.OPP_INVITE_LINK,
                false,
                AnalyticsParamValue.OPP_INVITE_LINK_PERSONAL_ID_NOT_CONFIGURED,
            )
            return null
        }

        val connectIntent = Intent(context, ConnectActivity::class.java)
        connectIntent.putExtra(
            ConnectConstants.REDIRECT_ACTION,
            ConnectConstants.CCC_GENERIC_OPPORTUNITY,
        )
        connectIntent.putExtra(ConnectConstants.OPPORTUNITY_UUID, uuid)
        connectIntent.putExtra(ConnectConstants.FROM_SMS_INVITE_LINK, true)
        connectIntent.putExtra(ConnectConstants.SHOW_LAUNCH_BUTTON, true)

        return connectIntent
    }
}
