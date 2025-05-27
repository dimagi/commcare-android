package org.commcare.android

import androidx.lifecycle.ViewModelProvider
import org.commcare.CommCareApplication
import org.commcare.android.integrity.IntegrityTokenViewModel

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
