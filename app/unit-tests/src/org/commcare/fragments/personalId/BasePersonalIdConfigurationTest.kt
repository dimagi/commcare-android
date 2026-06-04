package org.commcare.fragments.personalId

import androidx.annotation.CallSuper
import androidx.lifecycle.MutableLiveData
import com.google.android.play.core.integrity.StandardIntegrityManager
import org.commcare.android.CommCareViewModelProvider
import org.commcare.android.integrity.IntegrityTokenViewModel
import org.junit.After
import org.junit.Before
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

const val TEST_INTEGRITY_TOKEN: String = "test_integrity_token_12345"

/**
 * Shared base for PersonalID-flow fragment tests that boot [org.commcare.activities.connect.PersonalIdActivity].
 *
 * The nav graph's start destination is the phone fragment, which constructs an
 * `IntegrityTokenApiRequestHelper` in `onCreateView` — that in turn instantiates a real
 * [IntegrityTokenViewModel] whose `init` block requires a non-default `GOOGLE_CLOUD_PROJECT_NUMBER`
 * BuildConfig value and therefore fails under Robolectric. This base injects a mocked
 * viewmodel via [CommCareViewModelProvider] reflection before any activity setup runs, and
 * clears the static field again in teardown so tests do not leak into each other.
 *
 * Concrete fragment-specific bases (e.g. [BasePersonalIdPhoneFragmentTest],
 * [BasePersonalIdEmailFragmentTest]) extend this and add their own fragment / activity setup,
 * calling `super.setUp()` and `super.tearDown()` so the integrity mock is in place at the
 * moment `PersonalIdActivity` boots.
 */
abstract class BasePersonalIdConfigurationTest {
    @Before
    @CallSuper
    open fun setUp() {
        setupMockIntegrityTokenViewModel()
    }

    @After
    @CallSuper
    open fun tearDown() {
        val viewModelField = CommCareViewModelProvider::class.java.getDeclaredField("integrityTokenViewModel")
        viewModelField.isAccessible = true
        viewModelField.set(null, null)
    }

    private fun setupMockIntegrityTokenViewModel() {
        val mockToken = mock(StandardIntegrityManager.StandardIntegrityToken::class.java)
        val mockTokenProvider = mock(StandardIntegrityManager.StandardIntegrityTokenProvider::class.java)
        val mockViewModel = mock(IntegrityTokenViewModel::class.java)

        `when`(mockToken.token()).thenReturn(TEST_INTEGRITY_TOKEN)

        val providerStateLiveData = MutableLiveData<IntegrityTokenViewModel.TokenProviderState>()
        providerStateLiveData.postValue(
            IntegrityTokenViewModel.TokenProviderState.Success(mockTokenProvider),
        )
        `when`(mockViewModel.providerState).thenReturn(providerStateLiveData)

        doAnswer { invocation ->
            val callback = invocation.arguments[2] as IntegrityTokenViewModel.IntegrityTokenCallback
            val requestHash = invocation.arguments[0] as String
            callback.onTokenReceived(requestHash, mockToken)
            null
        }.`when`(mockViewModel).requestIntegrityToken(any(), any(), any())

        val field = CommCareViewModelProvider::class.java.getDeclaredField("integrityTokenViewModel")
        field.isAccessible = true
        field.set(null, mockViewModel)
    }
}
