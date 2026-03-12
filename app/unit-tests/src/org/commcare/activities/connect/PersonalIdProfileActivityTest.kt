package org.commcare.activities.connect

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileActivityTest {
    private lateinit var connectUserDbMock: MockedStatic<ConnectUserDatabaseUtil>
    private lateinit var mockUser: ConnectUserRecord

    @Before
    fun setUp() {
        mockUser = mock(ConnectUserRecord::class.java)
        `when`(mockUser.name).thenReturn("Test User")
        `when`(mockUser.userId).thenReturn("user-123")
        `when`(mockUser.password).thenReturn("password-456")

        connectUserDbMock = mockStatic(ConnectUserDatabaseUtil::class.java)
        connectUserDbMock
            .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser(any()) }
            .thenReturn(mockUser)
    }

    @After
    fun tearDown() {
        connectUserDbMock.close()
    }

    private fun buildActivity(): ActivityController<PersonalIdProfileActivity> =
        Robolectric.buildActivity(PersonalIdProfileActivity::class.java)

    @Test
    fun testInitialUiStateWithNoPhoto() {
        `when`(mockUser.photo).thenReturn(null)

        val activity =
            buildActivity()
                .create()
                .start()
                .resume()
                .get()
        ShadowLooper.idleMainLooper()

        val nameView = activity.findViewById<TextView>(R.id.profile_name)
        val button = activity.findViewById<MaterialButton>(R.id.photo_action_button)

        assertEquals("Test User", nameView.text.toString())
        assertEquals(
            activity.getString(R.string.personalid_profile_add_photo),
            button.text.toString(),
        )
    }

    @Test
    fun testInitialUiStateWithExistingPhoto() {
        `when`(mockUser.photo).thenReturn("base64photodata")

        val activity =
            buildActivity()
                .create()
                .start()
                .resume()
                .get()
        ShadowLooper.idleMainLooper()

        val button = activity.findViewById<MaterialButton>(R.id.photo_action_button)

        assertEquals(
            activity.getString(R.string.personalid_profile_update_photo),
            button.text.toString(),
        )
    }

    @Test
    fun testProfilePhotoPlaceholderShownWhenNoPhoto() {
        `when`(mockUser.photo).thenReturn(null)

        val activity =
            buildActivity()
                .create()
                .start()
                .resume()
                .get()
        ShadowLooper.idleMainLooper()

        val photoView = activity.findViewById<ImageView>(R.id.profile_photo)
        assertEquals(View.VISIBLE, photoView.visibility)
    }

    @Test
    fun testButtonIsEnabledInitially() {
        `when`(mockUser.photo).thenReturn(null)

        val activity =
            buildActivity()
                .create()
                .start()
                .resume()
                .get()
        ShadowLooper.idleMainLooper()

        val button = activity.findViewById<MaterialButton>(R.id.photo_action_button)
        assertTrue(button.isEnabled)
    }

    @Test
    fun testActivityFinishesWhenUserIsNull() {
        connectUserDbMock
            .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser(any()) }
            .thenReturn(null)

        val controller = buildActivity().create()
        val activity = controller.get()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun testUserDbNotStoredBeforeUpload() {
        `when`(mockUser.photo).thenReturn(null)

        buildActivity()
            .create()
            .start()
            .resume()
            .get()
        ShadowLooper.idleMainLooper()

        connectUserDbMock.verify(
            { ConnectUserDatabaseUtil.storeUser(any(), any()) },
            never(),
        )
    }

    @Test
    fun testToolbarTitleSetCorrectly() {
        `when`(mockUser.photo).thenReturn(null)

        val activity =
            buildActivity()
                .create()
                .start()
                .postCreate(null)
                .resume()
                .get()
        ShadowLooper.idleMainLooper()

        val title = activity.supportActionBar?.title
        assertEquals(activity.getString(R.string.personalid_profile_title), title)
    }
}
