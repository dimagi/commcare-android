package org.commcare.android.database.connect.models

/**
 * Enum representing different types of personal ID credentials.
 *
 * @property typeName The string identifier used to match this enum with API or database values.
 */
enum class CredentialType(val typeName: String) {
    LEARN("LEARN"),
    DELIVER("DELIVER"),
    APP_ACTIVITY("APP_ACTIVITY"),
    UNKNOWN("UNKNOWN");

    companion object {
        /**
         * Maps a string value to the corresponding CredentialType.
         * If the input doesn't match any known type, returns UNKNOWN.
         *
         * @param type The raw string value to map.
         * @return Corresponding CredentialType or UNKNOWN.
         */
        fun from(type: String?): CredentialType {
            return entries.find { it.typeName.equals(type, ignoreCase = true) } ?: UNKNOWN
        }
    }
}