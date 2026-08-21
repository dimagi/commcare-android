package org.commcare.personalId.photo

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.commcare.activities.CommCareActivity
import org.commcare.activities.camera.MicroImageActivity
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.personalId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.utils.ConnectivityStatus
import org.commcare.views.dialogs.StandardAlertDialog

/**
 * Reusable PersonalID photo update flow: confirmation dialog -> camera capture -> upload ->
 * local persistence -> generic toast feedback. The callbacks only need to cover
 * consumer-specific UI state. Construct this before the [caller] reaches STARTED (e.g. in
 * onCreate) so the activity result registration survives configuration changes and process
 * death.
 */
class PersonalIdPhotoUpdater(
    private val activity: CommCareActivity<*>,
    caller: ActivityResultCaller,
    private val onSuccess: (photoBase64: String) -> Unit,
    private val onFailure: (PersonalIdOrConnectApiErrorCodes, Throwable?) -> Unit,
) {
    private val takePhotoLauncher: ActivityResultLauncher<Intent> =
        caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handlePhotoResult(result)
        }

    /** Starts the photo update flow by showing the update-photo confirmation dialog. */
    fun initiatePhotoUpdate() {
        val dialog =
            StandardAlertDialog(
                activity.getString(R.string.personalid_user_photo_update_dialog_title),
                activity.getString(R.string.personalid_user_photo_update_dialog_message),
            )

        dialog.setPositiveButton(
            activity.getString(R.string.personalid_user_photo_update_dialog_continue),
        ) { dialogInterface, _ ->
            if (!ConnectivityStatus.isNetworkAvailable(activity)) {
                val toastMessage = activity.getString(R.string.recovery_network_unavailable)
                Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG).show()
                return@setPositiveButton
            }

            dialogInterface.dismiss()
            launchCameraForPhotoEdit()
        }
        dialog.setNegativeButton(
            activity.getString(R.string.personalid_user_photo_update_dialog_cancel),
        ) { dialogInterface, _ ->
            dialogInterface.dismiss()
        }

        dialog.makeCancelable()
        dialog.showNonPersistentDialog(activity)
    }

    private fun launchCameraForPhotoEdit() {
        val intent =
            Intent(activity, MicroImageActivity::class.java).apply {
                putExtra(
                    MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA,
                    USER_PHOTO_MAX_DIMENSION_PX,
                )
                putExtra(
                    MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA,
                    USER_PHOTO_MAX_SIZE_BYTES,
                )
                putExtra(
                    MicroImageActivity.TITLE_RES_EXTRA,
                    R.string.micro_image_activity_title,
                )
            }
        takePhotoLauncher.launch(intent)
    }

    private fun handlePhotoResult(result: ActivityResult) {
        val photoBase64 =
            result.data?.getStringExtra(MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY) ?: return
        uploadUserPhoto(photoBase64)
    }

    private fun uploadUserPhoto(photoBase64: String) {
        val user = ConnectUserDatabaseUtil.getUser(activity)
        object : PersonalIdApiHandler<Boolean>() {
            override fun onSuccess(success: Boolean) {
                user.photo = photoBase64
                ConnectUserDatabaseUtil.storeUser(activity, user)
                val toastMessage = activity.getString(R.string.personalid_user_photo_update_success)
                Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG).show()
                this@PersonalIdPhotoUpdater.onSuccess(photoBase64)
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                val errorMessage = PersonalIdOrConnectApiErrorHandler.handle(activity, errorCode, t)
                Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show()
                this@PersonalIdPhotoUpdater.onFailure(errorCode, t)
            }
        }.updateProfile(activity, user.userId, user.password, null, null, photoBase64)
    }

    companion object {
        private const val USER_PHOTO_MAX_DIMENSION_PX = 160
        private const val USER_PHOTO_MAX_SIZE_BYTES = 100 * 1024 // 100 KB
    }
}
