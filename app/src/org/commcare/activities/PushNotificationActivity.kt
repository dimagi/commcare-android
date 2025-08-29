package org.commcare.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPushNotificationBinding

class PushNotificationActivity : AppCompatActivity() {
    private val binding: ActivityPushNotificationBinding by lazy {
        ActivityPushNotificationBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setUpUi()
    }

    private fun setUpUi() {
        supportActionBar!!.apply {
            title = getString(R.string.personalid_notification)
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_notification, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (!isFinishing) {
                    finish()
                }
                true
            }

            R.id.notification_cloud_sync -> {
                //api call to sync notification
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}