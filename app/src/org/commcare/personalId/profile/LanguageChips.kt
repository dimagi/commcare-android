package org.commcare.personalId.profile

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.commcare.dalvik.R

/** Shows selected languages as chips; with [onRemove] each chip gets a close icon that removes it. */
object LanguageChips {
    fun render(
        group: ChipGroup,
        labels: List<String>,
        onRemove: ((index: Int) -> Unit)?,
    ) {
        group.removeAllViews()
        labels.forEachIndexed { index, label ->
            group.addView(
                Chip(group.context).apply {
                    text = label
                    isCheckable = false
                    isClickable = false
                    isCloseIconVisible = onRemove != null
                    closeIconContentDescription = context.getString(R.string.personalid_profile_languages_remove, label)
                    onRemove?.let { remove -> setOnCloseIconClickListener { remove(index) } }
                },
            )
        }
    }
}
