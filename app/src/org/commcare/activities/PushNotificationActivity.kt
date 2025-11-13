package org.commcare.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.commcare.activities.connect.viewmodel.PushNotificationViewModel
import org.commcare.adapters.PushNotificationAdapter
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPushNotificationBinding
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.CCAnalyticsParam
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.pn.helper.NotificationBroadcastHelper
import org.commcare.preferences.NotificationPrefs
import org.commcare.utils.FirebaseMessagingUtil.getIntentForPNClick

class PushNotificationActivity : AppCompatActivity() {
    private val binding: ActivityPushNotificationBinding by lazy {
        ActivityPushNotificationBinding.inflate(layoutInflater)
    }
    private lateinit var pushNotificationViewModel: PushNotificationViewModel
    private lateinit var pushNotificationAdapter: PushNotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initViews()
        observeRetrieveNotificationApi()
        registerForNewNotification()
        pushNotificationViewModel.loadNotifications(isRefreshed = false)
    }

    private fun observeRetrieveNotificationApi() {
        pushNotificationViewModel.allNotifications.observe(this) { notifications ->
            val isLoading = pushNotificationViewModel.isLoading.value == true
            NotificationPrefs.setNotificationAsRead(this)
            when {
                notifications.isNotEmpty() -> {
                    pushNotificationAdapter.submitList(notifications)
                    binding.rvNotifications.visibility = View.VISIBLE
                    binding.tvNoNotifications.visibility = View.GONE
                }

                isLoading -> {
                    binding.rvNotifications.visibility = View.GONE
                    binding.tvNoNotifications.visibility = View.GONE
                }

                else -> {
                    binding.rvNotifications.visibility = View.GONE
                    binding.tvNoNotifications.visibility = View.VISIBLE
                }
            }
        }

        pushNotificationViewModel.fetchApiError.observe(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        supportActionBar!!.apply {
            title = getString(R.string.personalid_notification)
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        pushNotificationViewModel =
            ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application),
            )[PushNotificationViewModel::class.java]
        pushNotificationAdapter =
            PushNotificationAdapter(
                listener =
                    object :
                        PushNotificationAdapter.OnNotificationClickListener {
                        override fun onNotificationClick(notificationRecord: PushNotificationRecord) {
                            FirebaseAnalyticsUtil.reportNotificationEvent(
                                AnalyticsParamValue.NOTIFICATION_EVENT_TYPE_CLICK,
                                AnalyticsParamValue.REPORT_NOTIFICATION_CLICK_NOTIFICATION_HISTORY,
                                notificationRecord.action,
                                notificationRecord.notificationId,
                            )
                            val activityIntent = getIntentForPNClick(application, notificationRecord)
                            if (activityIntent != null) {
                                startActivity(activityIntent)
                            }
                        }
                    },
            )
        binding.rvNotifications.adapter = pushNotificationAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_notification, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            R.id.notification_cloud_sync -> {
                pushNotificationViewModel.loadNotifications(isRefreshed = true)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    fun registerForNewNotification() {
        NotificationBroadcastHelper.registerForNotifications(this, this) {
            // Whenever new notification is received, signalling is calling retrieve_notifications api
            // so whenever this broadcast is received, new notification is already stored in local DB
            // that's the reason that isRefreshed = false is required
            pushNotificationViewModel.loadNotifications(false)
        }
    }
}
