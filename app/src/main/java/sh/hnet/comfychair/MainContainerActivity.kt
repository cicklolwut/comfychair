package sh.hnet.comfychair

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import sh.hnet.comfychair.connection.ConnectionState
import kotlinx.coroutines.runBlocking
import sh.hnet.comfychair.cache.MediaCache
import sh.hnet.comfychair.cache.MediaStateHolder
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.navigation.MainRoute
import sh.hnet.comfychair.repository.GalleryRepository
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.ui.components.ConnectionAlertDialog
import sh.hnet.comfychair.ui.navigation.MainNavHost
import sh.hnet.comfychair.ui.theme.ComfyChairTheme
import sh.hnet.comfychair.util.DebugLogger
import sh.hnet.comfychair.viewmodel.GenerationViewModel
import sh.hnet.comfychair.viewmodel.ImageToImageViewModel
import sh.hnet.comfychair.viewmodel.ImageToVideoViewModel
import sh.hnet.comfychair.viewmodel.TextToImageViewModel
import sh.hnet.comfychair.viewmodel.TextToVideoViewModel

/**
 * Container activity that hosts the main navigation graph.
 * Uses Jetpack Compose for UI with bottom navigation.
 */
class MainContainerActivity : ComponentActivity() {

    // Constants
    companion object {
        const val RESULT_REFRESH_NEEDED = 100
    }

    // ViewModels
    private val generationViewModel: GenerationViewModel by viewModels()
    private val imageToImageViewModel: ImageToImageViewModel by viewModels()
    private val imageToVideoViewModel: ImageToVideoViewModel by viewModels()

    /** Whether we already tried silent refresh and it failed — show dialog instead. */
    private var silentRefreshFailed = false

    /** Handle re-auth result (shared by silent and manual flows). */
    private fun handleReAuthResult(resultCode: Int, data: Intent?, isSilent: Boolean) {
        val cookies = data?.getStringExtra(WebViewAuthActivity.EXTRA_COOKIES) ?: ""
        if (resultCode == Activity.RESULT_OK && cookies.isNotEmpty()) {
            // Success — update credentials + reconnect
            val newCreds = sh.hnet.comfychair.model.AuthCredentials.Cookie(cookies)
            ConnectionManager.clientOrNull?.setCredentials(newCreds)
            val serverId = ConnectionManager.currentServerId
            if (serverId != null) {
                sh.hnet.comfychair.storage.CredentialStorage(this)
                    .saveCredentials(serverId, newCreds)
            }
            ConnectionManager.clearSessionExpired()
            silentRefreshFailed = false
            ConnectionManager.attemptSilentReconnect()
        } else if (isSilent) {
            // Silent refresh failed — show dialog for manual re-auth
            silentRefreshFailed = true
        } else {
            // User cancelled manual re-auth — log out
            ConnectionManager.clearSessionExpired()
            silentRefreshFailed = false
            generationViewModel.logout()
            finish()
        }
    }

