package org.commcare.personalId.profile.demographic

import android.content.Context
import org.commcare.dalvik.R
import org.json.JSONArray
import java.util.Locale
import java.util.MissingResourceException

/** A language the worker can say they speak: [code] is its ISO 639-3 code, [label] is what they see. */
data class PersonalIdLanguage(
    val code: String,
    val label: String,
)

/**
 * The languages offered on the demographic profile, read once from `res/raw/personalid_languages.json`.
 * The worker's choice is stored as comma-separated codes.
 */
object PersonalIdLanguages {
    private const val SEPARATOR = ","

    @Volatile
    private var languages: List<PersonalIdLanguage>? = null

    fun all(context: Context): List<PersonalIdLanguage> = languages ?: loadLanguages(context).also { languages = it }

    fun parseCodes(stored: String?): List<String> =
        stored
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    fun joinCodes(codes: List<String>): String? = codes.joinToString(SEPARATOR).ifEmpty { null }

    /** Labels for [codes] in the order given; codes no longer in the list are left out. */
    fun labelsFor(
        context: Context,
        codes: List<String>,
    ): List<String> {
        val labelsByCode = all(context).associate { it.code to it.label }
        return codes.mapNotNull { labelsByCode[it] }
    }

    /** The device language as an ISO 639-3 code, or null when Android has none for it. */
    fun deviceLanguageCode(locale: Locale = Locale.getDefault()): String? =
        try {
            locale.isO3Language.ifEmpty { null }
        } catch (e: MissingResourceException) {
            null
        }

    /** Selected languages first, then the device language, then the rest alphabetically. */
    fun orderForPicker(
        languages: List<PersonalIdLanguage>,
        selectedCodes: Set<String>,
        deviceLanguageCode: String?,
    ): List<PersonalIdLanguage> =
        languages.sortedWith(
            compareBy<PersonalIdLanguage>(
                { it.code !in selectedCodes },
                { it.code != deviceLanguageCode },
                { it.label.lowercase(Locale.ROOT) },
            ),
        )

    private fun loadLanguages(context: Context): List<PersonalIdLanguage> {
        val json =
            context.resources
                .openRawResource(R.raw.personalid_languages)
                .bufferedReader()
                .use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map {
            val entry = array.getJSONObject(it)
            PersonalIdLanguage(entry.getString("code"), entry.getString("label"))
        }
    }
}
