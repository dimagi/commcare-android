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

    // loops over all lazy resources and checks if one of them has the same local path as {@param problem} URI
    @JvmStatic
    fun ifUriBelongsToALazyResource(problem: MissingMediaException, lazyResources: Vector<Resource>): Boolean {
        for (lazyResource in lazyResources) {
            if (matchFileUriToResource(lazyResource, problem.uri)) {
                return true
            }
        }
        return false
    }

    // checks if {@param resource} has same location as that represented by {@param uri}
    @JvmStatic
    fun matchFileUriToResource(resource: Resource, uri: String?): Boolean {
        if (resource.installer is MediaFileAndroidInstaller) {
            val resourceUri = (resource.installer as MediaFileAndroidInstaller).localLocation
            val resourcePath = ReferenceManager.instance().DeriveReference(resourceUri).localURI
            val problemPath = ReferenceManager.instance().DeriveReference(uri).localURI
            if (File(resourcePath).canonicalPath.contentEquals(File(problemPath).canonicalPath)) {
                return true
            }
        }
        return false
    }
}