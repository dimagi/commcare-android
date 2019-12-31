package org.commcare.google.services.analytics

import org.commcare.utils.FileUtil
import java.io.File
import java.util.*

class FormAnalyticsHelper {

    var videoStartTime: Long = -1
    var videoName: String? = null
    var videoDuration: Long = -1

    fun recordVideoPlaybackStart(videoFile: File) {
        videoStartTime = Date().time
        videoName = videoFile.name
        videoDuration = FileUtil.getDuration(videoFile)
    }

    fun resetVideoPlaybackInfo() {
        videoStartTime = -1
        videoName = null
        videoDuration = -1
    }
}