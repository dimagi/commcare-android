package org.commcare.activities.camera

import android.Manifest
import android.os.Bundle
import android.util.Size
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.commcare.activities.CommonBaseActivity
import org.commcare.dalvik.R
import org.commcare.interfaces.RuntimePermissionRequester
import org.commcare.util.LogTypes
import org.commcare.utils.Permissions
import org.commcare.views.dialogs.DialogCreationHelpers
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localization
import java.util.concurrent.ExecutionException

abstract class BaseCameraActivity :
    CommonBaseActivity(),
    RuntimePermissionRequester {
    @JvmField
    protected var cameraView: PreviewView? = null

    private val cameraPermissionRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                logErrorAndExit("Error acquiring camera permission", "microimage.camera.permission.denied", null)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getContentLayout())
        cameraView = findViewById(R.id.view_finder)
        supportActionBar?.apply {
            setTitle(getTitleRes())
            setDisplayHomeAsUpEnabled(true)
        }
        onCameraViewReady()
        checkForCameraPermission()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkForCameraPermission() {
        if (Permissions.missingAppPermission(this, Manifest.permission.CAMERA)) {
            if (Permissions.shouldShowPermissionRationale(this, Manifest.permission.CAMERA)) {
                val dialog =
                    DialogCreationHelpers.buildPermissionRequestDialog(
                        this,
                        this,
                        -1, // actually not required due to launcher activity
                        getString(R.string.personalid_camera_permission_title),
                        getString(R.string.personalid_camera_permission_msg),
                    )
                dialog.showNonPersistentDialog(this)
            } else {
                requestNeededPermissions(-1)
            }
        } else {
            startCamera()
        }
    }

    override fun requestNeededPermissions(requestCode: Int) {
        cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA)
    }

    protected fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: ExecutionException) {
                logErrorAndExit("Error acquiring camera provider", "microimage.camera.start.failed", e)
                return@addListener
            } catch (e: InterruptedException) {
                logErrorAndExit("Error acquiring camera provider", "microimage.camera.start.failed", e)
                return@addListener
            }
            bindUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider) {
        val targetRotation = windowManager.defaultDisplay.rotation
        val targetResolution = getTargetResolution()

        val previewBuilder = Preview.Builder().setTargetRotation(targetRotation)
        targetResolution?.let { previewBuilder.setTargetResolution(it) }
        val preview = previewBuilder.build()
        preview.setSurfaceProvider(cameraView!!.surfaceProvider)

        val captureUseCase = buildCaptureUseCase(targetResolution, targetRotation)

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(this, getCameraSelector(), preview, captureUseCase)
    }

    protected fun logErrorAndExit(
        logMessage: String?,
        userMessageKey: String,
        e: Throwable?,
    ) {
        if (e == null) {
            Logger.log(LogTypes.TYPE_EXCEPTION, logMessage)
        } else {
            Logger.exception(logMessage, e)
        }
        Toast.makeText(this, Localization.get(userMessageKey), Toast.LENGTH_LONG).show()
        setResult(AppCompatActivity.RESULT_CANCELED)
        finish()
    }

    @LayoutRes
    protected abstract fun getContentLayout(): Int

    @StringRes
    protected abstract fun getTitleRes(): Int

    protected abstract fun getCameraSelector(): CameraSelector

    protected abstract fun getTargetResolution(): Size?

    protected abstract fun buildCaptureUseCase(
        targetResolution: Size?,
        targetRotation: Int,
    ): UseCase

    protected open fun onCameraViewReady() {}
}
