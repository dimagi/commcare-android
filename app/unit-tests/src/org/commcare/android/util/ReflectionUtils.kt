package org.commcare.android.util

import java.lang.reflect.Field

/**
 * Reads private fields by name, walking up the class hierarchy.
 *
 * For state that production exposes no accessor for and no UI surface reflects. Anything observable
 * through a view, an intent or a public method should be asserted there instead.
 */
object ReflectionUtils {
    fun readField(
        target: Any,
        name: String,
    ): Any? = fieldFor(target.javaClass, name).get(target)

    private fun fieldFor(
        start: Class<*>,
        name: String,
    ): Field {
        var cls: Class<*>? = start
        while (cls != null) {
            try {
                return cls.getDeclaredField(name).apply { isAccessible = true }
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException("$name not found on ${start.name} or its superclasses")
    }
}
