package org.commcare.views

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.adapters.ComboboxAdapter
import org.javarosa.core.model.ComboItem
import org.javarosa.core.model.ComboboxFilterRule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Vector

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ComboboxTest {
    @Test
    fun autoCorrectCapitalization_rewritesWrongCaseToCanonical() {
        val combobox = newCombobox("Apple", "Banana", "Cherry")
        combobox.setText("apple")

        combobox.autoCorrectCapitalization()

        assertEquals("Apple", combobox.text.toString())
    }

    @Test
    fun autoCorrectCapitalization_leavesCanonicalCasingUntouched() {
        val combobox = newCombobox("Apple", "Banana")
        combobox.setText("Apple")
        val originalEditable = combobox.text

        combobox.autoCorrectCapitalization()

        assertEquals("Apple", combobox.text.toString())
        // Same Editable instance — autoCorrectCapitalization should not have called setText.
        assertSameEditable(originalEditable, combobox.text)
    }

    @Test
    fun autoCorrectCapitalization_leavesUnknownTextUntouched() {
        val combobox = newCombobox("Apple", "Banana")
        combobox.setText("xyz")

        combobox.autoCorrectCapitalization()

        assertEquals("xyz", combobox.text.toString())
    }

    @Test
    fun autoCorrectCapitalization_leavesEmptyTextUntouched() {
        val combobox = newCombobox("Apple", "Banana")
        combobox.setText("")

        combobox.autoCorrectCapitalization()

        assertEquals("", combobox.text.toString())
    }

    private fun newCombobox(vararg displayTexts: String): Combobox {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val items = displayTexts.mapIndexed { i, label -> ComboItem(label, label, i) }
        val choicesVector = Vector(items)
        val adapter = ComboboxAdapter(context, items.toTypedArray(), permissiveFilterRule())
        return Combobox(context, choicesVector, adapter)
    }

    private fun permissiveFilterRule(): ComboboxFilterRule =
        object : ComboboxFilterRule {
            override fun choiceShouldBeShown(
                choice: ComboItem,
                textEntered: CharSequence,
            ): Boolean = true

            override fun shouldRestrictTyping(): Boolean = false
        }

    private fun assertSameEditable(
        expected: CharSequence,
        actual: CharSequence,
    ) {
        if (expected !== actual) {
            throw AssertionError("Expected the same Editable instance (no setText call), but got a different one")
        }
    }
}
