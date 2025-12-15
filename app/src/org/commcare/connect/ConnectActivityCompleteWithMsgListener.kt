package org.commcare.connect

interface ConnectActivityCompleteWithMsgListener {
    fun connectActivityCompleteWithMsg(success: Boolean, msg: String?)
}
