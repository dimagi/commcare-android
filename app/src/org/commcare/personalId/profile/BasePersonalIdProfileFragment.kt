package org.commcare.personalId.profile

import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.PersonalidProfileHeaderBinding
import org.commcare.views.dialogs.StandardAlertDialog

/**
 * Shared functionality for the Manage Profile screens.
 */
abstract class BasePersonalIdProfileFragment : Fragment() {
    protected fun renderProfileHeader(
        header: PersonalidProfileHeaderBinding,
        displayModel: PersonalIdProfileDisplayModel,
    ) {
        header.profileName.text = displayModel.name
        header.profilePhoneSubtitle.text = displayModel.displayPhone
        loadUserPhoto(header.profileUserImage, displayModel.photoBase64)
    }

    protected fun loadUserPhoto(
        imageView: ImageView,
        photoBase64: String?,
    ) {
        Glide
            .with(imageView)
            .load(photoBase64)
            .apply(
                RequestOptions()
                    .placeholder(R.drawable.nav_drawer_person_avatar)
                    .error(R.drawable.nav_drawer_person_avatar),
            ).into(imageView)
    }

    protected fun showConfirmationDialog(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String,
        onPositive: () -> Unit,
    ) {
        val dialog = StandardAlertDialog(title, message)
        dialog.setPositiveButton(positiveText) { dialogInterface, _ ->
            dialogInterface.dismiss()
            onPositive()
        }
        dialog.setNegativeButton(negativeText) { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        dialog.makeCancelable()
        dialog.showNonPersistentDialog(requireActivity())
    }
}
