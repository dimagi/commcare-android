package org.commcare.pn.workers

import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes

data class PNApiResponseStatus(
    val success: Boolean,
    val retry: Boolean
)