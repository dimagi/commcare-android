package org.commcare.connect.network.connect.models

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord
import org.commcare.android.database.connect.models.ConnectJobLearningRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils

data class LearningAppProgressResponseModel(
    val connectJobLearningRecords: List<ConnectJobLearningRecord> = emptyList(),
    val connectJobAssessmentRecords: List<ConnectJobAssessmentRecord> = emptyList(),
)

fun LearningAppProgressResponseModel.applyToJob(
    job: ConnectJobRecord,
    context: Context,
) {
    job.learnings = connectJobLearningRecords
    job.learningModulesCompleted = connectJobLearningRecords.size
    job.assessments = connectJobAssessmentRecords
    ConnectJobUtils.updateJobLearnProgress(context, job)
}
