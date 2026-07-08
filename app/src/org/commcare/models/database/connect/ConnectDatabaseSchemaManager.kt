package org.commcare.models.database.connect

import org.commcare.android.database.connect.models.ConnectAppRecord
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord
import org.commcare.android.database.connect.models.ConnectJobDeliveryFlagRecord
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord
import org.commcare.android.database.connect.models.ConnectJobLearningRecord
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.models.database.DbUtil
import org.commcare.models.database.IDatabase
import org.commcare.modern.database.TableBuilder

object ConnectDatabaseSchemaManager {
    const val DB_NAME = "database_connect"

    /**
     * V.2  - Added ConnectJobRecord, ConnectAppInfo, and ConnectLearningModuleInfo tables
     * V.3  - Added date_claimed to ConnectJobRecord; reason to ConnectJobDeliveryRecord
     * V.4  - Added confirmed/confirmedDate to ConnectJobPaymentRecord; link offer info to ConnectLinkedAppRecord
     * V.5  - Added projectStartDate and isActive to ConnectJobRecord
     * V.6  - Added pin, secondaryPhoneVerified, and registrationDate to ConnectUserRecord
     * V.7  - Added ConnectPaymentUnitRecord table
     * V.8  - Added is_user_suspended to ConnectJobRecord
     * V.9  - Added using_local_passphrase to ConnectLinkedAppRecord
     * V.10 - Added last_accessed to ConnectLinkedAppRecord
     * V.11 - Added daily start and finish times to ConnectJobRecord
     * V.12 - Added ConnectMessagingChannelRecord and ConnectMessagingMessageRecord tables
     * V.13 - Added ConnectJobDeliveryFlagRecord table
     * V.14 - Added photo and isDemo to ConnectUserRecord
     * V.16 - Added personal_id_credential table
     * V.17 - Added has_connect_access to ConnectUserRecord
     * V.18 - Added new columns to personal_id_credential table
     * V.19 - Added push_notification_history table
     * V.20 - Added acknowledged to push_notification_history
     * V.21 - Added ConnectReleaseToggleRecord table
     * V.22 - Added UUID field to ConnectAppRecord, ConnectLearnModuleSummaryRecord,
     *         ConnectJobLearningRecord, ConnectJobDeliveryRecord, ConnectJobAssessmentRecord,
     *         ConnectPaymentUnitRecord, ConnectJobRecord, ConnectJobPaymentRecord, PushNotificationRecord
     * V.23 - Added slugUUID to ConnectJobDeliveryRecord
     * V.24 - Added key and opportunityStatus to PushNotificationRecord
     * V.25 - Added sessionEndpointId and requireAppSync to PushNotificationRecord
     * V.26 - Added email to ConnectUserRecord
     * V.27 - Added connect_tasks table (ConnectTaskRecord) for DB-persisted task tracking
     */
    const val DB_VERSION_CONNECT = 27

    @JvmStatic
    fun initializeSchema(database: IDatabase) {
        database.beginTransaction()
        try {
            database.execSQL(TableBuilder(ConnectUserRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectLinkedAppRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectJobRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectAppRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectLearnModuleSummaryRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectJobLearningRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectJobAssessmentRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectJobDeliveryRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectJobPaymentRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectPaymentUnitRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectMessagingChannelRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectMessagingMessageRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(ConnectJobDeliveryFlagRecord::class.java).tableCreateString)
            database.execSQL(TableBuilder(PersonalIdWorkHistory::class.java).tableCreateString)
            database.execSQL(TableBuilder(PushNotificationRecord::class.java).tableCreateString)
            database.execSQL(
                TableBuilder(ConnectReleaseToggleRecord::class.java)
                    .apply { setUnique(ConnectReleaseToggleRecord.META_SLUG) }
                    .tableCreateString,
            )
            database.execSQL(TableBuilder(ConnectTaskRecord::class.java).tableCreateString)
            DbUtil.createNumbersTable(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}
