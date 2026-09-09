package org.commcare.utils

object ProgressUtils {
    /**
     * Fraction of [current] out of [max] in the range 0f..1f, guarding against a non-positive
     * [max]. Callers that need a percentage should multiply the result by 100.
     */
    @JvmStatic
    fun calculateProgress(
        current: Int,
        max: Int,
    ): Float = if (max > 0) current.toFloat() / max else 0f
}
