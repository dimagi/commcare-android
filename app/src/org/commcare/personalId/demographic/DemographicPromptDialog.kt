package org.commcare.personalId.demographic

import android.view.LayoutInflater
import androidx.core.view.isVisible
import org.commcare.activities.CommCareActivity
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.DialogDemographicPromptBinding
import org.commcare.views.dialogs.CustomViewAlertDialog

/**
 * The demographic profile prompt. Renders a [BannerStage] and reports which button was tapped; it
 * stores nothing itself.
 */
class DemographicPromptDialog private constructor(
    private val binding: DialogDemographicPromptBinding,
    private val stage: BannerStage,
    filledFieldCount: Int,
    private val onDecline: () -> Unit,
    private val onCompleteProfile: () -> Unit,
) : CustomViewAlertDialog(binding.root) {
    init {
        render(filledFieldCount)
        binding.demographicPromptDecline.setOnClickListener {
            dismiss()
            onDecline()
        }
        binding.demographicPromptComplete.setOnClickListener {
            dismiss()
            onCompleteProfile()
        }
    }

    private fun render(filledFieldCount: Int) {
        binding.demographicPromptProgress.setProgress(
            filledFieldCount * 100f / DemographicPromptState.PROFILE_FIELD_COUNT,
        )
        binding.demographicPromptWarning.isVisible = stage == BannerStage.WARNING
        binding.demographicPromptFinal.isVisible = stage == BannerStage.FINAL
        binding.demographicPromptDecline.setText(
            if (stage == BannerStage.FINAL) {
                R.string.personalid_demographic_banner_dismiss
            } else {
                R.string.personalid_demographic_banner_remind_later
            },
        )
    }

    companion object {
        fun show(
            activity: CommCareActivity<*>,
            stage: BannerStage,
            filledFieldCount: Int,
            onDecline: () -> Unit,
            onCompleteProfile: () -> Unit,
        ) {
            val binding = DialogDemographicPromptBinding.inflate(LayoutInflater.from(activity))
            activity.showAlertDialog(
                DemographicPromptDialog(binding, stage, filledFieldCount, onDecline, onCompleteProfile),
            )
        }
    }
}
