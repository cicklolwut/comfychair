package sh.hnet.comfychair.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.hnet.comfychair.service.ComfyChairHelperService
import sh.hnet.comfychair.viewmodel.DownloadManagerViewModel
import sh.hnet.comfychair.viewmodel.DownloadManagerViewModel.Companion.ACTIVE_STATUSES

/**
 * Bottom sheet showing all current and recent downloads.
 * Wire into a ModalBottomSheet or ScaffoldWithBottomBar as needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerSheet(
    onDismiss: () -> Unit,
    viewModel: DownloadManagerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDownloads()
    }

    // Summary bar text
    val summaryText = state.summary?.let { s ->
        buildString {
            if (s.downloading > 0) append("${s.downloading} downloading  ")
            if (s.queued > 0) append("${s.queued} queued  ")
            if (s.done > 0) append("${s.done} done  ")
            if (s.error > 0) append("${s.error} failed  ")
            if (s.interrupted > 0) append("${s.interrupted} interrupted")
        }.trim().ifEmpty { "No downloads" }
    } ?: if (state.isLoading) "Loading…" else "No downloads"

    val hasFinished = state.downloads.any { it.status !in ACTIVE_STATUSES }

    Column(modifier = Modifier.fillMaxWidth()) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Downloads", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(summaryText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasFinished) {
                TextButton(onClick = { viewModel.clearFinished() }) {
                    Text("Clear finished")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        HorizontalDivider()

        if (state.isLoading && state.downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No downloads yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.downloads, key = { it.id }) { job ->
                    DownloadJobRow(
                        job = job,
                        onCancel = { viewModel.cancelDownload(job.id) },
                        onRemove = { viewModel.removeDownload(job.id) }
                    )
                }
            }
        }

        // Error snackbar area
        state.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
                }
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun DownloadJobRow(
    job: ComfyChairHelperService.DownloadJob,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val isActive = job.status in ACTIVE_STATUSES
    val isCancelling = job.status == "cancelling"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Icon(
                imageVector = when (job.status) {
                    "done"        -> Icons.Default.Check
                    "error"       -> Icons.Default.Error
                    "cancelled",
                    "interrupted" -> Icons.Default.Cancel
                    "queued"      -> Icons.Default.HourglassEmpty
                    "cancelling"  -> Icons.Default.Pause
                    else          -> Icons.Default.Download     // downloading
                },
                contentDescription = job.status,
                tint = when (job.status) {
                    "done"        -> MaterialTheme.colorScheme.primary
                    "error"       -> MaterialTheme.colorScheme.error
                    "cancelled",
                    "interrupted" -> MaterialTheme.colorScheme.onSurfaceVariant
                    else          -> MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp)
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.filename ?: job.id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                job.modelType?.let { type ->
                    Text(
                        text = type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action button
            when {
                isActive && !isCancelling -> {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                isCancelling -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Progress bar for active downloads
        if (job.status == "downloading" && job.bytesTotal > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (job.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(start = 28.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatBytes(job.bytesDone)} / ${formatBytes(job.bytesTotal)}  (${job.progress.toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp)
            )
        } else if (job.status == "queued") {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(start = 28.dp))
        }

        // Error message
        if (job.status == "error" && job.error != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = job.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 28.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.0f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}
