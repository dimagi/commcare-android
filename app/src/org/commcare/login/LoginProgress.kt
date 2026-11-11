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

/** Caller-provided callback the engine emits [LoginProgress] on so the UI can reflect login phases. */
fun interface LoginProgressListener {
    fun onProgress(progress: LoginProgress)
}
