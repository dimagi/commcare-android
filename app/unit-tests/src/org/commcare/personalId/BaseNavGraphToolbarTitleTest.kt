package org.commcare.personalId

import android.app.Activity
import android.content.Context
import androidx.annotation.NavigationRes
import androidx.navigation.NavController
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBackUnconditionally
import org.commcare.personalId.NavGraphTitleTestSupport.assertToolbarTitle
import org.commcare.personalId.NavGraphTitleTestSupport.describe
import org.commcare.personalId.NavGraphTitleTestSupport.fragmentToFragmentActions
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

    protected abstract fun launch()

    @Test
    fun `every fragment destination has an expected title`() {
        launch()
        val fragmentIds =
            hostNavController.graph
                .filterNot { it is DialogFragmentNavigator.Destination }
                .map { it.id }
        assertEquals(
            fragmentIds.map { describe(context, it) }.toSet(),
            screens.keys.map { describe(context, it) }.toSet(),
        )
    }

    @Test
    fun `each fragment shows its title on arrival`() {
        (screens.values + extraArrivalScreens).forEach { screen ->
            val workflow = screen.args?.get("workflow")?.let { " $it" } ?: ""
            check("${describe(context, screen.destinationId)}$workflow") {
                launch()
                navigate(screen.destinationId, screen)
                assertToolbarTitle(screen.titleRes)
            }
        }
    }

    @Test
    fun `pressing back from each forward navigation restores the visible screen's title`() {
        fragmentToFragmentActions(context, graphRes).forEach { action ->
            val source = screens.getValue(action.sourceId)
            val target = screens.getValue(action.destinationId)
            check("${describe(context, source.destinationId)} -> ${describe(context, target.destinationId)}") {
                launch()
                val start = screens.getValue(hostNavController.graph.startDestinationId)
                val opened = mutableListOf(start)
                if (source != start) {
                    navigate(source.destinationId, source)
                    opened += source
                }
                navigate(action.actionId, target)
                opened += target
                assertToolbarTitle(target.titleRes)

                pressBackUnconditionally()
                ShadowLooper.idleMainLooper()

                if (!currentActivity.isFinishing) {
                    val currentId = hostNavController.currentDestination!!.id
                    val visible =
                        opened.lastOrNull { it.destinationId == currentId }
                            ?: throw AssertionError("Back landed on unexpected destination ${describe(context, currentId)}")
                    assertToolbarTitle(visible.titleRes)
                }
            }
        }
    }

    private fun navigate(
        resId: Int,
        screen: TitledScreen,
    ) {
        currentActivity.runOnUiThread { hostNavController.navigate(resId, screen.args) }
        ShadowLooper.idleMainLooper()
    }

    private fun check(
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
