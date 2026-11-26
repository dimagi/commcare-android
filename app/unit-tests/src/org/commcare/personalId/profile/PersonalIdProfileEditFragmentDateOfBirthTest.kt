package org.commcare.personalId.profile

import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.personalId.profile.demographic.DateOfBirth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.TimeZone

@Config(application = CommCareTestApplication::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
class PersonalIdProfileEditFragmentDateOfBirthTest(
    private val timeZoneId: String,
) : BasePersonalIdProfileTest() {
    @get:Rule
    val deviceTimeZone =
        object : ExternalResource() {
            private lateinit var original: TimeZone

            override fun before() {
                original = TimeZone.getDefault()
                TimeZone.setDefault(TimeZone.getTimeZone(timeZoneId))
            }

            override fun after() {
                TimeZone.setDefault(original)
            }
        }

    private lateinit var fragment: PersonalIdProfileEditFragment

    private fun openEditScreen(storedDateOfBirth: String?) {
        user.dateOfBirth = storedDateOfBirth
        onUiThread { navController.navigate(R.id.action_profile_to_profile_edit) }
        fragment =
            navHostFragment.childFragmentManager
                .primaryNavigationFragment as PersonalIdProfileEditFragment
    }

    private fun dateOfBirthField() = fragment.requireView().findViewById<TextInputEditText>(R.id.profile_date_of_birth_edit_text)

    private fun openPicker(): MaterialDatePicker<*> {
        onUiThread { dateOfBirthField().performClick() }
        return fragment.childFragmentManager.fragments
            .filterIsInstance<MaterialDatePicker<*>>()
            .single()
    }

    private fun MaterialDatePicker<*>.tapDay(day: Int) {
        val pickerView = requireDialog().window!!.decorView
        onUiThread {
            pickerView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
            )
            pickerView.layout(0, 0, 1080, 2400)
        }
        val grid =
            findMonthGrids(pickerView)
                .first { grid -> (0 until grid.adapter.count).any { grid.adapter.getItem(it) == selection } }
        val position = (0 until grid.childCount).first { (grid.getChildAt(it) as? TextView)?.text == day.toString() }
        onUiThread { grid.performItemClick(grid.getChildAt(position), grid.firstVisiblePosition + position, 0) }
    }

    private fun findMonthGrids(view: View): List<GridView> =
        when (view) {
            is GridView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { findMonthGrids(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun MaterialDatePicker<*>.tapOk() {
        val ok = requireDialog().findViewById<View>(com.google.android.material.R.id.confirm_button)
        onUiThread { ok.performClick() }
        ShadowLooper.idleMainLooper()
    }

    private fun save() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        onUiThread { fragment.requireView().findViewById<MaterialButton>(R.id.btn_save).performClick() }
        mockApiServer.drainHttp()
    }

    @Test
    fun `the picker opens on the stored date and confirming keeps it`() {
        openEditScreen("1994-03-21")

        val picker = openPicker()
        assertEquals(DateOfBirth.toUtcMillis("1994-03-21"), picker.selection)
        picker.tapOk()

        assertEquals(DateOfBirth.formatDate("1994-03-21"), dateOfBirthField().text.toString())
    }

    @Test
    fun `picking a day stores that calendar date`() {
        openEditScreen("1994-03-21")

        openPicker().apply {
            tapDay(15)
            tapOk()
        }
        assertEquals(DateOfBirth.formatDate("1994-03-15"), dateOfBirthField().text.toString())
        save()

        assertEquals("1994-03-15", user.dateOfBirth)
    }

    @Test
    fun `the first day of a month is not shifted into the previous month`() {
        openEditScreen("2000-01-20")

        openPicker().apply {
            tapDay(1)
            tapOk()
        }
        save()

        assertEquals("2000-01-01", user.dateOfBirth)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun timeZones() =
            listOf(
                arrayOf("Pacific/Pago_Pago"),
                arrayOf("UTC"),
                arrayOf("Asia/Kolkata"),
                arrayOf("Pacific/Kiritimati"),
            )
    }
}
