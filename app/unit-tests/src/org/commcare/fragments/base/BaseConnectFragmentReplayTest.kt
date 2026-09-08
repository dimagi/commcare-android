package org.commcare.fragments.base

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.repository.DataState
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.LoadingBinding
import org.commcare.fragments.RefreshableFragment
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * A fragment outlives its view, so its LiveData can still hold whatever it last emitted while
 * nothing was observing. Re-attaching the view replays that value as the first update the new
 * view sees, and [BaseConnectFragment.observeDataState] used to discard it — which left screens
 * that only render from a delivery, such as the opportunity list, completely blank.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class BaseConnectFragmentReplayTest {
    private lateinit var activity: HostActivity
    private val fragmentManager: FragmentManager get() = activity.supportFragmentManager

    @Before
    fun setUp() {
        activity =
            Robolectric
                .buildActivity(HostActivity::class.java)
                .create()
                .start()
                .resume()
                .get()
    }

    @Test
    fun `a state delivered while the view is alive reaches the consumer`() {
        val fragment = show()

        emit(fragment, DataState.Loading)
        emit(fragment, DataState.Success("first"))

        assertEquals(listOf("first"), fragment.delivered)
    }

    @Test
    fun `a success replayed to a re-created view is still delivered`() {
        val fragment = show()
        emit(fragment, DataState.Loading)
        emit(fragment, DataState.Success("first"))

        recreateView(fragment)

        assertEquals(
            "the replayed value must reach the consumer, or the screen renders nothing",
            listOf("first", "first"),
            fragment.delivered,
        )
    }

    @Test
    fun `a replayed success does not leave later states treated as replays`() {
        val fragment = show()
        emit(fragment, DataState.Success("first"))
        recreateView(fragment)

        emit(fragment, DataState.Success("second"))

        assertEquals(listOf("first", "first", "second"), fragment.delivered)
    }

    @Test
    fun `an error replayed to a re-created view is not surfaced`() {
        val fragment = show()
        emit(fragment, DataState.Loading)
        emit(fragment, DataState.Error())

        recreateView(fragment)

        assertEquals(emptyList<String>(), fragment.delivered)
        assertEquals(emptyList<String>(), fragment.cached)
    }

    // ---------- helpers ----------

    private fun show(): ProbeFragment {
        val fragment = ProbeFragment()
        fragmentManager.beginTransaction().add(CONTAINER_ID, fragment).commitNow()
        ShadowLooper.idleMainLooper()
        return fragment
    }

    private fun emit(
        fragment: ProbeFragment,
        state: DataState<String>,
    ) {
        activity.runOnUiThread { fragment.liveData.value = state }
        ShadowLooper.idleMainLooper()
    }

    /** Detach destroys the view and keeps the fragment; attach builds a new view on the same one. */
    private fun recreateView(fragment: ProbeFragment) {
        fragmentManager.beginTransaction().detach(fragment).commitNow()
        ShadowLooper.idleMainLooper()
        fragmentManager.beginTransaction().attach(fragment).commitNow()
        ShadowLooper.idleMainLooper()
    }

    class HostActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.ConnectTheme)
            super.onCreate(savedInstanceState)
            setContentView(FrameLayout(this).apply { id = CONTAINER_ID })
        }
    }

    class ProbeFragment :
        BaseConnectFragment<LoadingBinding>(),
        RefreshableFragment {
        val liveData = MutableLiveData<DataState<String>>()
        val cached = mutableListOf<String>()
        val delivered = mutableListOf<String>()

        override fun inflateBinding(
            inflater: LayoutInflater,
            container: ViewGroup?,
        ): LoadingBinding = LoadingBinding.inflate(inflater, container, false)

        override fun getEndpoint(): String? = null

        override fun refresh(forceRefresh: Boolean) = Unit

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)
            observeDataState(liveData, { cached += it }, { delivered += it })
        }
    }

    private companion object {
        const val CONTAINER_ID = 0x00ff0001
    }
}
