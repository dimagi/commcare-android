package org.commcare.connect.network.connect.models

import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord
import org.commcare.android.database.connect.models.ConnectJobLearningRecord

data class LearningAppProgressResponseModel (
    val connectJobLearningRecords: ArrayList<ConnectJobLearningRecord> = ArrayList(),
    val connectJobAssessmentRecords: ArrayList<ConnectJobAssessmentRecord> = ArrayList(),
)