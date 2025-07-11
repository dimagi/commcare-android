package org.commcare.activities.connect

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Base64
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
import org.commcare.utils.FileUtil
import org.commcare.utils.ImageType
import java.io.File
import java.io.IOException

class PersonalIdCredentialDetailActivity : AppCompatActivity() {
    private val binding: ActivityPersonalIdCredentialDetailsBinding by lazy {
        ActivityPersonalIdCredentialDetailsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val credentialData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("CREDENTIAL_CLICKED_DATA", CredentialShareData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("CREDENTIAL_CLICKED_DATA")
        }
        setUiData()
        binding.tvShare.setOnClickListener {
            if (credentialData != null) {
                createShareableView(credentialData) { shareView ->
                    val bitmap = getBitmapFromView(shareView)
                    val file = saveBitmapToFile(bitmap)
                    shareImage(file)
                }
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val file = File(cacheDir, "share_credential.png")

        try {
            FileUtil.writeBitmapToDiskAndCleanupHandles(bitmap, ImageType.PNG, file)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return file
    }

    private fun shareImage(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${this.packageName}.external.files.provider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share Credential via"))
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
        data: CredentialShareData,
        onReady: (View) -> Unit
    ) {
        val binding = CredentialShareViewBinding.inflate(LayoutInflater.from(this))

        binding.tvUserName.text = data.name
        binding.tvActionFor.text = getString(R.string.share_active_for, data.level)
        binding.tvCredTitle.text = data.title
        binding.tvEarnedOn.text = getString(R.string.share_earned_on, data.issuedDate)
        binding.tvWorkedOn.text = getString(R.string.share_worked_on, data.appName)
        binding.tvCredTitle.text = getString(R.string.share_worked_on, data.title)

        val imageBytes = Base64.decode(data.imageUrl!!.substringAfter("base64,"), Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        Glide.with(this)
            .asBitmap()
            .load(bitmap)
            .circleCrop()
            .placeholder(R.drawable.baseline_person_24)
            .error(R.drawable.baseline_person_24)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    binding.ivCredentialItem.setImageBitmap(resource)
                    onReady(binding.root)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    binding.ivCredentialItem.setImageResource(R.drawable.baseline_person_24)
                    onReady(binding.root)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    binding.ivCredentialItem.setImageDrawable(null)
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