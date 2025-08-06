package org.commcare.connect

/**
 * Interface for handling callbacks when a Connect activity finishes
 */
interface ConnectActivityCompleteListener {
    fun connectActivityComplete(success: Boolean)
}