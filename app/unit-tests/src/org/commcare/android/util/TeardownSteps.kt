package org.commcare.android.util

/**
 * Runs every teardown step even when an earlier one throws, then rethrows the first failure with
 * the rest suppressed, so a failed activity teardown can't leave static mocks registered for the
 * next test in the same JVM.
 */
fun runTeardownSteps(vararg steps: () -> Unit) {
    val errors = mutableListOf<Throwable>()
    steps.forEach { step ->
        try {
            step()
        } catch (t: Throwable) {
            errors.add(t)
        }
    }
    if (errors.isNotEmpty()) {
        val first = errors.first()
        errors.drop(1).forEach { first.addSuppressed(it) }
        throw first
    }
}
