package org.commcare.connect.network.connect

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.ApiConnect
import org.commcare.connect.network.IApiCallback
import org.commcare.connect.network.NoParsingResponseParser
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.connect.parser.ConnectOpportunitiesParser
import org.commcare.connect.network.connect.parser.DeliveryAppProgressResponseParser
import org.commcare.connect.network.connect.parser.LearningAppProgressResponseParser

/**
 * Class for all connect api handlers
 */
open abstract class ConnectApiHandler<T> : BaseApiHandler<T>() {

    fun getConnectOpportunities(context: Context, user: ConnectUserRecord) {
        ApiConnect.getConnectOpportunities(
            context, user, createCallback(
                ConnectOpportunitiesParser<T>()
            )
        )
    }

    fun connectStartLearning(context: Context, user: ConnectUserRecord, jobId: Int) {
        ApiConnect.startLearnApp(
            context, user, jobId, createCallback(
                NoParsingResponseParser<T>()
            )
        )
    }

    fun getLearningAppProgress(context: Context, user: ConnectUserRecord, jobId: Int){
        ApiConnect.getLearningAppProgress(context,user,jobId,createCallback(
            LearningAppProgressResponseParser<T>(),jobId)
        )
    }

    fun claimJob(context: Context, user: ConnectUserRecord, jobId: Int) {
        ApiConnect.claimJob(
            context, user, jobId, createCallback(
                NoParsingResponseParser<T>()
            )
        )
    }

    fun getDeliveries(context: Context, user: ConnectUserRecord, job: ConnectJobRecord) {
        ApiConnect.getDeliveries(context,user,job.jobId,createCallback(
            DeliveryAppProgressResponseParser<T>(),job)
        )
    }

    fun setPaymentConfirmation(context: Context, user: ConnectUserRecord, paymentId: String,confirmation:Boolean) {
        ApiConnect.setPaymentConfirmed(
            context, user, paymentId,confirmation, createCallback(
                NoParsingResponseParser<T>()
            )
        )
    }

}