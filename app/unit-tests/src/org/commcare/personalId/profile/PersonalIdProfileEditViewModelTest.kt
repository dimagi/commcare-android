package org.commcare.personalId.profile

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileEditViewModelTest {
    private lateinit var connectUserDatabaseUtilMock: MockedStatic<ConnectUserDatabaseUtil>
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
    }

    @After
    fun tearDown() {
        connectUserDatabaseUtilMock.close()
    }

    private fun buildViewModel(
        name: String = "Ada Lovelace",
        email: String? = "ada@example.com",
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): PersonalIdProfileEditViewModel {
        val user =
            ConnectUserRecord().apply {
                this.name = name
                this.email = email
            }
        connectUserDatabaseUtilMock
            .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser(any()) }
            .thenReturn(user)
        return PersonalIdProfileEditViewModel(application, savedStateHandle)
    }

    @Test
    fun `current values default to the record's name and email`() {
        val viewModel = buildViewModel()

        assertEquals("Ada Lovelace", viewModel.currentName)
        assertEquals("ada@example.com", viewModel.currentEmail)
    }

    @Test
    fun `current email defaults to empty for a record without an email`() {
        val viewModel = buildViewModel(email = null)

        assertEquals("", viewModel.currentEmail)
    }

    @Test
    fun `isNameModified flips when the name changes and reverts when restored`() {
        val viewModel = buildViewModel()

        assertFalse(viewModel.isNameModified())
        viewModel.onNameChanged("Grace Hopper")
        assertTrue(viewModel.isNameModified())
        assertFalse(viewModel.isEmailModified())
        viewModel.onNameChanged("Ada Lovelace")
        assertFalse(viewModel.isNameModified())
    }

    @Test
    fun `isEmailModified flips when the email changes and reverts when restored`() {
        val viewModel = buildViewModel()

        assertFalse(viewModel.isEmailModified())
        viewModel.onEmailChanged("grace@example.com")
        assertTrue(viewModel.isEmailModified())
        assertFalse(viewModel.isNameModified())
        viewModel.onEmailChanged("ada@example.com")
        assertFalse(viewModel.isEmailModified())
    }

    @Test
    fun `isModified is true when either field is modified`() {
        val viewModel = buildViewModel()

        assertFalse(viewModel.isModified())
        viewModel.onNameChanged("Grace Hopper")
        assertTrue(viewModel.isModified())
        viewModel.onNameChanged("Ada Lovelace")
        viewModel.onEmailChanged("grace@example.com")
        assertTrue(viewModel.isModified())
    }

    @Test
    fun `onNameChanged and onEmailChanged trim surrounding whitespace`() {
        val viewModel = buildViewModel()

        viewModel.onNameChanged(" Ada Lovelace ")
        viewModel.onEmailChanged(" ada@example.com ")

        assertEquals("Ada Lovelace", viewModel.currentName)
        assertEquals("ada@example.com", viewModel.currentEmail)
        assertFalse(viewModel.isModified())
    }

    @Test
    fun `onPhotoUpdated stores the new photo on the user record`() {
        val viewModel = buildViewModel()

        viewModel.onPhotoUpdated("new-base64-photo")

        assertEquals("new-base64-photo", viewModel.user.photo)
        assertFalse(viewModel.isModified())
    }

    @Test
    fun `isNameValid is false for a blank name`() {
        val viewModel = buildViewModel()

        assertTrue(viewModel.isNameValid())
        viewModel.onNameChanged("")
        assertFalse(viewModel.isNameValid())
        viewModel.onNameChanged("Grace Hopper")
        assertTrue(viewModel.isNameValid())
    }

    @Test
    fun `isEmailValid with no original email accepts empty or well-formed input`() {
        val viewModel = buildViewModel(email = null)

        assertTrue(viewModel.isEmailValid())
        viewModel.onEmailChanged("ada@example.com")
        assertTrue(viewModel.isEmailValid())
    }

    @Test
    fun `isEmailValid with no original email rejects malformed input`() {
        val viewModel = buildViewModel(email = null)

        viewModel.onEmailChanged("not-an-email")
        assertFalse(viewModel.isEmailValid())
    }

    @Test
    fun `isEmailValid with an original email rejects clearing the address`() {
        val viewModel = buildViewModel()

        viewModel.onEmailChanged("")
        assertFalse(viewModel.isEmailValid())
    }

    @Test
    fun `isEmailValid with an original email requires a well-formed address`() {
        val viewModel = buildViewModel()

        viewModel.onEmailChanged("not-an-email")
        assertFalse(viewModel.isEmailValid())
        viewModel.onEmailChanged("grace@example.com")
        assertTrue(viewModel.isEmailValid())
    }

    @Test
    fun `isEmailEmpty reflects the current email`() {
        val viewModel = buildViewModel()

        assertFalse(viewModel.isEmailEmpty())
        viewModel.onEmailChanged("")
        assertTrue(viewModel.isEmailEmpty())
    }

    @Test
    fun `canSave requires a modification and valid fields`() {
        val viewModel = buildViewModel()

        assertFalse(viewModel.canSave())
        viewModel.onNameChanged("Grace Hopper")
        assertTrue(viewModel.canSave())
        viewModel.onNameChanged("")
        assertFalse(viewModel.canSave())
        viewModel.onNameChanged("Ada Lovelace")
        viewModel.onEmailChanged("not-an-email")
        assertFalse(viewModel.canSave())
        viewModel.onEmailChanged("grace@example.com")
        assertTrue(viewModel.canSave())
    }

    @Test
    fun `values persisted in the saved state handle survive recreation`() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = buildViewModel(savedStateHandle = savedStateHandle)
        viewModel.onNameChanged("Grace Hopper")
        viewModel.onEmailChanged("grace@example.com")

        val recreatedViewModel = buildViewModel(savedStateHandle = savedStateHandle)

        assertEquals("Grace Hopper", recreatedViewModel.currentName)
        assertEquals("grace@example.com", recreatedViewModel.currentEmail)
        assertTrue(recreatedViewModel.isModified())
    }

    @Test
    fun `commitNameToRecord updates only the name on a freshly-read record and preserves its other fields`() {
        val viewModel = buildViewModel()
        viewModel.onNameChanged("Grace Hopper")

        val storedUser =
            ConnectUserRecord().apply {
                name = "Ada Lovelace"
                email = "ada@example.com"
                photo = "stored-photo"
            }
        connectUserDatabaseUtilMock
            .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser(any()) }
            .thenReturn(storedUser)

        viewModel.commitNameToRecord()

        assertFalse(viewModel.isNameModified())
        val storedRecordCaptor = argumentCaptor<ConnectUserRecord>()
        connectUserDatabaseUtilMock.verify {
            ConnectUserDatabaseUtil.storeUser(any(), storedRecordCaptor.capture())
        }
        val persisted = storedRecordCaptor.firstValue
        assertSame(storedUser, persisted)
        assertEquals("Grace Hopper", persisted.name)
        assertEquals("stored-photo", persisted.photo)
    }
}
