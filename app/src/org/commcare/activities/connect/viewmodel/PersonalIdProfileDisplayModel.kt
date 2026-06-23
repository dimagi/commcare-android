package org.commcare.activities.connect.viewmodel

data class PersonalIdProfileDisplayModel(
    val name: String,
    val displayPhone: String,
    val email: String,
    val photoBase64: String?,
)
