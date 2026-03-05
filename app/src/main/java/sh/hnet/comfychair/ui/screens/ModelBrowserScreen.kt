package sh.hnet.comfychair.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import android.text.Html
import android.widget.Toast
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.runtime.snapshotFlow
import coil3.imageLoader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.CompositionLocalProvider
import sh.hnet.comfychair.ui.components.SettingsScreenScaffold
import sh.hnet.comfychair.model.CivitaiTypeMapper
import sh.hnet.comfychair.viewmodel.ModelBrowserUiState
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelFile
import sh.hnet.comfychair.model.ModelType
import sh.hnet.comfychair.model.ModelVersion
import sh.hnet.comfychair.viewmodel.ModelBrowserEvent
import sh.hnet.comfychair.viewmodel.ModelBrowserViewModel

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
            // Provider selection
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        val gridState = rememberLazyGridState()
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.searchResults, key = { it.id }) { model ->
                                ModelGridCard(
                                    model = model,
                                    onClick = { viewModel.selectModel(model) }
                                )
                            }
                            
                            // Loading indicator at bottom
                            if (uiState.isLoadingMore) {
                                item(span = { GridItemSpan(2) }) {
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
                        
                        // Image prefetching for smooth scrolling
                        val imageLoader = context.imageLoader
                        LaunchedEffect(gridState, uiState.searchResults) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                                .collect { lastVisible ->
                                    val prefetchEnd = minOf(lastVisible + 8, uiState.searchResults.size)
                                    for (i in (lastVisible + 1) until prefetchEnd) {
                                        uiState.searchResults.getOrNull(i)?.thumbnailUrl?.let { url ->
                                            imageLoader.enqueue(
                                                ImageRequest.Builder(context).data(url).build()
                                            )
                                        }
                                    }
                                }
                        }
                    }

                    // Filter FAB (bottom-right)
                    SmallFloatingActionButton(
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

    // Filter bottom sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp) // Bottom padding for nav gesture area
            ) {
                // Title
                Text(
                    "Search Settings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // NSFW Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Show NSFW", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.showNsfw,
                        onCheckedChange = { viewModel.setShowNsfw(it) }
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Filters (reuse existing SearchFilters composable)
                SearchFilters(
                    uiState = uiState,
                    onFilterTypeChanged = viewModel::setFilterModelType,
                    onFilterBaseModelChanged = viewModel::setFilterBaseModel,
                    onFilterSortChanged = viewModel::setFilterSort,
                    onFilterPeriodChanged = viewModel::setFilterPeriod
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                // API Key management
                TextButton(
                    onClick = {
                        viewModel.resetProviderApiKey()
                        showFilterSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when (uiState.selectedProvider) {
                            ModelProvider.CIVITAI -> "Change Civitai API Key"
                            ModelProvider.HUGGINGFACE -> "Change HuggingFace API Key"
                        }
                    )
                }
            }
        }
    }

    // Model detail bottom sheet
    if (uiState.selectedModel != null) {
        ModelDetailBottomSheet(
            model = uiState.selectedModel!!,
            uiState = uiState,
            showNsfw = uiState.showNsfw,
            onDismiss = { viewModel.clearSelection() },
            onSelectVersion = viewModel::selectVersion,
            onToggleCommunityImages = viewModel::toggleCommunityImages,
            onLoadMoreCommunityImages = viewModel::loadMoreCommunityImages,
            onSelectModelType = viewModel::selectModelType,
            onSelectFile = viewModel::selectFile,
            onDownload = viewModel::downloadModel,
            onImportWorkflow = viewModel::importWorkflow
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

@Composable
fun ModelGridCard(
    model: ModelSearchResult,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            // Cover image with 2:3 aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.67f)
            ) {
                if (model.thumbnailUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(model.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = model.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
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
            }

            // Model info
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

                // Base model + tags
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    model.baseModel?.let { baseModel ->
                        MiniChip(
                            text = baseModel,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    model.tags.take(3).forEach { tag ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailBottomSheet(
    model: ModelSearchResult,
    uiState: ModelBrowserUiState,
    showNsfw: Boolean,
    onDismiss: () -> Unit,
    onSelectVersion: (ModelVersion) -> Unit,
    onToggleCommunityImages: () -> Unit,
    onLoadMoreCommunityImages: () -> Unit,
    onSelectModelType: (ModelType) -> Unit,
    onSelectFile: (ModelFile) -> Unit,
    onDownload: (subfolder: String) -> Unit,
    onImportWorkflow: (String) -> Unit
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
                    isLoading = uiState.isLoadingCommunityImages,
                    hasMore = uiState.hasMoreCommunityImages,
                    showNsfw = showNsfw,
                    onLoadMore = onLoadMoreCommunityImages,
                    onBack = onToggleCommunityImages,
                    onImportWorkflow = onImportWorkflow
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
                    val filteredImages = if (showNsfw) version.images else version.images.filter { it.nsfwLevel <= 4 }
                    
                    if (filteredImages.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lazyItems(filteredImages) { image ->
                                val imageUrl = image.url.replace("/original=true/", "/width=400/")
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.height(250.dp),
                                    contentScale = ContentScale.Fit
                                )
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
                        FilterChip(
                            selected = uiState.selectedVersion == version,
                            onClick = { onSelectVersion(version) },
                            label = { Text(version.name) }
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
                            SmallFloatingActionButton(
                                onClick = onToggleCommunityImages,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.Photo, contentDescription = "Community Images")
                            }
                        }
                        // Download FAB
                        SmallFloatingActionButton(
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
    // Use dynamic facets from Meili, fall back to defaults if empty
    val modelTypes = if (uiState.availableTypes.isNotEmpty()) uiState.availableTypes 
        else listOf("Checkpoint", "LORA", "LoCon", "TextualInversion", "VAE", "Controlnet", "Upscaler")
    val baseModels = if (uiState.availableBaseModels.isNotEmpty()) uiState.availableBaseModels
        else listOf("Illustrious", "NoobAI", "Pony", "SDXL 1.0", "SD 1.5", "Flux.1 D")
    val sortOptions = listOf("Most Downloaded", "Highest Rated", "Most Liked", "Most Discussed", "Most Collected", "Most Buzz", "Newest")
    val periodOptions = listOf("AllTime", "Month", "Week", "Day") // TODO: Period not used by Meili search

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
    // Reduce M3's 48dp minimum touch target for dense grid layouts
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        SuggestionChip(
            onClick = { },
            label = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = containerColor,
                labelColor = contentColor
            ),
            border = null,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun HtmlText(html: String) {
    val context = LocalContext.current
    AndroidView(
        factory = { 
            TextView(it).apply {
                text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                textSize = 14f
            }
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
                    ModelType.values().forEach { type ->
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
