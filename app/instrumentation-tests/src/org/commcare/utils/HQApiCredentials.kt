package org.commcare.utils

import androidx.test.platform.app.InstrumentationRegistry
import org.commcare.dalvik.test.BuildConfig

object HQApiCredentials {
    private const val USERNAME_ARGUMENT = "hqApiUsername"
    private const val PASSWORD_ARGUMENT = "hqApiPassword"

    @JvmStatic
    fun getUsername(): String = getArgumentOrDefault(USERNAME_ARGUMENT, BuildConfig.HQ_API_USERNAME)

    @JvmStatic
    fun getPassword(): String = getArgumentOrDefault(PASSWORD_ARGUMENT, BuildConfig.HQ_API_PASSWORD)

    private fun getArgumentOrDefault(
        argumentName: String,
        defaultValue: String,
    ): String =
        InstrumentationRegistry
            .getArguments()
            .getString(argumentName)
            ?.takeIf { it.isNotEmpty() }
            ?: defaultValue
}
