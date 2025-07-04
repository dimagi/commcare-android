package org.commcare.activities.connect

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdCredentialDetailsBinding
import org.commcare.dalvik.databinding.CredentialShareViewBinding
import org.commcare.utils.CredentialShareData
import java.io.File
import java.io.FileOutputStream

class PersonalIdCredentialDetailActivity : AppCompatActivity() {
    private val binding: ActivityPersonalIdCredentialDetailsBinding by lazy {
        ActivityPersonalIdCredentialDetailsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setUiData()
        binding.tvShare.setOnClickListener {
            //As of now using dummy data
            val data = CredentialShareData(
                name = "William Fernandes Johnson Shane",
                userActivity = "Moderate",
                credentialTitle = "LEARN APP CREDENTIAL",
                issueDate = "28 Jun 2025",
                appName = "CommCareApp",
                imageUrl = "https://thumbs.dreamstime.com/z/vector-illustration-avatar-dummy-logo-collection-image-icon-stock-isolated-object-set-symbol-web-137160339.jpg?ct=jpeg"
            )

            createShareableView(this, data) { shareView ->
                val bitmap = getBitmapFromView(shareView)
                val file = saveBitmapToFile(this, bitmap)
                shareImageToWhatsApp(this, file)
            }
        }

    }

    private fun saveBitmapToFile(context: Context, bitmap: Bitmap): File {
        val file = File(context.cacheDir, "share_credential.png")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return file
    }

    private fun shareImageToWhatsApp(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.external.files.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            `package` = "com.whatsapp"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share Credential"))
    }


    private fun setUiData() {
        supportActionBar?.apply {
            title = getString(R.string.my_earned_credential)
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = createBitmap(view.measuredWidth, view.measuredHeight)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }


    private fun createShareableView(
        context: Context,
        data: CredentialShareData,
        onReady: (View) -> Unit
    ) {
        val binding = CredentialShareViewBinding.inflate(LayoutInflater.from(context))

        binding.tvNameButton.text = data.name
        binding.tvActionFor.text = context.getString(R.string.share_active_for, data.userActivity)
        binding.tvCredTitle.text = data.credentialTitle
        binding.tvEarnedOn.text = context.getString(R.string.share_earned_on, data.issueDate)
        binding.tvWorkedOn.text = context.getString(R.string.share_worked_on, data.appName)

        Glide.with(context)
            .asBitmap()
            .load(data.imageUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    binding.ivCredentialItem.setImageBitmap(resource)
                    onReady(binding.root)
                }

                override fun onLoadCleared(placeholder: Drawable?) {

                }
            })
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (!isFinishing) {
                    finish()
                }
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}