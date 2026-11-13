package org.commcare.connect

import android.view.Menu
import android.view.MenuInflater
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.home.HomeActionsDelegate
import org.javarosa.core.services.locale.Localization

/**
 * The opportunity app's actions in Opportunity Home's overflow menu: report an issue, update the
 * app or CommCare, change language, settings and advanced. Each item shows only while its action
 * is available.
 */
class OpportunityHomeAppActionsMenu(
    private val actions: HomeActionsDelegate,
) {
    private class Item(
        val id: Int,
        val titleKey: String,
        val analyticsParam: String,
        val isAvailable: () -> Boolean,
        val run: () -> Unit,
    )

    private val items =
        listOf(
            Item(
                R.id.action_report_problem,
                "home.report",
                AnalyticsParamValue.REPORT_PROBLEM,
                actions::canReportProblem,
                actions::reportProblem,
            ),
            Item(
                R.id.action_update,
                "home.menu.update",
                AnalyticsParamValue.ITEM_UPDATE_CC,
                actions::canUpdateApp,
                actions::updateApp,
            ),
            Item(
                R.id.action_update_commcare,
                "home.menu.update.commcare",
                AnalyticsParamValue.ITEM_UPDATE_CC_PLATFORM,
                actions::canUpdateCommCare,
                actions::updateCommCare,
            ),
            Item(
                R.id.action_change_language,
                "home.menu.locale.change",
                AnalyticsParamValue.ITEM_CHANGE_LANGUAGE,
                actions::canChangeLanguage,
                actions::changeLanguage,
            ),
            Item(
                R.id.action_preferences,
                "home.menu.settings",
                AnalyticsParamValue.ITEM_SETTINGS,
                actions::canOpenSettings,
                actions::openSettings,
            ),
            Item(
                R.id.action_advanced,
                "home.menu.advanced",
                AnalyticsParamValue.ITEM_ADVANCED_ACTIONS,
                actions::canOpenAdvanced,
                actions::openAdvanced,
            ),
        )

    fun inflate(
        menu: Menu,
        inflater: MenuInflater,
    ) = inflater.inflate(R.menu.menu_opportunity_home_app_actions, menu)

    /**
     * Show the available actions with their localized titles, or hide them all when [onOpportunityHome]
     * is false. Titles are read only for shown items, since the app's strings need a seated app.
     */
    fun prepare(
        menu: Menu,
        onOpportunityHome: Boolean,
    ) {
        for (item in items) {
            val menuItem = menu.findItem(item.id) ?: continue
            val visible = onOpportunityHome && item.isAvailable()
            menuItem.isVisible = visible
            if (visible) {
                menuItem.title = Localization.get(item.titleKey)
            }
        }
    }

    /** The analytics param for [itemId], or null when it isn't an app action. */
    fun analyticsParam(itemId: Int): String? = items.firstOrNull { it.id == itemId }?.analyticsParam

    /** Run the action for [itemId]; returns false when it isn't an app action. */
    fun onItemSelected(itemId: Int): Boolean {
        val item = items.firstOrNull { it.id == itemId } ?: return false
        item.run()
        return true
    }
}
