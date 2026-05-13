package org.commcare.connect

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ReleaseToggleHelperTest {
    private val context: Context = CommCareTestApplication.instance()

    @Before
    fun setUp() {
        mockkStatic(ConnectAppDatabaseUtil::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ConnectAppDatabaseUtil::class)
    }

    // --- isToggleActive(sessionData, slug) -------------------------------------------------

    @Test
    fun `isToggleActive with session returns true when slug is present and active`() {
        val sessionData =
            PersonalIdSessionData(
                featureReleaseToggles = listOf(buildToggle("feature_a", active = true)),
            )

        assertTrue(ReleaseToggleHelper.isToggleActive(sessionData, "feature_a"))
    }

    @Test
    fun `isToggleActive with session returns false when slug is present but inactive`() {
        val sessionData =
            PersonalIdSessionData(
                featureReleaseToggles = listOf(buildToggle("feature_a", active = false)),
            )

        assertFalse(ReleaseToggleHelper.isToggleActive(sessionData, "feature_a"))
    }

    @Test
    fun `isToggleActive with session returns false when slug is missing from the list`() {
        val sessionData =
            PersonalIdSessionData(
                featureReleaseToggles = listOf(buildToggle("other_feature", active = true)),
            )

        assertFalse(ReleaseToggleHelper.isToggleActive(sessionData, "feature_a"))
    }

    @Test
    fun `isToggleActive with session returns false when toggle list is empty`() {
        val sessionData = PersonalIdSessionData(featureReleaseToggles = emptyList())

        assertFalse(ReleaseToggleHelper.isToggleActive(sessionData, "feature_a"))
    }

    @Test
    fun `isToggleActive with session returns false when toggle list is null`() {
        val sessionData = PersonalIdSessionData(featureReleaseToggles = null)

        assertFalse(ReleaseToggleHelper.isToggleActive(sessionData, "feature_a"))
    }

    @Test
    fun `isToggleActive with session returns false when session data is null`() {
        assertFalse(ReleaseToggleHelper.isToggleActive(null as PersonalIdSessionData?, "feature_a"))
    }

    @Test
    fun `isToggleActive with session returns first matching record when duplicates exist`() {
        val sessionData =
            PersonalIdSessionData(
                featureReleaseToggles =
                    listOf(
                        buildToggle("feature_a", active = true),
                        buildToggle("feature_a", active = false),
                    ),
            )

        assertTrue(ReleaseToggleHelper.isToggleActive(sessionData, "feature_a"))
    }

    // --- isToggleActive(context, slug) -----------------------------------------------------

    @Test
    fun `isToggleActive with context returns true when DB contains active toggle for slug`() {
        every { ConnectAppDatabaseUtil.getReleaseToggles(context) } returns
            listOf(buildToggle("feature_a", active = true))

        assertTrue(ReleaseToggleHelper.isToggleActive(context, "feature_a"))
    }

    @Test
    fun `isToggleActive with context returns false when DB contains inactive toggle for slug`() {
        every { ConnectAppDatabaseUtil.getReleaseToggles(context) } returns
            listOf(buildToggle("feature_a", active = false))

        assertFalse(ReleaseToggleHelper.isToggleActive(context, "feature_a"))
    }

    @Test
    fun `isToggleActive with context returns false when slug is missing from DB`() {
        every { ConnectAppDatabaseUtil.getReleaseToggles(context) } returns
            listOf(buildToggle("other_feature", active = true))

        assertFalse(ReleaseToggleHelper.isToggleActive(context, "feature_a"))
    }

    @Test
    fun `isToggleActive with context returns false when DB returns empty list`() {
        every { ConnectAppDatabaseUtil.getReleaseToggles(context) } returns emptyList()

        assertFalse(ReleaseToggleHelper.isToggleActive(context, "feature_a"))
    }

    @Test
    fun `isToggleActive with context returns false when DB returns null`() {
        every { ConnectAppDatabaseUtil.getReleaseToggles(context) } returns null

        assertFalse(ReleaseToggleHelper.isToggleActive(context, "feature_a"))
    }

    private fun buildToggle(
        slug: String,
        active: Boolean,
    ): ConnectReleaseToggleRecord =
        ConnectReleaseToggleRecord.releaseToggleFromJson(
            slug,
            JSONObject().apply { put("active", active) },
        )
}
