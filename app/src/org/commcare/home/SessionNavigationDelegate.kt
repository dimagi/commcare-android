package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import org.commcare.CommCareApplication
import org.commcare.activities.EntityDetailActivity
import org.commcare.activities.EntitySelectActivity
import org.commcare.activities.EntitySelectActivity.EXTRA_ENTITY_KEY
import org.commcare.activities.MenuActivity
import org.commcare.activities.PostRequestActivity
import org.commcare.activities.QueryRequestActivity
import org.commcare.core.process.CommCareInstanceInitializer
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.models.AndroidSessionWrapper
import org.commcare.services.CommCareSessionService
import org.commcare.session.CommCareSession
import org.commcare.session.SessionFrame
import org.commcare.session.SessionNavigationResponder
import org.commcare.session.SessionNavigator
import org.commcare.suite.model.EntityDatum
import org.commcare.suite.model.Menu
import org.commcare.suite.model.RemoteRequestEntry
import org.commcare.util.DatumUtil
import org.commcare.utils.ApkDependenciesUtils
import org.commcare.utils.EntityDetailUtils
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.services.locale.Localization
import org.javarosa.xpath.XPathTypeMismatchException
import java.io.Serializable
import java.util.concurrent.locks.ReentrantLock

/**
 * Walks the CommCare session for a home activity: owns the [SessionNavigator], opens the screen
 * each navigation step asks for, and handles what the menu, case and remote-request screens hand
 * back.
 *
 * @param clearSessionAndExit resets the session after a callout comes back to a session that has
 * moved on, finishing the host when an external launch asked it to
 */
