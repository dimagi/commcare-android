package org.commcare.utils

import android.content.Context

object AppMetaDataUtil {

    fun provideAppMetaData(context: Context) = hashMapOf("application_id" to context.packageName)
}