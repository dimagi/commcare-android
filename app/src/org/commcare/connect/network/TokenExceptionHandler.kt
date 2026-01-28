package org.commcare.connect.network

import android.content.Context
import android.widget.Toast
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.dalvik.R
import org.commcare.utils.GlobalErrors

object TokenExceptionHandler {
    fun handleTokenUnavailableException(context: Context) {
        Toast
            .makeText(
                context,
                context.getString(R.string.recovery_network_token_unavailable),
                Toast.LENGTH_LONG,
            ).show()
    }

    fun handleTokenDeniedException() {
        ConnectDatabaseHelper.crashDb(GlobalErrors.PERSONALID_LOST_CONFIGURATION_ERROR)
    }
}