class SessionNavigationDelegate(
    private val host: HomeActivityHost,
    private val sessionLaunch: SessionLaunchDelegate,
    private val clearSessionAndExit: (AndroidSessionWrapper, Boolean) -> Unit,
) : SessionNavigationResponder {
    @get:VisibleForTesting
    val sessionNavigator = SessionNavigator(this)

    private var selectedEntityPostSync: String? = null

    fun startNextSessionStep() {
        sessionNavigator.startNextSessionStep()
    }

    /** Like [startNextSessionStep], but steps back with an error when a fixture fails to load. */
    fun startNextSessionStepSafe() {
        try {
            sessionNavigator.startNextSessionStep()
        } catch (e: CommCareInstanceInitializer.FixtureInitializationException) {
            sessionNavigator.stepBack()
            val message =
                if (host.isDemoUser()) Localization.get("demo.mode.feature.unavailable") else e.message.orEmpty()
            host.showErrorDialog(message, false)
        }
    }

    /**
     * Open the root module menu once the app's APK dependencies are installed; otherwise prompt for them.
     *
     * @return whether the menu opened
     */
    fun enterRootModule(): Boolean {
        if (!ApkDependenciesUtils.performDependencyCheckFlow(host.syncTaskActivity)) {
            return false
        }
        launchRootMenu()
        return true
    }

    /** Open the root module menu. */
    fun launchRootMenu() {
        val intent = Intent(host.hostContext, MenuActivity::class.java)
        addPendingDataExtra(intent, currentSessionWrapper().session)
        host.startActivityForResult(intent, HomeRequestCodes.GET_COMMAND)
    }

    /**
     * The calling screen invalidated the session, so rebuild its evaluation context and navigate
     * again, reselecting the entity it had selected.
     */
    fun restartAfterCallout(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ENTITY_KEY)?.let { selectedEntityPostSync = it }
        currentSessionWrapper().cleanVolatiles()
        startNextSessionStepSafe()
    }

    /**
     * Handle the user's return from a screen session navigation opened.
     *
     * @return whether the host should continue session navigation
     */
    fun handleCalloutResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ): Boolean =
        when (requestCode) {
            HomeRequestCodes.GET_COMMAND -> {
                when (resultCode) {
                    RESULT_CANCELED -> stepBackFromMenuOrCaseList()
                    RESULT_OK -> setCommandFromCallout(intent!!)
                    else -> true
                }
            }

            HomeRequestCodes.GET_CASE -> {
                when (resultCode) {
                    RESULT_CANCELED -> stepBackFromMenuOrCaseList()
                    RESULT_OK -> setCaseFromCallout(intent!!)
                    else -> false
                }
            }

            HomeRequestCodes.MAKE_REMOTE_POST -> {
                stepBackIfCancelled(resultCode)
                if (resultCode == RESULT_OK) {
                    currentSessionWrapper().terminateSession()
                }
                true
            }

            HomeRequestCodes.GET_REMOTE_DATA -> {
                stepBackIfCancelled(resultCode)
                true
            }

            else -> {
                true
            }
        }

    override fun processSessionResponse(statusCode: Int) {
        val asw = currentSessionWrapper()
        when (statusCode) {
            SessionNavigator.ASSERTION_FAILURE -> {
                showAssertionFailure(asw)
            }

            SessionNavigator.NO_CURRENT_FORM -> {
                terminateSessionWithoutForm(asw)
            }

            SessionNavigator.START_FORM_ENTRY -> {
                sessionLaunch.startForm(asw)
            }

            SessionNavigator.GET_COMMAND -> {
                launchMenu(asw)
            }

            SessionNavigator.START_ENTITY_SELECTION -> {
                launchEntitySelect(asw.session)
            }

            SessionNavigator.LAUNCH_CONFIRM_DETAIL -> {
                launchConfirmDetail(asw)
            }

            SessionNavigator.PROCESS_QUERY_REQUEST -> {
                launchQueryRequest()
            }

            SessionNavigator.START_SYNC_REQUEST -> {
                launchRemotePost(asw)
            }

            SessionNavigator.XPATH_EXCEPTION_THROWN -> {
                host.showXPathError(sessionNavigator.currentException, false)
                asw.reset()
            }

            SessionNavigator.REPORT_CASE_AUTOSELECT -> {
                FirebaseAnalyticsUtil.reportFeatureUsage(AnalyticsParamValue.FEATURE_CASE_AUTOSELECT)
            }

            SessionNavigator.FORM_ENTRY_ATTEMPT_DURING_SYNC -> {
                rejectFormEntryDuringSync()
            }
        }
    }

    override fun getSessionForNavigator(): CommCareSession = CommCareApplication.instance().currentSession

    override fun getEvalContextForNavigator(): EvaluationContext = currentSessionWrapper().evaluationContext

    override fun getBackgroundSyncLock(): ReentrantLock = CommCareSessionService.sessionAliveLock

    private fun stepBackFromMenuOrCaseList(): Boolean {
        val currentState = currentSessionWrapper()
        val currentCommand = currentState.session.command
        if (currentCommand == null || currentCommand == Menu.TRAINING_MENU_ROOT) {
            currentState.reset()
            host.refreshHostUi()
            return false
        }
        currentState.session.stepBack(currentState.evaluationContext)
        return true
    }

    private fun setCommandFromCallout(intent: Intent): Boolean {
        val currentState = currentSessionWrapper()
        val session = currentState.session
        if (!sessionStateUnchangedSinceCallout(session, intent)) {
            clearSessionAndExit(currentState, true)
            return false
        }
        session.setCommand(intent.getStringExtra(SessionFrame.STATE_COMMAND_ID))
        return true
    }

    private fun setCaseFromCallout(intent: Intent): Boolean {
        val currentState = currentSessionWrapper()
        val session = currentState.session
        if (!sessionStateUnchangedSinceCallout(session, intent)) {
            clearSessionAndExit(currentState, true)
            return false
        }
        session.setEntityDatum(session.neededDatum, intent.getStringExtra(SessionFrame.STATE_DATUM_VAL))
        return true
    }

    private fun stepBackIfCancelled(resultCode: Int) {
        if (resultCode == RESULT_CANCELED) {
            val asw = currentSessionWrapper()
            asw.session.stepBack(asw.evaluationContext)
        }
    }

    /**
     * Whether the data the session waits for is still what it was when the callout we are
     * returning from was made.
     */
    private fun sessionStateUnchangedSinceCallout(
        session: CommCareSession,
        intent: Intent,
    ): Boolean {
        val pendingSessionData = intent.getStringExtra(KEY_PENDING_SESSION_DATA)
        val neededDataUnchanged = pendingSessionData == session.getNeededData(currentSessionWrapper().evaluationContext)
        val pendingDatumId = intent.getStringExtra(KEY_PENDING_SESSION_DATUM_ID)
        val datumIdUnchanged = pendingDatumId == null || pendingDatumId == session.neededDatum.dataId
        return neededDataUnchanged && datumIdUnchanged
    }

    private fun showAssertionFailure(asw: AndroidSessionWrapper) {
        val evalContext = asw.evaluationContext
        val failure =
            asw.session.currentEntry.assertions
                .getAssertionFailure(evalContext)
        host.showAlertDialog(
            StandardAlertDialog.getBasicAlertDialogWithIcon(
                Localization.get("app.handled.error.title"),
                failure.evaluate(evalContext),
                android.R.drawable.ic_dialog_info,
            ) { dialog, _ ->
                dialog.dismiss()
                asw.session.stepBack(asw.evaluationContext)
                sessionNavigator.startNextSessionStep()
            },
        )
    }

    private fun terminateSessionWithoutForm(asw: AndroidSessionWrapper) {
        val terminated =
            try {
                asw.terminateSession()
            } catch (e: XPathTypeMismatchException) {
                host.showXPathError(e, true)
                return
            }
        if (terminated) {
            sessionNavigator.startNextSessionStep()
        } else {
            host.refreshHostUi()
        }
    }

    private fun rejectFormEntryDuringSync() {
        sessionNavigator.stepBack()
        sessionNavigator.startNextSessionStep()
        Toast
            .makeText(
                host.hostContext,
                Localization.get("background.sync.form.entry.attempt.during.sync"),
                Toast.LENGTH_LONG,
            ).show()
    }

    private fun launchMenu(asw: AndroidSessionWrapper) {
        val intent = Intent(host.hostContext, MenuActivity::class.java)
        intent.putExtra(SessionFrame.STATE_COMMAND_ID, asw.session.command)
        addPendingDataExtra(intent, asw.session)
        host.startActivityForResult(intent, HomeRequestCodes.GET_COMMAND)
    }

    private fun launchRemotePost(asw: AndroidSessionWrapper) {
        val commandEntry = CommCareApplication.instance().commCarePlatform.getEntry(asw.session.command)
        if (commandEntry !is RemoteRequestEntry) {
            clearSessionAndExit(asw, true)
            return
        }
        val postRequest = commandEntry.postRequest
        val intent = Intent(host.hostContext.applicationContext, PostRequestActivity::class.java)
        intent.putExtra(PostRequestActivity.URL_KEY, postRequest.url)
        intent.putExtra(
            PostRequestActivity.PARAMS_KEY,
            postRequest.getEvaluatedParams(asw.evaluationContext, false) as Serializable,
        )
        host.startActivityForResult(intent, HomeRequestCodes.MAKE_REMOTE_POST)
    }

    private fun launchQueryRequest() {
        val intent = Intent(host.hostContext.applicationContext, QueryRequestActivity::class.java)
        host.startActivityForResult(intent, HomeRequestCodes.GET_REMOTE_DATA)
    }

    private fun launchEntitySelect(session: CommCareSession) {
        host.startActivityForResult(buildEntitySelectIntent(session), HomeRequestCodes.GET_CASE)
    }

    private fun buildEntitySelectIntent(session: CommCareSession): Intent {
        val intent = Intent(host.hostContext.applicationContext, EntitySelectActivity::class.java)
        intent.putExtra(SessionFrame.STATE_COMMAND_ID, session.command)
        val lastPopped = session.poppedStep
        if (lastPopped != null && SessionFrame.STATE_DATUM_VAL == lastPopped.type) {
            intent.putExtra(EXTRA_ENTITY_KEY, lastPopped.value)
        }
        selectedEntityPostSync?.let {
            intent.putExtra(EXTRA_ENTITY_KEY, it)
            selectedEntityPostSync = null
        }
        addPendingDataExtra(intent, session)
        addPendingDatumIdExtra(intent, session)
        return intent
    }

    private fun launchConfirmDetail(asw: AndroidSessionWrapper) {
        val session = asw.session
        val entityDatum = session.neededDatum as? EntityDatum ?: return
        val contextRef = sessionNavigator.currentAutoSelection
        val context = host.hostContext
        val twoPaneLandscape =
            context.getString(R.string.panes) == "two" &&
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (twoPaneLandscape) {
            val intent = buildEntitySelectIntent(session)
            intent.putExtra(
                EXTRA_ENTITY_KEY,
                DatumUtil.getReturnValueFromSelection(contextRef, entityDatum, asw.evaluationContext),
            )
            host.startActivityForResult(intent, HomeRequestCodes.GET_CASE)
        } else {
            val intent = Intent(context.applicationContext, EntityDetailActivity::class.java)
            EntityDetailUtils.populateDetailIntent(intent, contextRef, entityDatum, asw)
            addPendingDataExtra(intent, session)
            addPendingDatumIdExtra(intent, session)
            host.startActivityForResult(intent, HomeRequestCodes.GET_CASE)
        }
    }

    private fun addPendingDataExtra(
        intent: Intent,
        session: CommCareSession,
    ) {
        intent.putExtra(KEY_PENDING_SESSION_DATA, session.getNeededData(currentSessionWrapper().evaluationContext))
    }

    private fun addPendingDatumIdExtra(
        intent: Intent,
        session: CommCareSession,
    ) {
        intent.putExtra(KEY_PENDING_SESSION_DATUM_ID, session.neededDatum.dataId)
    }

    private fun currentSessionWrapper(): AndroidSessionWrapper = CommCareApplication.instance().currentSessionWrapper

    private companion object {
        const val KEY_PENDING_SESSION_DATA = "pending-session-data-id"
        const val KEY_PENDING_SESSION_DATUM_ID = "pending-session-datum-id"
    }
}
