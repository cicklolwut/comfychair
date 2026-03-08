package sh.hnet.comfychair.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import android.net.Uri
import android.text.Html
import android.widget.Toast
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.runtime.snapshotFlow
import coil3.imageLoader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import sh.hnet.comfychair.ui.components.shared.NoOverscrollContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.CompositionLocalProvider
import sh.hnet.comfychair.ui.components.AutoplayVideoThumbnail
import sh.hnet.comfychair.ui.components.SettingsScreenScaffold
import sh.hnet.comfychair.ui.components.VideoPlayer
import sh.hnet.comfychair.ui.components.VideoScaleMode
import sh.hnet.comfychair.model.CivitaiTypeMapper
import sh.hnet.comfychair.viewmodel.ModelBrowserUiState
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelFile
import sh.hnet.comfychair.model.ModelType
import sh.hnet.comfychair.model.ModelVersion
import sh.hnet.comfychair.viewmodel.ModelBrowserEvent
import sh.hnet.comfychair.viewmodel.ModelBrowserViewModel

private val NSFW_LEVELS = listOf(
    "PG" to 1,
    "PG-13" to 2,
    "R" to 4,
    "X" to 8,
    "XXX" to 16
)

private val BLUR_LEVELS = listOf(
    1 to "PG",
    2 to "PG-13",
    4 to "R",
    8 to "X",
    16 to "XXX",
    31 to "None"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBrowserScreen(
    viewModel: ModelBrowserViewModel,
    onNavigateToGeneration: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val isProviderConfigured = uiState.providerConfigured
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAnimationDialog by remember { mutableStateOf(false) }

    // Settings sheet state (don't update store until save)
    var settingsNsfwMax by remember { mutableStateOf(uiState.nsfwLevels.maxOrNull() ?: 2) }
    var settingsBlurThreshold by remember { mutableStateOf(uiState.blurThreshold) }
    var settingsShowAnimations by remember { mutableStateOf(uiState.showAnimations) }
    var settingsAutoplayVideos by remember { mutableStateOf(uiState.autoplayVideos) }
    var settingsApiKey by remember { mutableStateOf(uiState.apiKey) }
    var settingsCacheLimitMb by remember { mutableStateOf(viewModel.mediaCache.cacheLimitMb) }
    var settingsPrefetchEnabled by remember { mutableStateOf(viewModel.mediaCache.prefetchEnabled) }
    var settingsPrefetchCount by remember { mutableStateOf(viewModel.mediaCache.prefetchCount) }

    // Event handling
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ModelBrowserEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is ModelBrowserEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // UI
    SettingsScreenScaffold(
        title = "Model Browser",
        onNavigateToGeneration = onNavigateToGeneration,
        onLogout = onLogout,
        scrollable = false,
        horizontalPadding = 16.dp
    ) {
            // Provider selection row (left: chips, right: settings menu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedProvider == ModelProvider.CIVITAI,
                        onClick = { viewModel.selectProvider(ModelProvider.CIVITAI) },
                        label = { Text("Civitai") }
                    )
                    FilterChip(
                        selected = uiState.selectedProvider == ModelProvider.HUGGINGFACE,
                        onClick = { viewModel.selectProvider(ModelProvider.HUGGINGFACE) },
                        label = { Text("HuggingFace") }
                    )
                }

                // Settings button
                IconButton(onClick = { showSettingsSheet = true; settingsNsfwMax = uiState.nsfwLevels.maxOrNull() ?: 2; settingsBlurThreshold = uiState.blurThreshold; settingsShowAnimations = uiState.showAnimations; settingsAutoplayVideos = uiState.autoplayVideos; settingsApiKey = uiState.apiKey; settingsCacheLimitMb = viewModel.mediaCache.cacheLimitMb; settingsPrefetchEnabled = viewModel.mediaCache.prefetchEnabled; settingsPrefetchCount = viewModel.mediaCache.prefetchCount }) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isProviderConfigured) {
                // API key setup card
                ApiKeySetupCard(
                    provider = uiState.selectedProvider,
                    onKeySaved = { key -> viewModel.saveApiKey(key) }
                )
            } else {
                // Search bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search models...") },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.searchModels() }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.searchModels() }),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Wrap search results in Box for FAB overlay
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Loading indicator
                        if (uiState.isSearching) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        // Error message
                        uiState.errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // Search results - 2-column grid
                        val gridState = rememberLazyStaggeredGridState()

                        // Keys of grid items that are ≥33% visible — only these get autoplay.
                        // snapshotFlow + distinctUntilChanged avoids HashSet allocation on every
                        // scroll pixel; recomputes only when first/last visible index changes.
                        var autoplayKeys by remember { mutableStateOf(emptySet<Any>()) }

                        LaunchedEffect(gridState) {
                            snapshotFlow {
                                gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index to
                                    gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            }
                                .distinctUntilChanged()
                                .collect { (first, last) ->
                                    if (first == null || last == null) return@collect
                                    val info = gridState.layoutInfo
                                    val viewportStart = info.viewportStartOffset
                                    val viewportEnd = info.viewportEndOffset
                                    autoplayKeys = info.visibleItemsInfo
                                        .filter { item ->
                                            if (item.size.height == 0) return@filter false
                                            val itemTop = item.offset.y
                                            val itemBottom = itemTop + item.size.height
                                            val visibleTop = maxOf(itemTop, viewportStart)
                                            val visibleBottom = minOf(itemBottom, viewportEnd)
                                            val visibleHeight = maxOf(0, visibleBottom - visibleTop)
                                            visibleHeight.toFloat() / item.size.height >= 0.33f
                                        }
                                        .map { it.key }
                                        .toSet()
                                }
                        }

                        NoOverscrollContainer(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp
                        ) {
                            items(
                                items = uiState.searchResults,
                                key = { it.id },
                                contentType = { "model_card" }
                            ) { model ->
                                val onClick = remember(model.id) { { viewModel.selectModel(model) } }
                                ModelGridCard(
                                    model = model,
                                    filterType = uiState.filterModelType,
                                    filterBaseModel = uiState.filterBaseModel,
                                    showAnimations = uiState.showAnimations,
                                    autoplayVisible = uiState.autoplayVideos && autoplayKeys.contains(model.id),
                                    browseLevel = uiState.browseLevel,
                                    onClick = onClick
                                )
                            }
                            
                            // Loading indicator at bottom
                            if (uiState.isLoadingMore) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                        
                        // Scroll detection for infinite scroll
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { lastIndex ->
                                    if (lastIndex != null && lastIndex >= uiState.searchResults.size - 6
                                        && uiState.hasMoreResults && !uiState.isLoadingMore) {
                                        viewModel.loadMoreResults()
                                    }
                                }
                        }
                        
                        // Image prefetching for smooth scrolling.
                        // distinctUntilChanged() ensures we only fire when the last visible
                        // *row index* changes, not on every sub-pixel scroll frame.
                        // prefetched lives outside LaunchedEffect so it survives configuration
                        // changes (e.g. screen rotation) without resetting.
                        val imageLoader = context.imageLoader
                        val searchResults by rememberUpdatedState(uiState.searchResults)
                        val prefetched = remember { mutableSetOf<String>() }

                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                                .distinctUntilChanged()
                                .collect { lastVisible ->
                                    val currentResults = searchResults
                                    val prefetchEnd = minOf(lastVisible + 6, currentResults.size)
                                    for (i in (lastVisible + 1) until prefetchEnd) {
                                        currentResults.getOrNull(i)?.thumbnailUrl?.let { url ->
                                            if (url !in prefetched) {
                                                prefetched.add(url)
                                                imageLoader.enqueue(
                                                    ImageRequest.Builder(context)
                                                        .data(url)
                                                        .size(450, 675)
                                                        .build()
                                                )
                                            }
                                        }
                                    }
                                }
                        }
                        } // NoOverscrollContainer
                    }

                    // Filter FAB (bottom-right) - larger than SmallFAB, smaller than main generation FAB
                    FloatingActionButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                }
            }
    }

    // Animation confirmation dialog
    if (showAnimationDialog) {
        AlertDialog(
            onDismissRequest = { showAnimationDialog = false },
            title = { Text("Enable Animations") },
            text = { Text("Animated thumbnails (GIFs, animated WebP) will play in the grid. This may significantly increase bandwidth and memory usage.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setShowAnimations(true)
                    showAnimationDialog = false
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showAnimationDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Settings bottom sheet (half-height, save/cancel semantics)
    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title
                Text(
                    when (uiState.selectedProvider) {
                        ModelProvider.CIVITAI -> "Civitai Settings"
                        ModelProvider.HUGGINGFACE -> "HuggingFace Settings"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                HorizontalDivider()

                // NSFW Max Level (Civitai only) — controls model search results
                if (uiState.selectedProvider == ModelProvider.CIVITAI) {
                    Text("Model Search Level:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NSFW_LEVELS.forEach { (label, level) ->
                            FilterChip(
                                selected = settingsNsfwMax >= level,
                                onClick = { settingsNsfwMax = level },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Controls which models appear in search results. Image filtering is in the filter menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()
                }

                // Blur Threshold (Civitai only)
                if (uiState.selectedProvider == ModelProvider.CIVITAI) {
                    Text("Blur images above:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BLUR_LEVELS.forEach { (level, label) ->
                            FilterChip(
                                selected = settingsBlurThreshold == level,
                                onClick = { settingsBlurThreshold = level },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    HorizontalDivider()
                }

                // Animated Thumbnails (Civitai only)
                if (uiState.selectedProvider == ModelProvider.CIVITAI) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Animated Thumbnails", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = settingsShowAnimations,
                            onCheckedChange = { settingsShowAnimations = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Autoplay Videos", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "Auto-play video clips in grids (muted)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settingsAutoplayVideos,
                            onCheckedChange = { settingsAutoplayVideos = it }
                        )
                    }

                    HorizontalDivider()
                }

                // API Key
                Text(
                    when (uiState.selectedProvider) {
                        ModelProvider.CIVITAI -> "API Key:"
                        ModelProvider.HUGGINGFACE -> "API Token:"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = settingsApiKey,
                    onValueChange = { settingsApiKey = it },
                    placeholder = { Text("Enter key...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // --- Media Cache ---
                if (uiState.selectedProvider == ModelProvider.CIVITAI) {
                    HorizontalDivider()

                    Text("Media Cache", style = MaterialTheme.typography.labelMedium)

                    // Cache size limit
                    Text("Cache limit:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(100L, 250L, 500L, 1024L).forEach { mb ->
                            FilterChip(
                                selected = settingsCacheLimitMb == mb,
                                onClick = { settingsCacheLimitMb = mb },
                                label = {
                                    Text(
                                        if (mb >= 1024) "${mb / 1024}GB" else "${mb}MB",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Prefetch toggle + count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Prefetch videos", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = settingsPrefetchEnabled,
                            onCheckedChange = { settingsPrefetchEnabled = it }
                        )
                    }
                    if (settingsPrefetchEnabled) {
                        Text("Per page:", style = MaterialTheme.typography.bodySmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(3, 5, 10, 20).forEach { count ->
                                FilterChip(
                                    selected = settingsPrefetchCount == count,
                                    onClick = { settingsPrefetchCount = count },
                                    label = { Text("$count", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Cache usage + clear button
                    val cachedBytes = remember { viewModel.mediaCache.totalCachedBytes() }
                    val cachedCount = remember { viewModel.mediaCache.cachedCount() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${cachedCount} files · ${cachedBytes / (1024 * 1024)}MB used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { viewModel.clearMediaCache() }
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Save/Cancel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSettingsSheet = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            // Apply settings
                            viewModel.applySettings(
                                nsfwMax = settingsNsfwMax,
                                blurThreshold = settingsBlurThreshold,
                                showAnimations = settingsShowAnimations,
                                autoplayVideos = settingsAutoplayVideos,
                                apiKey = settingsApiKey
                            )
                            // Save cache settings
                            viewModel.mediaCache.cacheLimitMb = settingsCacheLimitMb
                            viewModel.mediaCache.prefetchEnabled = settingsPrefetchEnabled
                            viewModel.mediaCache.prefetchCount = settingsPrefetchCount
                            showSettingsSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 32.dp) // Bottom padding for nav gesture area
            ) {
                // Title
                Text(
                    "Search Settings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Browse Level (image NSFW filter) — individual toggles
                Text("Image Content Level:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val browseLevels = listOf(
                        1 to "PG",
                        2 to "PG-13",
                        4 to "R",
                        8 to "X",
                        16 to "XXX"
                    )
                    browseLevels.forEach { (bit, label) ->
                        val isSelected = (uiState.browseLevel and bit) != 0
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newLevel = if (isSelected) {
                                    uiState.browseLevel and bit.inv()
                                } else {
                                    uiState.browseLevel or bit
                                }
                                // Don't allow deselecting everything
                                if (newLevel != 0) viewModel.setBrowseLevel(newLevel)
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))
                
                // Filters (reuse existing SearchFilters composable)
                SearchFilters(
                    uiState = uiState,
                    onFilterTypeChanged = viewModel::setFilterModelType,
                    onFilterBaseModelChanged = viewModel::setFilterBaseModel,
                    onFilterSortChanged = viewModel::setFilterSort,
                    onFilterPeriodChanged = viewModel::setFilterPeriod
                )
            }
        }
    }

    // Model detail bottom sheet
    if (uiState.selectedModel != null) {
        ModelDetailBottomSheet(
            model = uiState.selectedModel!!,
            uiState = uiState,
            nsfwLevels = uiState.nsfwLevels,
            onDismiss = { viewModel.clearSelection() },
            onSelectVersion = viewModel::selectVersion,
            onToggleCommunityImages = viewModel::toggleCommunityImages,
            onLoadMoreCommunityImages = viewModel::loadMoreCommunityImages,
            onSortCommunityImages = viewModel::updateCommunitySort,
            onSelectModelType = viewModel::selectModelType,
            onSelectFile = viewModel::selectFile,
            onDownload = viewModel::downloadModel,
            onImportWorkflow = viewModel::importWorkflow,
            onTypeFilterChanged = viewModel::updateCommunityTypeFilter,
            onMetaOnlyChanged = viewModel::setCommunityMetaOnly,
            onFeaturedFirstChanged = viewModel::setCommunityFeaturedFirst,
            onGroupByPostChanged = viewModel::setCommunityGroupByPost,
            onBrowseLevelChanged = { viewModel.updateBrowseLevel(it) },
            onCommunityFiltersApplied = viewModel::reloadCommunityImages,
            onFetchMetadata = { imageId, postId, callback -> viewModel.fetchImageMetadata(imageId, postId, callback) }
        )
    }
}

@Composable
fun ApiKeySetupCard(
    provider: ModelProvider,
    onKeySaved: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }

    val providerName = when (provider) {
        ModelProvider.CIVITAI -> "Civitai"
        ModelProvider.HUGGINGFACE -> "HuggingFace"
    }

    val helpText = when (provider) {
        ModelProvider.CIVITAI -> "Get your API key from civitai.com → Settings → API Keys"
        ModelProvider.HUGGINGFACE -> "Get your token from huggingface.co → Settings → Access Tokens"
    }

    // Reset field when switching providers
    LaunchedEffect(provider) {
        apiKey = ""
        keyVisible = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$providerName API Key Required",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "Hide" else "Show"
                        )
                    }
                }
            )

            Button(
                onClick = { onKeySaved(apiKey.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank()
            ) {
                Text("Save & Continue")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelGridCard(
    model: ModelSearchResult,
    filterType: String?,
    filterBaseModel: String?,
    showAnimations: Boolean = false,
    autoplayVisible: Boolean = false,
    browseLevel: Int = 31,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isVideoCover = model.coverImageType == "video"
    // For video covers, never use animatedThumbnailUrl — it returns video/mp4 which Coil can't decode.
    // Video playback is handled separately via coverVideoUrl + ExoPlayer.
    val displayUrl = if (showAnimations && !isVideoCover) model.animatedThumbnailUrl ?: model.thumbnailUrl else model.thumbnailUrl
    val coverAllowed = (model.coverImageNsfwLevel and browseLevel) == model.coverImageNsfwLevel
    // Cache the ImageRequest — only rebuild when the URL changes, not on every recomposition
    // (autoplayKeys state changes during scroll would otherwise rebuild it on every frame).
    val coverImageRequest = remember(displayUrl) {
        ImageRequest.Builder(context)
            .data(displayUrl)
            .crossfade(150) // Shorter crossfade: 300ms default causes visible flicker during fast scrolling
            .build()
    }
    var inlinePlay by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (isVideoCover && coverAllowed && model.coverVideoUrl != null) {
                    { inlinePlay = !inlinePlay }
                } else null
            )
    ) {
        Column {
            // Cover image with 2:3 aspect ratio + overlaid badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {
                var coverFirstFrame by remember { mutableStateOf(false) }

                if (inlinePlay && isVideoCover && model.coverVideoUrl != null) {
                    VideoPlayer(
                        videoUri = Uri.parse(model.coverVideoUrl),
                        modifier = Modifier.fillMaxSize(),
                        showController = false,
                        scaleMode = VideoScaleMode.CROP,
                        onSingleTap = { inlinePlay = false; coverFirstFrame = false },
                        onFirstFrame = { coverFirstFrame = true }
                    )
                } else if (autoplayVisible && isVideoCover && model.coverVideoUrl != null && coverAllowed) {
                    // AutoplayVideoThumbnail handles its own thumbnail layering
                    AutoplayVideoThumbnail(
                        videoUrl = model.coverVideoUrl!!,
                        thumbnailUrl = displayUrl ?: "",
                        itemKey = "cover_${model.id}",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Thumbnail on TOP — hidden once inline video renders first frame
                // Skip for autoplay (AutoplayVideoThumbnail has its own)
                val showCoverThumbnail = !(inlinePlay && coverFirstFrame) && !(autoplayVisible && isVideoCover)
                if (showCoverThumbnail && displayUrl != null && coverAllowed) {
                    AsyncImage(
                        model = coverImageRequest,
                        contentDescription = model.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    // Play icon for video covers (only when not playing)
                    if (isVideoCover && !autoplayVisible) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Video",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(32.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                } else if (displayUrl != null && !coverAllowed) {
                    // Cover image exists but blocked by browse level — show placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else if (displayUrl == null) {
                    // No cover image at all — show model name as fallback
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Loading spinner while inline video is loading (before first frame)
                if (inlinePlay && !coverFirstFrame) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Type + base model badges overlaid on image (top-left)
                // Hide badge if its filter is active (redundant info)
                val showType = model.civitaiType != null && filterType == null && model.civitaiType != "Other"
                val showBase = model.baseModel != null && filterBaseModel == null && model.baseModel != "Other"
                if (showType || showBase) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (showType) {
                            MiniChip(
                                text = model.civitaiType!!,
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        if (showBase) {
                            MiniChip(
                                text = model.baseModel!!,
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Model info — name + horizontal scrolling tags
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (model.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        model.tags.take(4).forEach { tag ->
                            MiniChip(
                                text = tag,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailBottomSheet(
    model: ModelSearchResult,
    uiState: ModelBrowserUiState,
    nsfwLevels: Set<Int>,
    onDismiss: () -> Unit,
    onSelectVersion: (ModelVersion) -> Unit,
    onToggleCommunityImages: () -> Unit,
    onLoadMoreCommunityImages: () -> Unit,
    onSortCommunityImages: (String) -> Unit,
    onSelectModelType: (ModelType) -> Unit,
    onSelectFile: (ModelFile) -> Unit,
    onDownload: (subfolder: String) -> Unit,
    onImportWorkflow: (String) -> Unit,
    onTypeFilterChanged: (String?) -> Unit = {},
    onMetaOnlyChanged: (Boolean) -> Unit = {},
    onFeaturedFirstChanged: (Boolean) -> Unit = {},
    onGroupByPostChanged: (Boolean) -> Unit = {},
    onBrowseLevelChanged: (Int) -> Unit = {},
    onCommunityFiltersApplied: () -> Unit = {},
    onFetchMetadata: ((Long, Long?, (sh.hnet.comfychair.model.GenerationMetadata?) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    var showDownloadDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Show community images or model details
            if (uiState.showCommunityImages) {
                CommunityImagesScreen(
                    images = uiState.communityImages,
                    posts = uiState.communityPosts,
                    isLoading = uiState.isLoadingCommunityImages,
                    hasMore = uiState.hasMoreCommunityImages,
                    browseLevel = uiState.browseLevel,
                    showAnimations = uiState.showAnimations,
                    currentSort = uiState.communityImagesSort,
                    typeFilter = uiState.communityTypeFilter,
                    metaOnly = uiState.communityMetaOnly,
                    featuredFirst = uiState.communityFeaturedFirst,
                    groupByPost = uiState.communityGroupByPost,
                    autoplayVideos = uiState.autoplayVideos,
                    onSortChanged = onSortCommunityImages,
                    onTypeFilterChanged = onTypeFilterChanged,
                    onMetaOnlyChanged = onMetaOnlyChanged,
                    onFeaturedFirstChanged = onFeaturedFirstChanged,
                    onGroupByPostChanged = onGroupByPostChanged,
                    onBrowseLevelChanged = onBrowseLevelChanged,
                    onFiltersApplied = onCommunityFiltersApplied,
                    onLoadMore = onLoadMoreCommunityImages,
                    onBack = onToggleCommunityImages,
                    onImportWorkflow = onImportWorkflow,
                    onFetchMetadata = onFetchMetadata
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .padding(bottom = 80.dp) // Space for FABs
                    ) {
                // Image gallery (if version has images)
                uiState.selectedVersion?.let { version ->
                    // Filter images based on NSFW setting
                    val filteredImages = version.images.filter { it.nsfwLevel in nsfwLevels }
                    
                    if (filteredImages.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lazyItems(filteredImages) { image ->
                                val isVideo = image.type == "video"
                                val params = "anim=false,width=450,optimized=true"
                                val imageUrl = image.url
                                    .replace("/original=true/", "/$params/")
                                    .replace(Regex("/width=\\d+[^/]*/"), "/$params/")
                                Box {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.height(250.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    if (isVideo) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Video",
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(32.dp),
                                            tint = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Model name
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Creator and base model
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    model.creator?.let { creator ->
                        Text(
                            text = "by $creator",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    model.baseModel?.let { baseModel ->
                        Text(
                            text = baseModel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Download/favorite stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    model.downloadCount?.let {
                        Text(
                            text = "⬇ ${formatNumber(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    model.favoriteCount?.let {
                        Text(
                            text = "❤ ${formatNumber(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Version selector
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lazyItems(model.versions) { version ->
                        val isInstalled = version.id.toLongOrNull()
                            ?.let { it in uiState.installedVersionIds } == true
                        FilterChip(
                            selected = uiState.selectedVersion == version,
                            onClick = { onSelectVersion(version) },
                            label = { Text(version.name) },
                            leadingIcon = if (isInstalled) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Installed",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trained words (if available)
                uiState.selectedVersion?.let { version ->
                    if (version.trainedWords.isNotEmpty()) {
                        Text(
                            text = "Trained Words",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            version.trainedWords.forEach { word ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text(word) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Version notes
                uiState.selectedVersion?.let { version ->
                    if (!version.description.isNullOrBlank()) {
                        Text(
                            text = "Version Notes",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HtmlText(html = version.description)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Model description
                model.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HtmlText(html = desc)
                    }
                }
                        }
                    }

                    // FAB column at bottom-right
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Community images FAB (only for Civitai)
                        if (uiState.selectedProvider == ModelProvider.CIVITAI) {
                            FloatingActionButton(
                                onClick = onToggleCommunityImages,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.Photo, contentDescription = "Community Images")
                            }
                        }
                        // Download FAB
                        FloatingActionButton(
                            onClick = { showDownloadDialog = true }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                        }
                    }
                }
            }
        }

    // Download config dialog
    if (showDownloadDialog) {
        DownloadConfigDialog(
            model = model,
            uiState = uiState,
            onDismiss = { showDownloadDialog = false },
            onSelectModelType = onSelectModelType,
            onSelectFile = onSelectFile,
            onDownload = onDownload
        )
    }
}

@Composable
fun SearchFilters(
    uiState: ModelBrowserUiState,
    onFilterTypeChanged: (String?) -> Unit,
    onFilterBaseModelChanged: (String?) -> Unit,
    onFilterSortChanged: (String) -> Unit,
    onFilterPeriodChanged: (String) -> Unit
) {
    // Static filter lists (trpc doesn't provide dynamic facets like Meili did)
    val modelTypes = listOf(
        "LORA", "Checkpoint", "LoCon", "TextualInversion", "Workflows", "Wildcards",
        "DoRA", "Poses", "Hypernetwork", "VAE", "Controlnet", "AestheticGradient",
        "Detection", "MotionModule", "Upscaler", "Other"
    )
    val baseModels = listOf(
        "Illustrious", "SD 1.5", "Pony", "Flux.1 D", "SDXL 1.0", "NoobAI",
        "ZImageTurbo", "Qwen", "Hunyuan Video", "Wan Video 2.2 I2V-A14B",
        "Wan Video 2.2 T2V-A14B", "Wan Video 14B I2v", "Flux.1 S", "SD 2.1 768",
        "Chroma", "ZImageBase", "Flux.1 Kontext", "Other"
    )
    val sortOptions = listOf("Most Downloaded", "Highest Rated", "Most Liked", "Most Discussed", "Most Collected", "Most Buzz", "Newest")
    val periodOptions = listOf("AllTime", "Year", "Month", "Week", "Day")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sort
        Text("Sort", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sortOptions.forEach { sort ->
                FilterChip(
                    selected = uiState.filterSort == sort,
                    onClick = { onFilterSortChanged(sort) },
                    label = { Text(sort, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Period
        Text("Period", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            periodOptions.forEach { period ->
                FilterChip(
                    selected = uiState.filterPeriod == period,
                    onClick = { onFilterPeriodChanged(period) },
                    label = { Text(period, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Model type
        Text("Type", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = uiState.filterModelType == null,
                onClick = { onFilterTypeChanged(null) },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) }
            )
            modelTypes.forEach { type ->
                FilterChip(
                    selected = uiState.filterModelType == type,
                    onClick = { onFilterTypeChanged(type) },
                    label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Base model
        Text("Base Model", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = uiState.filterBaseModel == null,
                onClick = { onFilterBaseModelChanged(null) },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) }
            )
            baseModels.forEach { model ->
                FilterChip(
                    selected = uiState.filterBaseModel == model,
                    onClick = { onFilterBaseModelChanged(model) },
                    label = { Text(model, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun MiniChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.height(24.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HtmlText(html: String) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 14f
                setTextColor(textColor)
            }
        },
        update = { textView ->
            textView.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DownloadConfigDialog(
    model: ModelSearchResult,
    uiState: ModelBrowserUiState,
    onDismiss: () -> Unit,
    onSelectModelType: (ModelType) -> Unit,
    onSelectFile: (ModelFile) -> Unit,
    onDownload: (subfolder: String) -> Unit
) {
    var subfolder by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "Download Configuration",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Model type selection (auto-detected)
                Text("Model Type:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelType.entries.forEach { type ->
                        FilterChip(
                            selected = uiState.selectedModelType == type,
                            onClick = { onSelectModelType(type) },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Subfolder input
                OutlinedTextField(
                    value = subfolder,
                    onValueChange = { subfolder = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Subfolder (optional)") },
                    placeholder = { Text("e.g. illustrious or sdxl/loras") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Leave empty to use default location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // File selection (for HuggingFace)
                if (model.provider == ModelProvider.HUGGINGFACE && 
                    uiState.selectedVersion?.files?.isNotEmpty() == true) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Select File:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    uiState.selectedVersion?.files?.forEach { file ->
                        FilterChip(
                            selected = uiState.selectedFile == file,
                            onClick = { onSelectFile(file) },
                            label = { Text(file.filename) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Download button
                if (uiState.isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    uiState.downloadProgress?.let { progress ->
                        Text(
                            text = progress,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { 
                                onDownload(subfolder.trim())
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = uiState.selectedModelType != null
                        ) {
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

private fun formatNumber(num: Long): String {
    return when {
        num >= 1_000_000 -> String.format("%.1fM", num / 1_000_000.0)
        num >= 1_000 -> String.format("%.1fK", num / 1_000.0)
        else -> num.toString()
    }
}
