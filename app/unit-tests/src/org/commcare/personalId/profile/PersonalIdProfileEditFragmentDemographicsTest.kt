package org.commcare.personalId.profile

import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.views.BottomSheetListPicker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

/** Editing and saving the demographic fields, and the language picker. */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileEditFragmentDemographicsTest : BasePersonalIdProfileTest() {
    private lateinit var fragment: PersonalIdProfileEditFragment

    @Before
    fun navigateToEditFragment() {
        openEditScreen()
    }

    private fun openEditScreen() {
        onUiThread { navController.navigate(R.id.action_profile_to_profile_edit) }
        fragment =
            navHostFragment.childFragmentManager
                .primaryNavigationFragment as PersonalIdProfileEditFragment
    }

    private fun <T : View> view(id: Int) = fragment.requireView().findViewById<T>(id)

    private fun saveButton() = view<MaterialButton>(R.id.btn_save)

    private fun saveWithResponse(code: Int): RecordedRequest {
        mockWebServer.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
        onUiThread { saveButton().performClick() }
        return mockApiServer.drainHttp()
    }

    private fun RecordedRequest.bodyFields(): Map<String, String> =
        JSONObject(body.readUtf8()).let { json -> json.keys().asSequence().associateWith { json.getString(it) } }

    private fun languagesBox() = view<MaterialCardView>(R.id.profile_input_languages)

    private fun languageChips() =
        view<ChipGroup>(R.id.profile_languages_chips).let { group ->
            (0 until group.childCount).map { group.getChildAt(it) as Chip }
        }

    private fun warningColor() = MaterialColors.getColor(fragment.requireView(), R.attr.connectStatusWarning)

    /** The outline a field shows while not focused, which is what an empty field's warning has to colour. */
    private fun restingStrokeColor(field: TextInputLayout) = ReflectionHelpers.getField<Int>(field, "defaultStrokeColor")

    private fun chooseOption(
        dropdownId: Int,
        position: Int,
    ) {
        val dropdown = view<MaterialAutoCompleteTextView>(dropdownId)
        onUiThread {
            dropdown.showDropDown()
            dropdown.listSelection = position
            dropdown.performCompletion()
        }
    }

    private fun openLanguagePicker(): BottomSheetListPicker {
        onUiThread { languagesBox().performClick() }
        val sheet =
            fragment.childFragmentManager
                .findFragmentByTag(PersonalIdProfileEditFragment.LANGUAGE_PICKER_TAG) as BottomSheetListPicker
        onUiThread {
            sheet.binding.pickerList.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY),
            )
            sheet.binding.pickerList.layout(0, 0, 1080, 4000)
        }
        return sheet
    }

    private fun BottomSheetListPicker.visibleRows() =
        (0 until binding.pickerList.childCount).map {
            binding.pickerList.getChildAt(it).findViewById<CheckBox>(R.id.picker_checkbox)
        }

    /** The list is filtered on a background thread, so wait until the rows shown match the query. */
    private fun BottomSheetListPicker.search(query: String) {
        onUiThread { binding.pickerSearch.setText(query) }
        repeat(MAX_LIST_WAITS) {
            onUiThread {
                binding.pickerList.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY),
                )
                binding.pickerList.layout(0, 0, 1080, 4000)
            }
            val rows = visibleRows()
            if (rows.isNotEmpty() &&
                rows.all {
                    it.text
                        .toString()
                        .lowercase()
                        .contains(query.lowercase())
                }
            ) {
                return
            }
            Thread.sleep(LIST_WAIT_MILLIS)
        }
    }

    private fun BottomSheetListPicker.tick(label: String) {
        val box = visibleRows().first { it.text.toString() == label }
        onUiThread { box.performClick() }
    }

    private fun BottomSheetListPicker.done() {
        onUiThread { binding.pickerDone.performClick() }
    }

    private fun pickLanguage(
        query: String,
        label: String,
    ) {
        openLanguagePicker().apply {
            search(query)
            tick(label)
            done()
        }
    }

    // ========== Warning borders ==========

    @Test
    fun `empty demographic fields show the warning outline`() {
        val warning = warningColor()

        listOf(
            R.id.profile_input_gender,
            R.id.profile_input_date_of_birth,
            R.id.profile_input_education,
            R.id.profile_input_chw_experience,
            R.id.profile_input_tech_proficiency,
        ).forEach { assertEquals(warning, restingStrokeColor(view(it))) }
        assertEquals(warning, languagesBox().strokeColor)
    }

    @Test
    fun `choosing an option clears its warning without saving`() {
        chooseOption(R.id.profile_gender_dropdown, 1)

        assertNotEquals(warningColor(), restingStrokeColor(view(R.id.profile_input_gender)))
        assertEquals(warningColor(), restingStrokeColor(view(R.id.profile_input_education)))
        assertNull("Nothing is saved until Save is tapped", user.gender)
    }

    @Test
    fun `removing the last language shows the warning again`() {
        pickLanguage("hausa", "Hausa")
        assertNotEquals(warningColor(), languagesBox().strokeColor)

        onUiThread { languageChips().single().performCloseIconClick() }

        assertEquals(warningColor(), languagesBox().strokeColor)
    }

    // ========== Save ==========

    @Test
    fun `changing only a demographic field enables save`() {
        chooseOption(R.id.profile_education_dropdown, 0)

        assertTrue(saveButton().isEnabled)
    }

    @Test
    fun `a partial save sends every field with the unset ones empty and stores the chosen ones after success`() {
        chooseOption(R.id.profile_gender_dropdown, 0)
        pickLanguage("hindi", "हिन्दी (Hindi)")

        val request = saveWithResponse(200)

        assertEquals("/users/update_profile", request.path)
        assertEquals(
            mapOf(
                "name" to "Ada Lovelace",
                "gender" to "female",
                "date_of_birth" to "",
                "languages" to "hin",
                "education" to "",
                "chw_experience" to "",
                "tech_proficiency" to "",
            ),
            request.bodyFields(),
        )
        assertEquals("female", user.gender)
        assertEquals("hin", user.languages)
        assertNull(user.dateOfBirth)
        assertNull(user.education)
        assertNull(user.chwExperience)
        assertNull(user.techProficiency)
        assertEquals(R.id.personalid_profile_fragment, currentDestinationId())
    }

    @Test
    fun `saving every dropdown and several languages stores their keys and codes`() {
        chooseOption(R.id.profile_gender_dropdown, 2)
        chooseOption(R.id.profile_education_dropdown, 3)
        chooseOption(R.id.profile_chw_experience_dropdown, 2)
        chooseOption(R.id.profile_tech_proficiency_dropdown, 2)
        pickLanguage("hausa", "Hausa")
        pickLanguage("english", "English")

        val fields = saveWithResponse(200).bodyFields()

        assertEquals("prefer_not_to_say", fields["gender"])
        assertEquals("bachelors_degree", fields["education"])
        assertEquals("3_5_years", fields["chw_experience"])
        assertEquals("comfortable", fields["tech_proficiency"])
        assertEquals("hau,eng", fields["languages"])
        assertEquals("prefer_not_to_say", user.gender)
        assertEquals("bachelors_degree", user.education)
        assertEquals("3_5_years", user.chwExperience)
        assertEquals("comfortable", user.techProficiency)
        assertEquals("hau,eng", user.languages)
    }

    @Test
    fun `changing the name and a demographic field sends both in one request`() {
        onUiThread { view<TextInputEditText>(R.id.profile_name_edit_text).setText("Grace Hopper") }
        chooseOption(R.id.profile_education_dropdown, 0)

        val fields = saveWithResponse(200).bodyFields()

        assertEquals("Grace Hopper", fields["name"])
        assertEquals("no_formal_education", fields["education"])
        assertEquals(1, mockWebServer.requestCount)
        assertEquals("Grace Hopper", user.name)
        assertEquals("no_formal_education", user.education)
    }

    @Test
    fun `removing a saved language sends it empty and clears it after success`() {
        onUiThread { navController.popBackStack() }
        user.languages = "hau"
        openEditScreen()
        onUiThread { languageChips().single().performCloseIconClick() }

        val fields = saveWithResponse(200).bodyFields()

        assertEquals("", fields["languages"])
        assertNull(user.languages)
    }

    @Test
    fun `a failed save keeps the stored demographics and stays on the edit screen`() {
        chooseOption(R.id.profile_gender_dropdown, 0)

        saveWithResponse(500)

        assertNull(user.gender)
        assertEquals(R.id.personalid_profile_edit_fragment, currentDestinationId())
        assertTrue(saveButton().isEnabled)
    }

    @Test
    fun `back after changing only a demographic field asks to discard`() {
        chooseOption(R.id.profile_tech_proficiency_dropdown, 1)

        onUiThread { activity.onBackPressedDispatcher.onBackPressed() }

        assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
    }

    // ========== Language chips ==========

    @Test
    fun `a chip's close icon removes only that language`() {
        pickLanguage("hausa", "Hausa")
        pickLanguage("english", "English")

        onUiThread { languageChips().first { it.text == "Hausa" }.performCloseIconClick() }

        assertEquals(listOf("English"), languageChips().map { it.text.toString() })
    }

    @Test
    fun `a stored language no longer in the list gets no chip but is kept on save`() {
        onUiThread { navController.popBackStack() }
        user.languages = "xyz,hau"
        openEditScreen()

        assertEquals(listOf("Hausa"), languageChips().map { it.text.toString() })
        chooseOption(R.id.profile_gender_dropdown, 0)

        assertEquals("xyz,hau", saveWithResponse(200).bodyFields()["languages"])
        assertEquals("xyz,hau", user.languages)
    }

    // ========== Language picker ==========

    @Test
    fun `searching finds a language and Done adds its chip`() {
        pickLanguage("hindi", "हिन्दी (Hindi)")

        assertEquals(listOf("हिन्दी (Hindi)"), languageChips().map { it.text.toString() })
    }

    @Test
    fun `a ticked language stays ticked when the search changes`() {
        openLanguagePicker().apply {
            search("hausa")
            tick("Hausa")
            search("")
            search("hau")

            assertTrue(visibleRows().first { it.text.toString() == "Hausa" }.isChecked)
        }
    }

    @Test
    fun `closing the picker without Done changes nothing`() {
        openLanguagePicker().apply {
            search("hausa")
            tick("Hausa")
            onUiThread { dismiss() }
        }

        assertEquals(emptyList<String>(), languageChips().map { it.text.toString() })
    }

    @Test
    fun `already chosen languages open first and ticked`() {
        pickLanguage("yoruba", "Yorùbá (Yoruba)")

        openLanguagePicker().apply {
            val first = visibleRows().first()
            assertEquals("Yorùbá (Yoruba)", first.text.toString())
            assertTrue(first.isChecked)
        }
    }

    private companion object {
        const val MAX_LIST_WAITS = 50
        const val LIST_WAIT_MILLIS = 20L
    }
}
