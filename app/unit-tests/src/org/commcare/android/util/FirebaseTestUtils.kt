package org.commcare.android.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Firebase setup for Robolectric tests. Production code such as FirebaseAuthService calls
 * FirebaseAuth.getInstance(), which throws unless a default FirebaseApp exists in the process,
 * and Robolectric does not run the manifest initialization that provides one on a device.
 */
object FirebaseTestUtils {
    /** Initializes a default FirebaseApp with placeholder options unless one already exists. */
    fun initializeDefaultAppIfNeeded() {
        val context: Context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            return
        }
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions
                .Builder()
                .setApplicationId("1:1234567890:android:testappid")
                .setApiKey("test-api-key")
                .setProjectId("test-project")
                .build(),
        )
    }
}
