package sh.hnet.comfychair.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.json.JSONObject
import sh.hnet.comfychair.model.CommunityImage
import sh.hnet.comfychair.model.GenerationMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityImagesScreen(
    images: List<CommunityImage>,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onImportWorkflow: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedImage by remember { mutableStateOf<CommunityImage?>(null) }
    val gridState = rememberLazyGridState()

    // Detect when scrolled near bottom for pagination
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= images.size - 4 && hasMore && !isLoading) {
                    onLoadMore()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Model")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Community Images",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Grid of images
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(images, key = { it.id }) { image ->
                CommunityImageCard(
                    image = image,
                    onClick = { selectedImage = image }
                )
            }
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // Image detail sheet
    selectedImage?.let { image ->
        ImageDetailSheet(
            image = image,
            onDismiss = { selectedImage = null },
            onImportWorkflow = onImportWorkflow
        )
    }
}

@Composable
private fun CommunityImageCard(
    image: CommunityImage,
    onClick: () -> Unit
) {
    val aspectRatio = if (image.height > 0) {
        image.width.toFloat() / image.height.toFloat()
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image.thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Heart count overlay
        if (image.stats?.heartCount != null && image.stats.heartCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = formatCount(image.stats.heartCount),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageDetailSheet(
    image: CommunityImage,
    onDismiss: () -> Unit,
    onImportWorkflow: (String) -> Unit
) {
    val context = LocalContext.current
    var negativePromptExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Full-size image
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.url.replace("/original=true/", "/width=600/"))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Generation metadata
            image.meta?.let { meta ->
                Text(
                    text = "Generation Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Prompt
                meta.prompt?.let { prompt ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Prompt",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Negative prompt (collapsible)
                meta.negativePrompt?.let { negPrompt ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { negativePromptExpanded = !negativePromptExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Negative Prompt",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (negativePromptExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (negativePromptExpanded) "Collapse" else "Expand"
                                )
                            }
                            if (negativePromptExpanded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = negPrompt,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Parameters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    meta.sampler?.let { ParameterChip("Sampler", it) }
                    meta.steps?.let { ParameterChip("Steps", it.toString()) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    meta.cfgScale?.let { ParameterChip("CFG", it.toString()) }
                    meta.seed?.let { ParameterChip("Seed", it.toString()) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Resources (LoRAs, checkpoints)
                if (meta.resources.isNotEmpty()) {
                    Text(
                        text = "Resources",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    meta.resources.forEach { resource ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    resource.name?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    resource.type?.let {
                                        Text(
                                            text = it.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                resource.weight?.let {
                                    Text(
                                        text = "Weight: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Import workflow button
                Button(
                    onClick = {
                        val json = metadataToJson(meta)
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Workflow", json)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Workflow copied to clipboard", Toast.LENGTH_SHORT).show()
                        
                        // Also trigger import callback
                        onImportWorkflow(json)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Workflow")
                }
            }

            // If no metadata
            if (image.meta == null) {
                Text(
                    text = "No generation metadata available for this image.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ParameterChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
        count >= 1000 -> String.format("%.1fK", count / 1000.0)
        else -> count.toString()
    }
}

private fun metadataToJson(meta: GenerationMetadata): String {
    val json = JSONObject()
    meta.prompt?.let { json.put("prompt", it) }
    meta.negativePrompt?.let { json.put("negativePrompt", it) }
    meta.sampler?.let { json.put("sampler", it) }
    meta.steps?.let { json.put("steps", it) }
    meta.cfgScale?.let { json.put("cfgScale", it) }
    meta.seed?.let { json.put("seed", it) }
    return json.toString(2) // Pretty print with indent
}
