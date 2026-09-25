package org.commcare.fragments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.activities.CommCareWiFiDirectActivity
import org.commcare.util.LogTypes
import org.commcare.utils.closeQuietly
import org.javarosa.core.services.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ViewModel for the file server fragment. Owns the Wi-Fi Direct receive socket for the activity rather than the
 * fragment that starts it. The fragment is recreated on rotation, and a socket dying with it would leave port
 * 8988 bound. onCleared is the only place the port is released.
 *
 * @author avazirna
 */
class FileServerViewModel : ViewModel() {
    private val _statusText = MutableLiveData<String>()
    val statusText: LiveData<String> = _statusText

    private val pendingZips = ConcurrentLinkedQueue<String>()
    private val _receivedZipPaths = MutableLiveData<List<String>>(emptyList())
    val receivedZipPaths: LiveData<List<String>> = _receivedZipPaths

    @Volatile
    private var running = false

    @Volatile
    private var serverSocket: ServerSocket? = null

    val isRunning: Boolean
        get() = running

    fun onReceivedZipHandled(path: String) {
        pendingZips.remove(path)
        publishPendingZips()
    }

    private fun publishPendingZips() {
        _receivedZipPaths.postValue(pendingZips.toList())
    }

    fun startServer(receiveZipDirectory: String) {
        if (running) {
            Logger.log(TAG, "File server already running, keeping the existing socket")
            return
        }
        Logger.log(TAG, "File Server starting...")
        running = true
        _statusText.value = "Starting server"
        viewModelScope.launch(Dispatchers.IO) { serve(receiveZipDirectory) }
    }

    fun stopServer() {
        running = false
        serverSocket.closeQuietly()
    }

    private fun serve(receiveZipDirectory: String) {
        val socket =
            try {
                ServerSocket(PORT)
            } catch (e: IOException) {
                Logger.exception("Wi-fi direct file server could not bind port $PORT", e)
                _statusText.postValue("Could not start the file server, the port is in use.")
                running = false
                return
            }
        serverSocket = socket

        try {
            while (running) {
                _statusText.postValue("Ready to accept new file transfer.")
                val client =
                    try {
                        socket.accept()
                    } catch (e: IOException) {
                        if (running) {
                            Logger.exception("Wi-fi direct file server stopped accepting connections", e)
                            _statusText.postValue("File server stopped.")
                        }
                        break
                    }
                Logger.log(TAG, "Ready in wi-fi direct file server receive loop")
                receive(client, receiveZipDirectory)
            }
        } finally {
            running = false
            socket.closeQuietly()
            serverSocket = null
        }
    }

    private fun receive(
        client: Socket,
        receiveZipDirectory: String,
    ) {
        try {
            val f = File(receiveZipDirectory + System.currentTimeMillis() + ".zip")
            f.parentFile?.mkdirs()
            f.createNewFile()

            Logger.log(TAG, "server: copying files $f")
            CommCareWiFiDirectActivity.copyFile(client.getInputStream(), FileOutputStream(f))

            _statusText.postValue("copied files: ${f.absolutePath}")
            pendingZips.add(f.absolutePath)
            publishPendingZips()
        } catch (e: IOException) {
            val errorMessage = "File Server crashed after transfer with IO Exception: ${e.message}"
            Logger.exception(errorMessage, e)
            _statusText.postValue(errorMessage)
        } finally {
            client.closeQuietly()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopServer()
    }

    companion object {
        private const val TAG = LogTypes.TYPE_WIFI_DIRECT
        private const val PORT = 8988
    }
}
