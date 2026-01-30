package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date
import kotlin.math.ceil

@Table(ConnectJobRecord.STORAGE_KEY)
class ConnectJobRecordV21 : Persisted(), Serializable {

    @Persisting(1)
    @MetaField(ConnectJobRecord.META_JOB_ID)
    var jobId = 0

    @Persisting(2)
    @MetaField(ConnectJobRecord.META_NAME)
    var title: String =  ""

    @Persisting(3)
    @MetaField(ConnectJobRecord.META_DESCRIPTION)
    var description: String =  ""

    @Persisting(4)
    @MetaField(ConnectJobRecord.META_ORGANIZATION)
    var organization: String =  ""

    @Persisting(5)
    @MetaField(ConnectJobRecord.META_END_DATE)
    var projectEndDate: Date = Date()

    @Persisting(6)
    @MetaField(ConnectJobRecord.META_BUDGET_PER_VISIT)
    var budgetPerVisit = 0

    @Persisting(7)
    @MetaField(ConnectJobRecord.META_BUDGET_TOTAL)
    var totalBudget = 0

    @Persisting(8)
    @MetaField(ConnectJobRecord.META_MAX_VISITS_PER_USER)
    var maxVisits = 0

    @Persisting(9)
    @MetaField(ConnectJobRecord.META_MAX_DAILY_VISITS)
    var maxDailyVisits = 0

    @Persisting(10)
    @MetaField(ConnectJobRecord.META_DELIVERY_PROGRESS)
    var completedVisits = 0

    @Persisting(11)
    @MetaField(ConnectJobRecord.META_LAST_WORKED_DATE)
    var lastWorkedDate: Date =  Date()

    @Persisting(12)
    @MetaField(ConnectJobRecord.META_STATUS)
    var status = 0

    @Persisting(13)
    @MetaField(ConnectJobRecord.META_LEARN_MODULES)
    var numLearningModules = 0

    @Persisting(14)
    @MetaField(ConnectJobRecord.META_COMPLETED_MODULES)
    var learningModulesCompleted = 0

    @Persisting(15)
    @MetaField(ConnectJobRecord.META_CURRENCY)
    var currency: String =  ""

    @Persisting(16)
    @MetaField(ConnectJobRecord.META_ACCRUED)
    var paymentAccrued: String =  ""

    @Persisting(17)
    @MetaField(ConnectJobRecord.META_SHORT_DESCRIPTION)
    var shortDescription: String =  ""

    @Persisting(18)
    var lastUpdate: Date = Date()

    @Persisting(19)
    var lastLearnUpdate: Date =  Date()

    @Persisting(20)
    var lastDeliveryUpdate: Date =  Date()

    @Persisting(21)
    @MetaField(ConnectJobRecord.META_CLAIM_DATE)
    var dateClaimed: Date =  Date()

    @Persisting(22)
    @MetaField(ConnectJobRecord.META_START_DATE)
    var projectStartDate: Date =  Date()

    @Persisting(23)
    @MetaField(ConnectJobRecord.META_IS_ACTIVE)
    var isActive = false

    @Persisting(24)
    @MetaField(ConnectJobRecord.META_USER_SUSPENDED)
    var isUserSuspended = false

    @Persisting(25)
    @MetaField(ConnectJobRecord.META_DAILY_START_TIME)
    var dailyStartTime: String =  ""

    @Persisting(26)
    @MetaField(ConnectJobRecord.META_DAILY_FINISH_TIME)
    var dailyFinishTime: String =  ""


    fun getDaysRemaining(): Int {
        var startDate: Date? = Date()
        if (projectStartDate.after(startDate)) {
            startDate = projectStartDate
        }
        val millis = (projectEndDate.getTime() - (startDate)!!.getTime()).toDouble()
        //Ceiling means we'll get 0 within 24 hours of the end date
        //(since the end date has 00:00 time, but project is valid until midnight)
        val days = ceil(millis / 1000 / 3600 / 24).toInt()
        //Now plus 1 so we report i.e. 1 day remaining on the last day
        return if (days >= 0) (days + 1) else 0
    }

    companion object {

        const val STORAGE_KEY = ConnectJobRecord.STORAGE_KEY

        fun fromV21(oldRecord: ConnectJobRecordV21): ConnectJobRecord {
            val newRecord = ConnectJobRecord()

            newRecord.jobId = oldRecord.jobId
            newRecord.title = oldRecord.title
            newRecord.description = oldRecord.description
            newRecord.status = oldRecord.status
            newRecord.completedVisits = oldRecord.completedVisits
            newRecord.maxDailyVisits = oldRecord.maxDailyVisits
            newRecord.maxVisits = oldRecord.maxVisits
            newRecord.budgetPerVisit = (oldRecord.budgetPerVisit)
            newRecord.totalBudget = oldRecord.totalBudget
            newRecord.projectEndDate = oldRecord.projectEndDate
            newRecord.lastWorkedDate = oldRecord.lastWorkedDate
            newRecord.deliveries = ArrayList<ConnectJobDeliveryRecord>()
            newRecord.payments = ArrayList<ConnectJobPaymentRecord>()
            newRecord.learnings = ArrayList<ConnectJobLearningRecord>()
            newRecord.assessments = ArrayList<ConnectJobAssessmentRecord>()
            newRecord.paymentUnits = ArrayList<ConnectPaymentUnitRecord>()

            newRecord.organization = oldRecord.organization
            newRecord.numLearningModules = oldRecord.numLearningModules
            newRecord.learningModulesCompleted = oldRecord.learningModulesCompleted
            newRecord.currency = oldRecord.currency
            newRecord.setPaymentAccrued(oldRecord.paymentAccrued)
            newRecord.shortDescription = oldRecord.shortDescription
            newRecord.lastUpdate = oldRecord.lastUpdate
            newRecord.lastLearnUpdate = oldRecord.lastLearnUpdate
            newRecord.lastDeliveryUpdate = oldRecord.lastDeliveryUpdate
            newRecord.dateClaimed = oldRecord.dateClaimed
            newRecord.projectStartDate = oldRecord.projectStartDate
            newRecord.isActive = oldRecord.isActive
            newRecord.isUserSuspended = oldRecord.isUserSuspended
            newRecord.dailyStartTime = oldRecord.dailyStartTime
            newRecord.dailyFinishTime = oldRecord.dailyFinishTime
            newRecord.opportunityUUID = ""
            return newRecord
        }
    }
    
    
}