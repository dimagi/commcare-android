package org.commcare.activities.connect

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import org.commcare.activities.connect.viewmodel.PersonalIdCredentialViewModel
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.adapters.PersonalIdCredentialAdapter
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ActivityPersonalIdCredentialBinding
import org.commcare.utils.CredentialShareData
import org.commcare.utils.MultipleAppsUtil
import org.commcare.utils.convertIsoDate
import org.commcare.utils.parseIsoDateForSorting

class PersonalIdCredentialActivity : AppCompatActivity() {
    private val binding: ActivityPersonalIdCredentialBinding by lazy {
        ActivityPersonalIdCredentialBinding.inflate(layoutInflater)
    }
    private lateinit var personalIdCredentialViewModel: PersonalIdCredentialViewModel
    private lateinit var earnedCredentialAdapter: PersonalIdCredentialAdapter
    private lateinit var yetToBeEarnedCredentialsAdapter: PersonalIdCredentialAdapter
    private var earnedCredentialCount = 0
    private var yetToBeEarnedCredentialCount = 0
    private var personalIdSessionData: PersonalIdSessionData? = null
    private var userName: String? = null
    private var profilePic: String? = null
    private var installedAppRecords: List<PersonalIdCredential> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        personalIdCredentialViewModel = ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[PersonalIdCredentialViewModel::class.java]
        personalIdSessionData =
            ViewModelProvider(this)[PersonalIdSessionDataViewModel::class.java].personalIdSessionData
        userName = personalIdSessionData!!.userName
        profilePic = personalIdSessionData?.photoBase64
        getInstalledAppsList()
        callCredentialApi()
        setUpUi()
        observeCredentialApiCall()
    }

    private fun getInstalledAppsList() {
        val usableApps = MultipleAppsUtil.getUsableAppRecords()

        installedAppRecords = usableApps.map { record ->
            PersonalIdCredential().apply {
                appId = record.applicationId
                title = record.displayName ?: ""
            }
        }
    }

    private fun setUpUi() {
        supportActionBar!!.apply {
            title = getString(R.string.my_worker_history)
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.tvEarnedCredentials.text = resources.getQuantityString(
            R.plurals.earned_credentials, earnedCredentialCount, earnedCredentialCount
        )
        binding.tvCredentialsYetToBeEarned.text = resources.getQuantityString(
            R.plurals.credentials_yet_to_be_earned,
            yetToBeEarnedCredentialCount,
            yetToBeEarnedCredentialCount
        )
    }

    private fun setUpEarnedRecyclerView() {
        earnedCredentialAdapter = PersonalIdCredentialAdapter(listener = object :
            PersonalIdCredentialAdapter.OnCredentialClickListener {
            override fun onViewCredentialClick(credential: PersonalIdCredential) {
                navigateToCredentialDetail(credential)
            }
        })
        binding.rvEarnedCredential.adapter = earnedCredentialAdapter
    }

    private fun setUpYetToBeEarnedRecyclerView() {
        yetToBeEarnedCredentialsAdapter = PersonalIdCredentialAdapter(listener = object :
            PersonalIdCredentialAdapter.OnCredentialClickListener {
            override fun onViewCredentialClick(credential: PersonalIdCredential) {
                navigateToCredentialDetail(credential)
            }
        })
        binding.rvYetToBeEarnedCredentials.adapter = yetToBeEarnedCredentialsAdapter
    }

    private fun navigateToCredentialDetail(credential: PersonalIdCredential) {
        val formattedIssuedDate: String = convertIsoDate(credential.issuedDate, "dd/MM/yyyy")

        val credentialShareData = CredentialShareData(
            name = userName!!,
            imageUrl = profilePic,
            type = credential.type,
            uuid = credential.uuid,
            appId = credential.appId,
            oppId = credential.oppId,
            title = when (credential.type) {
                "LEARN" -> getString(R.string.connect_certified_learner)
                "DELIVER" -> getString(R.string.connect_delivery_worker)
                "APP_ACTIVITY" -> getString(R.string.commcarehq_worker)
                else -> ""
            },
            issuer = credential.issuer,
            level = credential.level,
            issuedDate = formattedIssuedDate,
            appName = credential.title
        )
        startActivity(
            Intent(
                this, PersonalIdCredentialDetailActivity::class.java
            ).putExtra("CREDENTIAL_CLICKED_DATA", credentialShareData)
        )
    }

    private fun observeCredentialApiCall() {
        personalIdCredentialViewModel.credentialsLiveData.observe(this) { result ->
            val earnedCredentials = result.validCredentials

            setUpEarnedRecyclerView()
            updateEarnedCredentialAdapter(earnedCredentials)

            // Filter yet-to-be-earned by checking which installed app credentials are not in earned list
            val earnedAppIds = earnedCredentials.map { it.appId }.toSet()
            val yetToBeEarned = installedAppRecords.filter { it.appId !in earnedAppIds }

            setUpYetToBeEarnedRecyclerView()
            updateYetToBeEarnedCredentialAdapter(yetToBeEarned)
            updateUiCounts(earnedCredentials.size, yetToBeEarned.size)
        }

        personalIdCredentialViewModel.apiError.observe(this) { (code, throwable) ->
            PersonalIdApiErrorHandler.handle(this, code, throwable)
            Toast.makeText(
                this, throwable?.localizedMessage ?: "Something went wrong", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateUiCounts(earned: Int, yetToBeEarned: Int) {
        earnedCredentialCount = earned
        yetToBeEarnedCredentialCount = yetToBeEarned
        binding.tvEarnedCredentials.text = resources.getQuantityString(
            R.plurals.earned_credentials, earned, earned
        )
        binding.tvCredentialsYetToBeEarned.text = resources.getQuantityString(
            R.plurals.credentials_yet_to_be_earned, yetToBeEarned, yetToBeEarned
        )
    }

    private fun updateEarnedCredentialAdapter(data: List<PersonalIdCredential>) {
        val sortedData = data.sortedByDescending { parseIsoDateForSorting(it.issuedDate) }
        earnedCredentialAdapter.setData(sortedData)
    }

    private fun updateYetToBeEarnedCredentialAdapter(data: List<PersonalIdCredential>) {
        val sortedData = data.sortedByDescending { parseIsoDateForSorting(it.issuedDate) }
        yetToBeEarnedCredentialsAdapter.setData(sortedData)
    }

    private fun callCredentialApi() {
        personalIdCredentialViewModel.retrieveCredentials()
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
                callCredentialApi()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
