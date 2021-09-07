package org.commcare.utils

import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import org.commcare.tasks.templates.CoroutineAsyncTaskHelper

/**
 * @author $|-|!˅@M
 */
fun mockAsyncTaskDispatchers() {
    mockkObject(CoroutineAsyncTaskHelper)
    every { CoroutineAsyncTaskHelper.parallelDispatcher() } returns Dispatchers.Main
    every { CoroutineAsyncTaskHelper.serialDispatcher() } returns Dispatchers.Main
}
