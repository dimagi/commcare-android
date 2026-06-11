package org.commcare.connect.network.connect

import android.content.Context
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.ApiConnect
import org.commcare.connect.network.NoParsingResponseParser
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.connect.models.ConnectPaymentConfirmationModel
import org.commcare.connect.network.connect.parser.ConnectOpportunitiesParser
import org.commcare.interfaces.base.BaseConnectView

/**
 * Class for all connect api handlers
 */
abstract class ConnectApiHandler<T>(
    loading: Boolean? = false,
    view: BaseConnectView? = null,
) : BaseApiHandler<T>(loading, view) {
    fun getConnectOpportunities(
        context: Context,
        user: ConnectUserRecord,
    ) {
        ApiConnect.getConnectOpportunities(
            context,
            user,
            createCallback(
                ConnectOpportunitiesParser<T>(),
                context,
            ),
        )
    }

    fun connectStartLearning(
        context: Context,
        user: ConnectUserRecord,
        jobUUID: String,
    ) {
        ApiConnect.startLearnApp(
            context,
            user,
            jobUUID,
            createCallback(
                NoParsingResponseParser<T>(),
            ),
        )
    }

    fun claimJob(
        context: Context,
        user: ConnectUserRecord,
        jobUUID: String,
    ) {
        ApiConnect.claimJob(
            context,
            user,
            jobUUID,
            createCallback(
                NoParsingResponseParser<T>(),
            ),
        )
    }

    fun setPaymentConfirmations(
        context: Context,
        user: ConnectUserRecord,
        paymentConfirmations: List<ConnectPaymentConfirmationModel>,
    ) {
        ApiConnect.setPaymentsConfirmed(
            context,
            user,
            paymentConfirmations,
            createCallback(
                NoParsingResponseParser(),
            ),
        )
    }
}
