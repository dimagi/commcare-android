package org.commcare.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CredentialShareData(
    val name: String? = null,
    val imageUrl: String? = null,
    val uuid: String? = null,
    val appId: String? = null,
    val oppId: String? = null,
    val title: String? = null,
    val issuer: String? = null,
    val level: String? = null,
    val type: String? = null,
    val issuedDate: String? = null,
    val appName: String? = null,
) : Parcelable
