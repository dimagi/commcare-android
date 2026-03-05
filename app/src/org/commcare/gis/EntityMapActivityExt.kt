package org.commcare.gis

import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.location.LocationHelper
import org.commcare.views.UserfacingErrorHandling
import org.javarosa.xpath.XPathException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun EntityMapActivity.loadMap(mapReadyTask: Task<GoogleMap>) {
    lifecycleScope.launch {
        showProgressDialog(EntityMapActivity.MAP_LOAD_TASK_ID)

        val entitiesDeferred = async(Dispatchers.IO) {
            try {
                addEntityData()
                null
            } catch (e: XPathException) {
                e
            }
        }
        val locationDeferred = async { LocationHelper.getCurrentLocationWithTimeout(this@loadMap) }
        val map = suspendCancellableCoroutine<GoogleMap> { cont ->
            mapReadyTask.addOnSuccessListener { cont.resume(it) }
            mapReadyTask.addOnFailureListener { cont.resumeWithException(it) }
        }

        val entityError = entitiesDeferred.await()
        val location = locationDeferred.await()

        dismissProgressDialogForTask(EntityMapActivity.MAP_LOAD_TASK_ID)
        setupMap(map, location)

        if (entityError != null) {
            UserfacingErrorHandling<EntityMapActivity>().logErrorAndShowDialog(
                this@loadMap,
                entityError,
                true
            )
        }
    }
}
