package org.commcare.android

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import org.commcare.CommCareApplication
import org.commcare.utils.IntegrityTokenViewModel

/**
 * Singleton Wrapper for view models scoped to application lifecycle
 */
object CommCareViewModelProvider {
    private var integrityTokenViewModel: IntegrityTokenViewModel? = null

    fun getIntegrityTokenViewModel(): IntegrityTokenViewModel {
        if (integrityTokenViewModel == null) {
            integrityTokenViewModel = ViewModelProvider.AndroidViewModelFactory
                .getInstance(CommCareApplication.instance())
                .create(IntegrityTokenViewModel::class.java)
        }
        return integrityTokenViewModel!!
    }
}
