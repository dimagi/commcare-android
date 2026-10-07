package org.commcare.personalId

import android.app.Activity
import android.content.Context
import androidx.annotation.NavigationRes
import androidx.navigation.NavController
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBackUnconditionally
import org.commcare.personalId.NavGraphTitleTestSupport.assertToolbarTitle
import org.commcare.personalId.NavGraphTitleTestSupport.getFragmentToFragmentActions
import org.commcare.personalId.NavGraphTitleTestSupport.getResourceNameForId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.robolectric.shadows.ShadowLooper

abstract class BaseNavGraphToolbarTitleTest {
    @get:Rule
    val errors = ErrorCollector()

    protected val context: Context = ApplicationProvider.getApplicationContext()

    @get:NavigationRes
    protected abstract val graphRes: Int

    protected abstract val screens: Map<Int, TitledScreen>

    protected open val extraArrivalScreens: List<TitledScreen> = emptyList()

    protected abstract val currentActivity: Activity

    protected abstract val hostNavController: NavController

    protected abstract fun launchHostActivity()

    @Test
    fun `every fragment destination has an expected title`() {
        launchHostActivity()
        val fragmentIds =
            hostNavController.graph
                .filterNot { it is DialogFragmentNavigator.Destination }
                .map { it.id }
        assertEquals(
            fragmentIds.map { getResourceNameForId(context, it) }.toSet(),
            screens.keys.map { getResourceNameForId(context, it) }.toSet(),
        )
    }

    @Test
    fun `each fragment shows its title on arrival`() {
        (screens.values + extraArrivalScreens).forEach { screen ->
            val workflow = screen.args?.get("workflow")?.let { " $it" } ?: ""
            verifyCase("${getResourceNameForId(context, screen.destinationId)}$workflow") {
                launchHostActivity()
                navigateToScreen(screen.destinationId, screen)
                assertToolbarTitle(screen.titleRes)
            }
        }
    }

    @Test
    fun `pressing back from each forward navigation restores the visible screen's title`() {
        getFragmentToFragmentActions(context, graphRes).forEach { action ->
            val sourceScreen = screens.getValue(action.sourceId)
            val targetScreen = screens.getValue(action.destinationId)
            verifyCase("${getResourceNameForId(context, sourceScreen.destinationId)} -> ${getResourceNameForId(context, targetScreen.destinationId)}") {
                launchHostActivity()
                val startScreen = screens.getValue(hostNavController.graph.startDestinationId)
                val openedScreens = mutableListOf(startScreen)
                if (sourceScreen != startScreen) {
                    navigateToScreen(sourceScreen.destinationId, sourceScreen)
                    openedScreens += sourceScreen
                }
                navigateToScreen(action.actionId, targetScreen)
                openedScreens += targetScreen
                assertToolbarTitle(targetScreen.titleRes)

                pressBackUnconditionally()
                ShadowLooper.idleMainLooper()

                if (!currentActivity.isFinishing) {
                    val currentId = hostNavController.currentDestination!!.id
                    val visibleScreen =
                        openedScreens.lastOrNull { it.destinationId == currentId }
                            ?: throw AssertionError("Back landed on unexpected destination ${getResourceNameForId(context, currentId)}")
                    assertToolbarTitle(visibleScreen.titleRes)
                }
            }
        }
    }

    private fun navigateToScreen(
        resId: Int,
        screen: TitledScreen,
    ) {
        currentActivity.runOnUiThread { hostNavController.navigate(resId, screen.args) }
        ShadowLooper.idleMainLooper()
    }

    private fun verifyCase(
        case: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (t: Throwable) {
            errors.addError(AssertionError(case, t))
        }
    }
}
