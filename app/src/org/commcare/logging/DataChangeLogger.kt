package org.commcare.logging

import android.content.Context
import android.os.Environment
import org.apache.commons.lang3.StringUtils
import org.commcare.android.logging.ReportingUtils
import org.commcare.util.LogTypes
import org.commcare.utils.CrashUtil
import org.commcare.utils.FileUtil
import org.javarosa.core.io.StreamsUtil
import org.javarosa.core.services.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 *  Used to log some crtitical platform events like DB migrations, Clear user data,
 *  app installations etc. All these logs are backed by file storage and are accessible
 *  from outside CommCare
 */
object DataChangeLogger {

    private const val PRIMARY_LOG_FILE_NAME = "CommCare Data Change Logs.txt"
    private const val SECONDRY_LOG_FILE_NAME = "CommCare Data Change Logs1.txt"
    private const val DATE_FORMAT = "d MMM yy HH:mm:ss z"
    private const val MAX_FILE_SIZE = (100 * 1000).toLong() // ~100KB

    private var primaryFile: File? = null
    private var secondryFile: File? = null

    @JvmStatic
    fun init(context: Context) {
        initLogFiles(context)
    }

    private fun initLogFiles(context: Context) {
        if (!isExternalStorageWritable()) {
            Logger.log(LogTypes.TYPE_ERROR_STORAGE, "External Storage unavialable to write logs");
            return
        }

        primaryFile = initFile(context, PRIMARY_LOG_FILE_NAME)
        if (primaryFile!!.exists() && primaryFile!!.length() > MAX_FILE_SIZE) {
            // When primary file gets full -> Delete secondry and move logs in primary to secondry
            secondryFile = initFile(context, SECONDRY_LOG_FILE_NAME)
            secondryFile!!.delete()
            try {
                FileUtil.copyFile(primaryFile, secondryFile)
                primaryFile!!.delete()
                primaryFile!!.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun initFile(context: Context, fileName: String): File {
        val file = File(context.getExternalFilesDir(null), fileName)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return file
    }

    /**
     * Logs a given message to the fileSystem
     */
    @JvmStatic
    fun log(dataChangeLogType: DataChangeLogType) {
        // Include this info as part of any crash reports
        CrashUtil.log(dataChangeLogType.message);

        // Write to local storage
        if (primaryFile != null && primaryFile!!.exists()) {
            try {
                val outputStream = FileOutputStream(primaryFile!!, true)
                outputStream.write(appendMetaData(dataChangeLogType).toByteArray())
                outputStream.flush()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Return the complete set of logs logged till now for
     * this installation
     */
    @JvmStatic
    fun getLogs(): String {
        return getLogs(secondryFile) + getLogs(primaryFile)
    }

    private fun appendMetaData(dataChangeLogType: DataChangeLogType): String {
        val sb = StringBuilder()

        sb.append(SimpleDateFormat(DATE_FORMAT).format(Date()))
        sb.append(" - ")
        sb.append(dataChangeLogType.message)

        val metaData = getMetaData(dataChangeLogType)
        for (key in metaData.keys) {
            val value = metaData[key]
            if (!StringUtils.isEmpty(value)) {
                sb.append(" [")
                sb.append(key)
                sb.append(": ")
                sb.append(value)
                sb.append("]")
            }
        }

        sb.append("\n\n")
        return sb.toString()
    }

    private fun getMetaData(dataChangeLogType: DataChangeLogType): Map<String, String> {
        val metaData = LinkedHashMap<String, String>()
        metaData.put("CommCare Version", ReportingUtils.getCommCareVersionString())

        // Add App Name
        val appName = when(dataChangeLogType) {
            is DataChangeLogType.CommCareAppUninstall -> dataChangeLogType.appName
            else -> ReportingUtils.getAppName()
        }
        metaData.put("App Name", appName)

        // Add App Version
        val appVersion = when(dataChangeLogType) {
            is DataChangeLogType.CommCareAppUninstall -> dataChangeLogType.appVersion
            else -> ReportingUtils.getAppVersion()
        }
        if (appVersion != -1) {
            metaData.put("App Version", appVersion.toString())
        }

        metaData.put("Username", ReportingUtils.getUser())
        return metaData
    }

    private fun getLogs(file: File?): String {
        if (file != null && file.exists()) {
            try {
                val inputStream = FileInputStream(file)
                return String(StreamsUtil.inputStreamToByteArray(inputStream))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return ""
    }

    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
}