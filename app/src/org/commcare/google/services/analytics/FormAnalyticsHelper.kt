package org.commcare.google.services.analytics

import java.io.File
import java.util.Date
import org.commcare.utils.FileUtil

class FormAnalyticsHelper {

    var videoStartTime: Long = -1
    var videoName: String = ""
    var videoDuration: Long = -1

    fun recordVideoPlaybackStart(videoFile: File) {
        videoStartTime = Date().time
        videoName = videoFile.name
        videoDuration = FileUtil.getDuration(videoFile)
    }

    fun resetVideoPlaybackInfo() {
        videoStartTime = -1
        videoName = ""
        videoDuration = -1
    }
}
