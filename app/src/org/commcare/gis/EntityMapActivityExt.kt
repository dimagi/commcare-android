package org.commcare.gis

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.lifecycle.lifecycleScope
import org.commcare.location.LocationHelper
import org.commcare.views.UserfacingErrorHandling
import org.javarosa.xpath.XPathException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun EntityMapActivity.loadMap(mapReadyTask: Task<GoogleMap>) {
    lifecycleScope.launch {
        showProgressDialog(EntityMapActivity.MAP_LOAD_TASK_ID)
        try {
            coroutineScope {
                val entitiesDeferred = async(Dispatchers.IO) { addEntityData() }
                val locationDeferred = async { LocationHelper.getCurrentLocationWithTimeout(this@loadMap) }
                val mapDeferred = async {
                    suspendCancellableCoroutine { cont ->
                        mapReadyTask.addOnSuccessListener { cont.resume(it) }
                        mapReadyTask.addOnFailureListener { cont.resumeWithException(it) }
                    }
                }

                val map = mapDeferred.await()
                entitiesDeferred.await()
                val location = locationDeferred.await()

                dismissProgressDialogForTask(EntityMapActivity.MAP_LOAD_TASK_ID)
                setupMap(map, location)
            }
        } catch (e: XPathException) {
            dismissProgressDialogForTask(EntityMapActivity.MAP_LOAD_TASK_ID)
            UserfacingErrorHandling<EntityMapActivity>().logErrorAndShowDialog(this@loadMap, e, true)
        }
    }
}
