package org.commcare.utils

import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import org.commcare.tasks.templates.CoroutinesAsyncTask

/**
 * @author $|-|!˅@M
 */
fun mockAsyncTaskDispatchers() {
    mockkObject(CoroutinesAsyncTask)
    every { CoroutinesAsyncTask.parallelDispatcher() } returns Dispatchers.Main
    every { CoroutinesAsyncTask.serialDispatcher() } returns Dispatchers.Main
}
