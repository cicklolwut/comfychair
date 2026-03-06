package sh.hnet.comfychair.ui.screens

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import sh.hnet.comfychair.ui.components.shared.NoOverscrollContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.model.CommunityImage
import sh.hnet.comfychair.model.GenerationMetadata
import sh.hnet.comfychair.ui.components.ImageViewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityImagesScreen(
    images: List<CommunityImage>,
    isLoading: Boolean,
    hasMore: Boolean,
    browseLevel: Int = 31,
    showAnimations: Boolean = false,
    currentSort: String = "Most Reactions",
    onSortChanged: (String) -> Unit = {},
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onImportWorkflow: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedImageIndex by remember { mutableIntStateOf(-1) }
    val gridState = rememberLazyGridState()

    // Filter images based on browse level (bitmask comparison)
    // Include images with nsfwLevel <= browseLevel
    val filteredImages = images.filter { image ->
        (image.nsfwLevel and browseLevel) == image.nsfwLevel
    }

    // Detect when scrolled near bottom for pagination
    // Key on filteredImages.size so it re-evaluates when new images arrive
    LaunchedEffect(gridState, filteredImages.size, hasMore, isLoading) {
        if (!hasMore || isLoading) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= filteredImages.size - 4) {
                    onLoadMore()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Grid of images (now takes full height — no header)
        NoOverscrollContainer(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredImages, key = { it.id }) { image ->
                CommunityImageCard(
                    image = image,
                    showAnimations = showAnimations,
                    onClick = { selectedImageIndex = filteredImages.indexOf(image) }
                )
            }

            // Loading indicator at bottom
            if (isLoading) {
                item(span = { GridItemSpan(2) }) {
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
        }
        } // NoOverscrollContainer

        // FAB column (bottom-right): sort + back
        var showSortSheet by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = { showSortSheet = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.FilterList, contentDescription = "Sort")
            }
            FloatingActionButton(
                onClick = onBack,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to model")
            }
        }

        // Sort bottom sheet
        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Sort Community Images",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val sortOptions = listOf("Most Reactions", "Most Comments", "Newest")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sortOptions.forEach { sort ->
                            FilterChip(
                                selected = currentSort == sort,
                                onClick = {
                                    onSortChanged(sort)
                                    showSortSheet = false
                                },
                                label = { Text(sort) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen image viewer
    if (selectedImageIndex >= 0) {
        CommunityImageViewer(
            images = filteredImages,
            initialIndex = selectedImageIndex,
            onDismiss = { selectedImageIndex = -1 },
            onImportWorkflow = onImportWorkflow
        )
    }
}

@Composable
private fun CommunityImageCard(
    image: CommunityImage,
    showAnimations: Boolean = false,
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
        val displayUrl = if (showAnimations) image.animatedThumbnailUrl else image.thumbnailUrl
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(displayUrl)
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
private fun CommunityImageViewer(
    images: List<CommunityImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onImportWorkflow: (String) -> Unit
) {
    val context = LocalContext.current
    var showMetadataSheet by remember { mutableStateOf(false) }
    var showPromptOverlay by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size }
    )
    
    // Sync pager with currentIndex
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> currentIndex = page }
    }

    // Handle back button
    BackHandler { onDismiss() }

    // Fullscreen black overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // HorizontalPager for swiping between images
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { images[it].id }
        ) { page ->
            val image = images[page]
            CommunityImagePage(
                image = image,
                onTap = { showPromptOverlay = !showPromptOverlay }
            )
        }
        
        // Semi-transparent prompt overlay at bottom (toggled by tap)
        if (showPromptOverlay) {
            val currentImage = images.getOrNull(currentIndex)
            currentImage?.meta?.prompt?.let { prompt ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
                        .padding(bottom = 48.dp) // space for FABs
                ) {
                    Text(
                        text = prompt,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        // Page indicator
        Text(
            text = "${currentIndex + 1} / ${images.size}",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )
        
        // FAB row at bottom-right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Save/download button
            val currentImage = images.getOrNull(currentIndex)
            FloatingActionButton(
                onClick = { 
                    currentImage?.let { image ->
                        // Download image using DownloadManager
                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val request = DownloadManager.Request(Uri.parse(image.url))
                            .setTitle("image_${image.id}")
                            .setDescription("Downloading from Civitai...")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ComfyChair/image_${image.id}.jpg")
                            .setAllowedOverMetered(true)
                            .setAllowedOverRoaming(true)
                        try {
                            downloadManager.enqueue(request)
                            Toast.makeText(context, "Downloading to Downloads/ComfyChair...", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Download, contentDescription = "Save image")
            }
            
            // Metadata/details button
            FloatingActionButton(
                onClick = { showMetadataSheet = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Info, contentDescription = "Generation info")
            }
            
            // Close button
            FloatingActionButton(
                onClick = onDismiss,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }

    // Full metadata bottom sheet
    if (showMetadataSheet) {
        val currentImage = images.getOrNull(currentIndex)
        ImageMetadataSheet(
            meta = currentImage?.meta,
            imageName = "image_${currentImage?.id}",
            onDismiss = { showMetadataSheet = false },
            onImportWorkflow = onImportWorkflow,
            context = context
        )
    }
}

@Composable
private fun CommunityImagePage(
    image: CommunityImage,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(image.id) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(image.id) { mutableStateOf(true) }
    
    // Load image via Coil, constrained to screen dimensions to prevent OOM.
    // Full-res Civitai images can be 3-4K+ (48MB+ as bitmap). Without a size cap,
    // multiple pages in the pager will exhaust available memory.
    LaunchedEffect(image.id) {
        android.util.Log.d("CommunityImage", "Loading image ${image.id}: ${image.url}")
        // Also write to file for persistence
        try {
            context.getExternalFilesDir(null)?.let { dir ->
                java.io.File(dir, "app_log.txt").appendText("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())} Loading image ${image.id}: ${image.url}\n")
            }
        } catch (_: Exception) {}
        isLoading = true
        val dm = context.resources.displayMetrics
        val request = ImageRequest.Builder(context)
            .data(image.url)
            .size(dm.widthPixels, dm.heightPixels)
            .build()
        try {
            val result = context.imageLoader.execute(request)
            bitmap = if (result is SuccessResult) {
                android.util.Log.d("CommunityImage", "Loaded image ${image.id} successfully")
                result.image.toBitmap()
            } else {
                android.util.Log.e("CommunityImage", "Failed to load image ${image.id}: $result")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CommunityImage", "Exception loading image ${image.id}", e)
            try {
                context.getExternalFilesDir(null)?.let { dir ->
                    val stack = e.stackTraceToString()
                    java.io.File(dir, "crash_log.txt").appendText("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())} Exception loading ${image.id}:\n$stack\n\n")
                }
            } catch (_: Exception) {}
        }
        isLoading = false
    }
    
    // Note: We don't manually recycle bitmaps - Coil manages its own memory cache.
    // Manual recycling causes crashes when the same image is viewed again.
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading || bitmap == null) {
            // Show thumbnail as placeholder while loading
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            ImageViewer(
                bitmap = bitmap!!,
                onSingleTap = onTap
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageMetadataSheet(
    meta: GenerationMetadata?,
    imageName: String?,
    onDismiss: () -> Unit,
    onImportWorkflow: (String) -> Unit,
    context: Context
) {
    var negativePromptExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Generation Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (meta == null) {
                // No metadata available
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No Metadata Available",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This image doesn't have generation parameters embedded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (imageName != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Image: $imageName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                return@Column
            }

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

            Spacer(modifier = Modifier.height(16.dp))
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
    meta.baseModel?.let { json.put("baseModel", it) }
    if (meta.resources.isNotEmpty()) {
        val resourcesArray = JSONArray()
        meta.resources.forEach { res ->
            val obj = JSONObject()
            res.name?.let { obj.put("name", it) }
            res.type?.let { obj.put("type", it) }
            res.weight?.let { obj.put("weight", it) }
            res.modelVersionId?.let { obj.put("modelVersionId", it) }
            resourcesArray.put(obj)
        }
        json.put("resources", resourcesArray)
    }
    return json.toString(2) // Pretty print with indent
}
