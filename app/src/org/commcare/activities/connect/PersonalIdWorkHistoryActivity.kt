package org.commcare.activities.connect

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayoutMediator
import org.commcare.activities.connect.viewmodel.PersonalIdWorkHistoryViewModel
import org.commcare.adapters.WorkHistoryViewPagerAdapter
import org.commcare.activities.CommonBaseActivity
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdWorkHistoryBinding


class PersonalIdWorkHistoryActivity : CommonBaseActivity() {
    private val binding: ActivityPersonalIdWorkHistoryBinding by lazy {
        ActivityPersonalIdWorkHistoryBinding.inflate(layoutInflater)
    }

    private lateinit var workHistoryViewPagerAdapter: WorkHistoryViewPagerAdapter
    private lateinit var personalIdWorkHistoryViewModel: PersonalIdWorkHistoryViewModel
    private var userName: String? = null
    private var profilePic: String? = null
    private val titles = listOf(R.string.personalid_work_history_earned, R.string.personalid_work_history_pending)
    private val icons = listOf(R.drawable.ic_personalid_work_history_earned, R.drawable.ic_personalid_work_history_pending)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        personalIdWorkHistoryViewModel = ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[PersonalIdWorkHistoryViewModel::class.java]
        userName = personalIdWorkHistoryViewModel.userName
        profilePic = personalIdWorkHistoryViewModel.profilePhoto
        workHistoryViewPagerAdapter = WorkHistoryViewPagerAdapter(this,userName!!,profilePic ?: "")
        observeWorkHistoryApiCall()
        fetchWorkHistoryFromNetwork()
        setUpUi()
    }

    private fun setUpUi() {
        supportActionBar!!.apply {
            title = getString(R.string.personalid_work_history_title)
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.vpWorkHistory.adapter = workHistoryViewPagerAdapter
        TabLayoutMediator(binding.tabWorkHistory, binding.vpWorkHistory) { tab, position ->
            tab.text = getString(titles[position])
            tab.setIcon(icons[position])
        }.attach()
    }

    private fun observeWorkHistoryApiCall() {
        personalIdWorkHistoryViewModel.apiError.observe(this) { (code, throwable) ->
            val errorMessage = PersonalIdApiErrorHandler.handle(this, code, throwable)
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchWorkHistoryFromNetwork() {
        personalIdWorkHistoryViewModel.retrieveAndProcessWorkHistory()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_work_history, menu)
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

            R.id.cloud_sync -> {
                fetchWorkHistoryFromNetwork()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
