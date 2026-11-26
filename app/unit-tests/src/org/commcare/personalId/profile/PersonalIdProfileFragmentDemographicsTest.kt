package org.commcare.personalId.profile

import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The profile screen with some demographic fields filled in and the rest left empty. */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileFragmentDemographicsTest : BasePersonalIdProfileTest() {
    override fun buildUser(): ConnectUserRecord =
        super.buildUser().apply {
            gender = "male"
            languages = "hau,eng"
            education = "masters_or_higher"
        }

    private fun fragment() =
        navHostFragment.childFragmentManager
            .primaryNavigationFragment as PersonalIdProfileFragment

    private fun valueOf(
        @IdRes id: Int,
    ) = fragment()
        .requireView()
        .findViewById<TextView>(id)
        .text
        .toString()

    private fun languageChips() =
        fragment().requireView().findViewById<ChipGroup>(R.id.profile_value_languages).let { group ->
            (0 until group.childCount).map { group.getChildAt(it) as Chip }
        }

    @Test
    fun `filled demographic fields show their labels`() {
        assertEquals("Male", valueOf(R.id.profile_value_gender))
        assertEquals("Masters Degree or higher", valueOf(R.id.profile_value_education))
    }

    @Test
    fun `empty demographic fields show a dash`() {
        val dash = fragment().getString(R.string.personalid_profile_value_not_set)

        assertEquals(dash, valueOf(R.id.profile_value_date_of_birth))
        assertEquals(dash, valueOf(R.id.profile_value_chw_experience))
        assertEquals(dash, valueOf(R.id.profile_value_tech_proficiency))
    }

    @Test
    fun `languages show as read-only chips by their labels`() {
        val chips = languageChips()

        assertEquals(listOf("Hausa", "English"), chips.map { it.text.toString() })
        chips.forEach { assertFalse("Profile chips must not be removable", it.isCloseIconVisible) }
    }
}
