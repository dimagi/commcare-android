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
import com.google.android.material.textfield.TextInputLayout
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
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

/** Editing the demographic fields, which are saved on the device only, and the language picker. */
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

    private fun openLanguagePicker(): PersonalIdLanguagePickerSheet {
        onUiThread { languagesBox().performClick() }
        val sheet =
            fragment.childFragmentManager
                .findFragmentByTag(PersonalIdLanguagePickerSheet.TAG) as PersonalIdLanguagePickerSheet
        onUiThread {
            sheet.binding.languageList.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY),
            )
            sheet.binding.languageList.layout(0, 0, 1080, 4000)
        }
        return sheet
    }

    private fun PersonalIdLanguagePickerSheet.visibleRows() =
        (0 until binding.languageList.childCount).map {
            binding.languageList.getChildAt(it).findViewById<CheckBox>(R.id.language_checkbox)
        }

    /** The list is filtered on a background thread, so wait until the rows shown match the query. */
    private fun PersonalIdLanguagePickerSheet.search(query: String) {
        onUiThread { binding.languageSearch.setText(query) }
        repeat(MAX_LIST_WAITS) {
            onUiThread {
                binding.languageList.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY),
                )
                binding.languageList.layout(0, 0, 1080, 4000)
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

    private fun PersonalIdLanguagePickerSheet.tick(label: String) {
        val box = visibleRows().first { it.text.toString() == label }
        onUiThread { box.performClick() }
    }

    private fun PersonalIdLanguagePickerSheet.done() {
        onUiThread { binding.languageDone.performClick() }
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
    fun `a partial save stores only the chosen fields on the device without calling the server`() {
        chooseOption(R.id.profile_gender_dropdown, 0)
        pickLanguage("hindi", "हिन्दी (Hindi)")

        onUiThread { saveButton().performClick() }

        assertEquals("female", user.gender)
        assertEquals("hin", user.languages)
        assertNull(user.dateOfBirth)
        assertNull(user.education)
        assertNull(user.chwExperience)
        assertNull(user.techProficiency)
        assertEquals("Demographic fields are not sent yet", 0, mockWebServer.requestCount)
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

        onUiThread { saveButton().performClick() }

        assertEquals("prefer_not_to_say", user.gender)
        assertEquals("bachelors_degree", user.education)
        assertEquals("3_5_years", user.chwExperience)
        assertEquals("comfortable", user.techProficiency)
        assertEquals("hau,eng", user.languages)
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
        onUiThread { saveButton().performClick() }

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