    // Silent re-auth — WebView opens, auto-finishes if cookies refresh
    private val silentReAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleReAuthResult(result.resultCode, result.data, isSilent = true) }

    // Manual re-auth — user-initiated from dialog
    private val manualReAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleReAuthResult(result.resultCode, result.data, isSilent = false) }

    /** Launch WebView for re-auth. */
    private fun launchReAuth(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val connState = ConnectionManager.connectionState.value
        if (connState is ConnectionState.Connected) {
            val serverUrl = buildServerUrl(connState.hostname, connState.port)
            val intent = WebViewAuthActivity.createIntent(this, serverUrl, connState.hostname)
            launcher.launch(intent)
        }
    }

    // Activity result launchers
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_REFRESH_NEEDED -> {
                // Settings cleared/restored or workflows changed - restart activity fresh
                val intent = Intent(this, MainContainerActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
            SettingsContainerActivity.RESULT_CONNECTION_CHANGED -> {
                // Connection settings changed - return to login screen
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }
        }
    }

    // Lifecycle methods
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Guard check - redirect to login if not connected (unless in offline mode)
        val isOfflineMode = AppSettings.isOfflineMode(this)
        if (!ConnectionManager.isConnected && !isOfflineMode) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            return
        }

        // Initialize GalleryRepository for offline cache access
        GalleryRepository.getInstance().initialize(this)

        // Enable debug logging based on saved preference (must be early to capture init logs)
        DebugLogger.setEnabled(AppSettings.isDebugLoggingEnabled(this))
        DebugLogger.i("MainContainer", "onCreate - debug logging enabled")

        // Initialize the ViewModel (uses ConnectionManager internally)
        generationViewModel.initialize(this)

        // Set current server ID for per-server media scoping
        val serverId = ConnectionManager.currentServerId
        DebugLogger.d("MainContainer", "setCurrentServerId: ${serverId?.take(8) ?: "NULL"}...")
        MediaStateHolder.setCurrentServerId(serverId)

        // Set caching mode based on user preference
        val isMemoryFirst = AppSettings.isMemoryFirstCache(this)
        DebugLogger.d("MainContainer", "caching mode: ${if (isMemoryFirst) "memory-first" else "disk-first"}")
        MediaStateHolder.setMemoryFirstMode(isMemoryFirst, applicationContext)
        MediaCache.setMemoryFirstMode(isMemoryFirst)

        // Initialize MediaCache with context for image/video fetching
        MediaCache.ensureInitialized(applicationContext)

        // Load saved media state before screens initialize
        if (isMemoryFirst) {
            // Memory-first: load everything from disk into memory
            DebugLogger.d("MainContainer", "loading media from disk (memory-first mode)")
            runBlocking {
                MediaStateHolder.loadFromDisk(applicationContext)
            }
        } else {
            // Disk-first: just discover video promptIds (bytes read on-demand)
            DebugLogger.d("MainContainer", "discovering video promptIds (disk-first mode)")
            MediaStateHolder.discoverVideoPromptIds(applicationContext)
        }

        // Determine start destination based on active generation owner
        val startDestination = when (generationViewModel.generationState.value.ownerId) {
            TextToImageViewModel.OWNER_ID -> MainRoute.TextToImage.route
            ImageToImageViewModel.OWNER_ID -> MainRoute.ImageToImage.route
            TextToVideoViewModel.OWNER_ID -> MainRoute.TextToVideo.route
            ImageToVideoViewModel.OWNER_ID -> MainRoute.ImageToVideo.route
            else -> MainRoute.TextToImage.route
        }

        setContent {
            ComfyChairTheme {
                // Observe connection alert state from ConnectionManager (single source of truth)
                val connectionAlertState by ConnectionManager.connectionAlertState.collectAsState()
                val isReconnecting by ConnectionManager.isReconnecting.collectAsState()

                Surface(modifier = Modifier.fillMaxSize()) {
                    MainNavHost(
                        generationViewModel = generationViewModel,
                        imageToImageViewModel = imageToImageViewModel,
                        imageToVideoViewModel = imageToVideoViewModel,
                        onNavigateToSettings = { openSettings() },
                        onNavigateToGallery = { openGallery() },
                        onLogout = { logout() },
                        startDestination = startDestination
                    )
                }

                // Session expired — try silent refresh first, prompt only if it fails
                val sessionExpired by ConnectionManager.sessionExpired.collectAsState()
                if (sessionExpired && !silentRefreshFailed) {
                    // Auto-launch silent WebView re-auth
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        launchReAuth(silentReAuthLauncher)
                    }
                }
                if (sessionExpired && silentRefreshFailed) {
                    // Silent refresh failed — show manual re-auth dialog
                    AlertDialog(
                        onDismissRequest = { /* don't dismiss by tapping outside */ },
                        title = { Text(stringResource(R.string.title_session_expired)) },
                        text = { Text(stringResource(R.string.message_session_expired)) },
                        confirmButton = {
                            Button(onClick = {
                                silentRefreshFailed = false
                                launchReAuth(manualReAuthLauncher)
                            }) {
                                Text(stringResource(R.string.button_reauthenticate))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                ConnectionManager.clearSessionExpired()
                                silentRefreshFailed = false
                                generationViewModel.logout()
                                finish()
                            }) {
                                Text(stringResource(R.string.button_logout))
                            }
                        }
                    )
                }

                // Show connection alert dialog when connection fails
                connectionAlertState?.let { state ->
                    ConnectionAlertDialog(
                        failureType = state.failureType,
                        hasOfflineCache = state.hasOfflineCache,
                        isReconnecting = isReconnecting,
                        onReconnect = {
                            ConnectionManager.retrySingleAttempt(this@MainContainerActivity)
                        },
                        onGoOffline = {
                            ConnectionManager.clearConnectionAlert()
                            AppSettings.setOfflineMode(this@MainContainerActivity, true)
                        },
                        onReturnToLogin = {
                            ConnectionManager.clearConnectionAlert()
                            generationViewModel.logout()
                            finish()
                        },
                        onDismiss = { ConnectionManager.clearConnectionAlert() }
                    )
                }
            }
        }
    }

    // Navigation helpers
    /**
     * Open the Settings activity
     */
    private fun openSettings() {
        val intent = Intent(this, SettingsContainerActivity::class.java)
        settingsLauncher.launch(intent)
    }

    /**
     * Open the Gallery activity
     */
    private fun openGallery() {
        val intent = Intent(this, GalleryContainerActivity::class.java)
        startActivity(intent)
    }

    /**
     * Log out from the server and return to MainActivity
     */
    private fun logout() {
        generationViewModel.logout()
        finish()
    }

    override fun onStop() {
        super.onStop()
        DebugLogger.d("MainContainer", "onStop - saving generation state")
        // Save generation state when going to background
        generationViewModel.saveGenerationState(this)

        // Persist all dirty media to disk synchronously to ensure completion before process death
        // Only persist in memory-first mode (disk-first writes immediately, no persistence needed)
        // Also skip if media cache is disabled
        val isMemoryFirst = AppSettings.isMemoryFirstCache(this)
        val isCacheDisabled = AppSettings.isMediaCacheDisabled(this)
        DebugLogger.d("MainContainer", "onStop - memoryFirst=$isMemoryFirst, cacheDisabled=$isCacheDisabled")
        if (isMemoryFirst && !isCacheDisabled) {
            DebugLogger.d("MainContainer", "onStop - persisting media to disk")
            runBlocking {
                MediaStateHolder.persistToDisk(applicationContext)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val isGenerating = generationViewModel.generationState.value.isGenerating

        // Only attempt reconnection if generating (need the connection)
        // Otherwise, connection will be established on-demand when user taps Generate
        if (!AppSettings.isOfflineMode(this) && isGenerating) {
            ConnectionManager.attemptSilentReconnect()
        }

        // Check if there's a pending generation that may have completed while in background
        if (isGenerating) {
            generationViewModel.checkServerForCompletion()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ViewModel handles cleanup automatically via onCleared()
    }

    private fun buildServerUrl(hostname: String, port: Int): String {
        return when (port) {
            443 -> "https://$hostname"
            80 -> "http://$hostname"
            8188 -> "http://$hostname:8188"
            else -> "https://$hostname:$port"
        }
    }
}
