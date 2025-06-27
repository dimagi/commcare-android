package org.commcare.activities.connect

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdCredentialBinding
import org.commcare.dalvik.databinding.ActivitySelectedPersonalIdCredentialBinding

class SelectedPersonalIdCredentialActivity : AppCompatActivity() {
    private val binding: ActivitySelectedPersonalIdCredentialBinding by lazy {
        ActivitySelectedPersonalIdCredentialBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setUiData()
    }

    private fun setUiData() {
        supportActionBar?.apply {
            title = getString(R.string.my_earned_credential)
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (!isFinishing) {
                    this.onBackPressed()
                }
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}