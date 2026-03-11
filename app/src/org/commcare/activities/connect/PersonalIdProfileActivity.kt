package org.commcare.activities.connect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalidProfileBinding
import org.commcare.fragments.MicroImageActivity
import org.commcare.views.dialogs.CustomProgressDialog

class PersonalIdProfileActivity : CommCareActivity<PersonalIdProfileActivity>() {
    private val binding by lazy {
        ActivityPersonalidProfileBinding.inflate(layoutInflater)
    }
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Intent>
    private lateinit var user: ConnectUserRecord
    private var previousPhoto: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val loadedUser = ConnectUserDatabaseUtil.getUser(this)
        if (loadedUser == null) {
            finish()
            return
        }
        user = loadedUser

        initTakePhotoLauncher()
        setupUi()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        supportActionBar?.title = getString(R.string.personalid_profile_title)
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.title = getString(R.string.personalid_profile_title)
    }

    private fun initTakePhotoLauncher() {
        takePhotoLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data
                        ?.getStringExtra(MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY)
                        ?.let { photoBase64 ->
                            previousPhoto = user.photo
                            displayPhoto(photoBase64)
                            uploadPhoto(photoBase64)
                        }
                }
            }
    }

    private fun setupUi() {
        binding.profileName.text = user.name
        loadCurrentPhoto()
        binding.photoActionButton.setOnClickListener { launchCamera() }
    }

    private fun loadCurrentPhoto() {
        val photo = user.photo
        if (!photo.isNullOrEmpty()) {
            loadPhotoWithGlide(photo)
            binding.photoActionButton.setText(R.string.personalid_profile_update_photo)
        } else {
            binding.photoActionButton.setText(R.string.personalid_profile_add_photo)
        }
    }

    private fun displayPhoto(photoBase64: String) {
        loadPhotoWithGlide(photoBase64)
        binding.photoActionButton.setText(R.string.personalid_profile_update_photo)
    }

    private fun loadPhotoWithGlide(photo: String) {
        Glide
            .with(this)
            .load(photo)
            .apply(
                RequestOptions
                    .circleCropTransform()
                    .placeholder(R.drawable.nav_drawer_person_avatar)
                    .error(R.drawable.nav_drawer_person_avatar),
            ).into(binding.profilePhoto)
    }

    private fun launchCamera() {
        val intent =
            Intent(this, MicroImageActivity::class.java).apply {
                putExtra(MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, PHOTO_MAX_DIMENSION_PX)
                putExtra(MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, PHOTO_MAX_SIZE_BYTES)
                putExtra(MicroImageActivity.MICRO_IMAGE_TITLE_EXTRA, getString(R.string.personalid_profile_capture_photo))
            }
        takePhotoLauncher.launch(intent)
    }

    private fun uploadPhoto(photoBase64: String) {
        showProgressDialog(TASK_UPDATE_PHOTO)
        binding.photoActionButton.isEnabled = false

        try {
            object : PersonalIdApiHandler<Void>() {
                override fun onSuccess(data: Void?) {
                    dismissProgressDialogForTask(TASK_UPDATE_PHOTO)
                    user.photo = photoBase64
                    ConnectUserDatabaseUtil.storeUser(this@PersonalIdProfileActivity, user)
                    binding.photoActionButton.isEnabled = true
                    Toast
                        .makeText(
                            this@PersonalIdProfileActivity,
                            R.string.personalid_profile_photo_updated,
                            Toast.LENGTH_SHORT,
                        ).show()
                }

                override fun onFailure(
                    errorCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?,
                ) {
                    dismissProgressDialogForTask(TASK_UPDATE_PHOTO)
                    revertPhoto()
                    binding.photoActionButton.isEnabled = true
                    val errorMessage =
                        PersonalIdOrConnectApiErrorHandler.handle(
                            this@PersonalIdProfileActivity,
                            errorCode,
                            t,
                        )
                    if (errorMessage.isNotEmpty()) {
                        Toast
                            .makeText(
                                this@PersonalIdProfileActivity,
                                errorMessage,
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                }
            }.updatePhoto(this, user.userId, user.password, photoBase64)
        } catch (e: Exception) {
            dismissProgressDialogForTask(TASK_UPDATE_PHOTO)
            revertPhoto()
            binding.photoActionButton.isEnabled = true
            Toast.makeText(this, R.string.personalid_profile_photo_update_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun revertPhoto() {
        val photo = previousPhoto
        if (!photo.isNullOrEmpty()) {
            loadPhotoWithGlide(photo)
            binding.photoActionButton.setText(R.string.personalid_profile_update_photo)
        } else {
            binding.profilePhoto.setImageResource(R.drawable.nav_drawer_person_avatar)
            binding.photoActionButton.setText(R.string.personalid_profile_add_photo)
        }
    }

    override fun generateProgressDialog(taskId: Int): CustomProgressDialog {
        val dialog = CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId)
        dialog.setCancelable()
        return dialog
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                if (!isFinishing) {
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        private const val TASK_UPDATE_PHOTO = 1
        private const val PHOTO_MAX_DIMENSION_PX = 160
        private const val PHOTO_MAX_SIZE_BYTES = 100 * 1024 // 100 KB
    }
}
