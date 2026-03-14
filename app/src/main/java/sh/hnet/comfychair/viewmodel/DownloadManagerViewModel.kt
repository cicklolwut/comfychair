package sh.hnet.comfychair.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.service.ComfyChairHelperService
import sh.hnet.comfychair.util.DebugLogger

private const val TAG = "DownloadManagerViewModel"
private const val POLL_INTERVAL_MS = 2_000L

data class DownloadManagerUiState(
    val downloads: List<ComfyChairHelperService.DownloadJob> = emptyList(),
    val summary: ComfyChairHelperService.DownloadSummary? = null,
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val errorMessage: String? = null
)

class DownloadManagerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadManagerUiState())
    val uiState: StateFlow<DownloadManagerUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    private val helperService = ComfyChairHelperService(
        serverUrlProvider = { ConnectionManager.client.getBaseUrl() ?: "" },
        credentialsProvider = { ConnectionManager.client.getCredentials() },
        onSessionExpired = { ConnectionManager.handleSessionExpired() }
    )

    // ---------------------------------------------------------------------------
    // Load
    // ---------------------------------------------------------------------------

    fun loadDownloads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = helperService.listDownloads()
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            downloads = result.jobs,
                            summary = result.summary,
                            isLoading = false
                        )
                    }
                    // Auto-start polling if there are active downloads
                    if (result.jobs.any { j -> j.status in ACTIVE_STATUSES }) {
                        startPolling()
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load downloads") }
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "loadDownloads failed: $e")
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Polling
    // ---------------------------------------------------------------------------

    fun startPolling() {
        if (pollJob?.isActive == true) return
        _uiState.update { it.copy(isPolling = true) }
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                try {
                    val result = helperService.listDownloads() ?: break
                    _uiState.update {
                        it.copy(downloads = result.jobs, summary = result.summary)
                    }
                    // Stop polling once nothing is active
                    if (result.jobs.none { j -> j.status in ACTIVE_STATUSES }) {
                        break
                    }
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Poll error: $e")
                    break
                }
            }
            _uiState.update { it.copy(isPolling = false) }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    // ---------------------------------------------------------------------------
    // Cancel
    // ---------------------------------------------------------------------------

    fun cancelDownload(jobId: String) {
        viewModelScope.launch {
            try {
                val ok = helperService.cancelDownload(jobId)
                if (ok) {
                    // Optimistically mark as cancelling
                    _uiState.update { state ->
                        state.copy(
                            downloads = state.downloads.map { job ->
                                if (job.id == jobId) job.copy(status = "cancelling") else job
                            }
                        )
                    }
                    // Keep polling so we catch the final 'cancelled' transition
                    startPolling()
                } else {
                    DebugLogger.w(TAG, "cancelDownload: server rejected cancel for $jobId")
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "cancelDownload error: $e")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Remove / Clear
    // ---------------------------------------------------------------------------

    fun removeDownload(jobId: String) {
        viewModelScope.launch {
            try {
                val ok = helperService.removeDownload(jobId)
                if (ok) {
                    _uiState.update { state ->
                        state.copy(downloads = state.downloads.filter { it.id != jobId })
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "removeDownload error: $e")
            }
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            try {
                val count = helperService.clearFinishedDownloads()
                if (count >= 0) {
                    DebugLogger.d(TAG, "clearFinished: removed $count jobs")
                    loadDownloads()
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "clearFinished error: $e")
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Dismiss error
    // ---------------------------------------------------------------------------

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ---------------------------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    companion object {
        val ACTIVE_STATUSES = setOf("queued", "downloading", "cancelling")
    }
}
