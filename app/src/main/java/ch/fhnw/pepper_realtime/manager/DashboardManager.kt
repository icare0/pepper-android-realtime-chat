package ch.fhnw.pepper_realtime.manager

import ch.fhnw.pepper_realtime.data.PerceptionData
import ch.fhnw.pepper_realtime.ui.DashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for perception dashboard overlay state.
 * Extracted from ChatViewModel for better separation of concerns.
 */
@Singleton
class DashboardManager @Inject constructor() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    // Callback for refreshing face list when dashboard opens
    private var onDashboardOpened: (() -> Unit)? = null

    /**
     * Set callback to be invoked when dashboard opens.
     * Used to trigger face list refresh.
     */
    fun setOnDashboardOpenedCallback(callback: () -> Unit) {
        onDashboardOpened = callback
    }

    // Latest perception snapshot, kept while the dashboard is hidden. Perception updates arrive
    // at up to 10 Hz and the dashboard state is collected by MainScreen, so emitting them while
    // hidden recomposed the whole screen continuously on Pepper's tablet.
    @Volatile private var latestHumans: List<PerceptionData.HumanInfo> = emptyList()
    @Volatile private var latestUpdate: String = ""

    fun showDashboard() {
        _state.update { it.copy(isVisible = true, isMonitoring = true, humans = latestHumans, lastUpdate = latestUpdate) }
        onDashboardOpened?.invoke()
    }

    fun hideDashboard() {
        _state.update { it.copy(isVisible = false, isMonitoring = false) }
    }

    fun toggleDashboard() {
        if (_state.value.isVisible) hideDashboard() else showDashboard()
    }

    fun updateDashboardHumans(humans: List<PerceptionData.HumanInfo>, timestamp: String) {
        latestHumans = humans
        latestUpdate = timestamp
        if (!_state.value.isVisible) return
        _state.update { it.copy(humans = humans, lastUpdate = timestamp) }
    }

    fun resetDashboard() {
        latestHumans = emptyList()
        latestUpdate = ""
        _state.value = DashboardState()
    }
}
