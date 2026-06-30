package org.commcare.activities.camera

import android.Manifest
import android.os.Bundle
import android.util.Size
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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

/**
 * Base activity for CameraX-backed screens. Handles the camera permission flow, provider
 * acquisition, and lifecycle binding of a preview plus a capture use case.
 *
 * Subclasses supply the layout, title, camera selector, target resolution, and the
 * concrete capture use case (e.g. image capture or analysis).
 */
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
        cameraView = getCameraView()
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
                        getString(R.string.camera_permission_title),
                        getString(R.string.camera_permission_msg),
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
            if (isFinishing || isDestroyed) {
                return@addListener
            }
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindUseCases(cameraProvider)
            } catch (e: ExecutionException) {
                logErrorAndExit("Error acquiring camera provider", "microimage.camera.start.failed", e)
            } catch (e: InterruptedException) {
                logErrorAndExit("Error acquiring camera provider", "microimage.camera.start.failed", e)
            } catch (e: IllegalStateException) {
                logErrorAndExit("Error binding camera use cases", "microimage.camera.start.failed", e)
            } catch (e: IllegalArgumentException) {
                logErrorAndExit("Error binding camera use cases", "microimage.camera.start.failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider) {
        val targetRotation = ContextCompat.getDisplayOrDefault(this).rotation
        val targetResolution = getTargetResolution()

        val previewBuilder = Preview.Builder().setTargetRotation(targetRotation)
        targetResolution.let { previewBuilder.setResolutionSelector(buildResolutionSelector(it)) }
        val preview = previewBuilder.build()
        preview.surfaceProvider = cameraView!!.surfaceProvider

        val captureUseCase = buildCaptureUseCase(targetResolution, targetRotation)

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(this, getCameraSelector(), preview, captureUseCase)
    }

    /**
     * Builds a [ResolutionSelector] bound to [targetResolution]. The bound size is normalized to
     * the sensor's natural landscape orientation, since [ResolutionStrategy] matches against
     * sensor-oriented sizes rather than rotating to the target rotation like the deprecated
     * `setTargetResolution`.
     */
    protected fun buildResolutionSelector(targetResolution: Size): ResolutionSelector {
        val boundSize =
            if (targetResolution.height > targetResolution.width) {
                Size(targetResolution.height, targetResolution.width)
            } else {
                targetResolution
            }
        return ResolutionSelector
            .Builder()
            .setResolutionStrategy(
                ResolutionStrategy(boundSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            ).build()
    }

    protected fun logErrorAndExit(
        logMessage: String,
        userMessageKey: String,
        e: Throwable?,
    ) {
        if (e == null) {
            Logger.log(LogTypes.TYPE_EXCEPTION, logMessage)
        } else {
            Logger.exception(logMessage, e)
        }
        Toast.makeText(this, Localization.get(userMessageKey), Toast.LENGTH_LONG).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    @LayoutRes
    protected abstract fun getContentLayout(): Int

    @StringRes
    protected abstract fun getTitleRes(): Int

    protected abstract fun getCameraView(): PreviewView

    protected abstract fun getCameraSelector(): CameraSelector

    protected abstract fun getTargetResolution(): Size

    protected abstract fun buildCaptureUseCase(
        targetResolution: Size?,
        targetRotation: Int,
    ): UseCase

    protected open fun onCameraViewReady() {}
}
