package org.commcare.engine.resource

import org.commcare.android.resource.installers.MediaFileAndroidInstaller
import org.commcare.resources.model.MissingMediaException
import org.commcare.resources.model.Resource
import org.javarosa.core.reference.InvalidReferenceException
import org.javarosa.core.reference.ReferenceManager
import java.io.File
import java.io.IOException
import java.util.*

object AndroidResourceUtils {

    // loops over all lazy resources and checks if one of them has the same local path as the file uri
    @JvmStatic
    fun ifUriBelongsToALazyResource(problem: MissingMediaException, lazyResources: Vector<Resource>): Boolean {
        for (lazyResource in lazyResources) {
            if (matchFileUriToResource(lazyResource, problem.uri)) {
                return true
            }

        }
        return false
    }

    @JvmStatic
    fun matchFileUriToResource(lazyResource: Resource, uri: String?): Boolean {
        if (lazyResource.installer is MediaFileAndroidInstaller) {
            try {
                val resourceUri = (lazyResource.installer as MediaFileAndroidInstaller).localLocation
                val resourcePath = ReferenceManager.instance().DeriveReference(resourceUri).localURI
                val problemPath = ReferenceManager.instance().DeriveReference(uri).localURI
                if (File(resourcePath).canonicalPath.contentEquals(File(problemPath).canonicalPath)) {
                    return true
                }
            } catch (e: InvalidReferenceException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return false
    }
}