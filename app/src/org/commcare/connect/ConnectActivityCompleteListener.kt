package org.commcare.connect

/**
 * Interface for handling callbacks when a Connect activity finishes.
 *
 * A functional interface so Kotlin callers can pass a lambda; that is also why [error] carries no
 * default — Kotlin forbids one on a functional interface's abstract method.
 */
fun interface ConnectActivityCompleteListener {
    fun connectActivityComplete(
        success: Boolean,
        error: String?,
    )
}
