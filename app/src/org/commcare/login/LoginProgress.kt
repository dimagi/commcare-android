package org.commcare.login

data class LoginProgress(
    val phase: LoginPhase,
    val percent: Int? = null,
    val message: String? = null,
)

enum class LoginPhase {
    Seating,
    SigningIn,
    Syncing,
}

fun interface LoginProgressSink {
    fun onProgress(progress: LoginProgress)
}
