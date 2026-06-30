package org.commcare.personalId.profile

data class PersonalIdProfileDisplayModel(
    val name: String,
    val displayPhone: String,
    val email: String,
    val photoBase64: String?,
)
