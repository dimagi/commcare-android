package org.commcare.fragments.personalId

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ApiPersonalId
import org.commcare.personalId.PersonalIdRecoveryCompleter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/**
 * Unit tests for [EmailHelper] covering the two public helpers:
 * - `sendEmailOtp` — workflow → auth-arg mapping (token vs ConnectUserRecord).
 * - `routeAfterEmailDeclined` — workflow → side-effect routing.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class EmailHelperTest {
    @Test
    fun `sendEmailOtp uses session token for REGISTRATION workflow`() {
        val activity = mock(Activity::class.java)
        val sessionData = PersonalIdSessionData(token = "session-token-123")

        mockStatic(ApiPersonalId::class.java).use { mockApi ->
            EmailHelper.sendEmailOtp(
                activity = activity,
                email = "user@example.com",
                workflow = EmailWorkFlow.REGISTRATION,
                sessionData = sessionData,
                onSuccess = {},
                onFailure = { _, _ -> },
            )

            mockApi.verify {
                ApiPersonalId.sendEmailOtp(
                    eq(activity),
                    eq("user@example.com"),
                    eq("session-token-123"),
                    eq(null),
                    any(),
                )
            }
        }
    }

    @Test
    fun `sendEmailOtp uses session token for RECOVERY workflow`() {
        val activity = mock(Activity::class.java)
        val sessionData = PersonalIdSessionData(token = "session-token-456")

        mockStatic(ApiPersonalId::class.java).use { mockApi ->
            EmailHelper.sendEmailOtp(
                activity = activity,
                email = "user@example.com",
                workflow = EmailWorkFlow.RECOVERY,
                sessionData = sessionData,
                onSuccess = {},
                onFailure = { _, _ -> },
            )

            mockApi.verify {
                ApiPersonalId.sendEmailOtp(
                    eq(activity),
                    eq("user@example.com"),
                    eq("session-token-456"),
                    eq(null),
                    any(),
                )
            }
        }
    }

    @Test
    fun `sendEmailOtp uses ConnectUserRecord for EXISTING_USER workflow`() {
        val activity = mock(Activity::class.java)
        val user = mock(ConnectUserRecord::class.java)

        mockStatic(ConnectUserDatabaseUtil::class.java).use { mockedDb ->
            mockedDb
                .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser(activity) }
                .thenReturn(user)

            mockStatic(ApiPersonalId::class.java).use { mockApi ->
                EmailHelper.sendEmailOtp(
                    activity = activity,
                    email = "user@example.com",
                    workflow = EmailWorkFlow.EXISTING_USER,
                    sessionData = null,
                    onSuccess = {},
                    onFailure = { _, _ -> },
                )

                mockApi.verify {
                    ApiPersonalId.sendEmailOtp(
                        eq(activity),
                        eq("user@example.com"),
                        eq(null),
                        eq(user),
                        any(),
                    )
                }
            }
        }
    }

    // ---------- routeAfterEmailDeclined --------------------------------------------------

    @Test
    fun `routeAfterEmailDeclined finishes activity for EXISTING_USER`() {
        val activity = mock(FragmentActivity::class.java)
        val fragment = mock(Fragment::class.java)
        whenever(fragment.requireActivity()).thenReturn(activity)

        var registrationCalled = false
        var recoverySuccessCalled = false

        EmailHelper.routeAfterEmailDeclined(
            fragment = fragment,
            workflow = EmailWorkFlow.EXISTING_USER,
            sessionData = null,
            onRegistration = { registrationCalled = true },
            onRecoverySuccess = { recoverySuccessCalled = true },
        )

        verify(activity).finish()
        assertFalse(registrationCalled)
        assertFalse(recoverySuccessCalled)
    }

    @Test
    fun `routeAfterEmailDeclined invokes onRegistration only for REGISTRATION`() {
        val activity = mock(FragmentActivity::class.java)
        val fragment = mock(Fragment::class.java)
        whenever(fragment.requireActivity()).thenReturn(activity)

        var registrationCalled = false
        var recoverySuccessCalled = false

        EmailHelper.routeAfterEmailDeclined(
            fragment = fragment,
            workflow = EmailWorkFlow.REGISTRATION,
            sessionData = null,
            onRegistration = { registrationCalled = true },
            onRecoverySuccess = { recoverySuccessCalled = true },
        )

        assertTrue(registrationCalled)
        assertFalse(recoverySuccessCalled)
        verify(activity, never()).finish()
    }

    @Test
    fun `routeAfterEmailDeclined invokes onRecoverySuccess for RECOVERY`() {
        val activity = mock(FragmentActivity::class.java)
        val fragment = mock(Fragment::class.java)
        val sessionData = PersonalIdSessionData(token = "tok")
        whenever(fragment.requireActivity()).thenReturn(activity)

        var registrationCalled = false
        var recoverySuccessCalled = false

        mockStatic(PersonalIdRecoveryCompleter::class.java).use { mockCompleter ->
            EmailHelper.routeAfterEmailDeclined(
                fragment = fragment,
                workflow = EmailWorkFlow.RECOVERY,
                sessionData = sessionData,
                onRegistration = { registrationCalled = true },
                onRecoverySuccess = { recoverySuccessCalled = true },
            )
        }

        assertFalse(registrationCalled)
        assertTrue(recoverySuccessCalled)
        verify(activity, never()).finish()
    }
}
