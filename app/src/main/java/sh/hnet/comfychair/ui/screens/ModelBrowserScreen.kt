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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.material.icons.filled.FilterList
import sh.hnet.comfychair.model.CivitaiTypeMapper
import sh.hnet.comfychair.viewmodel.ModelBrowserUiState
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelType
import sh.hnet.comfychair.model.ModelVersion
import sh.hnet.comfychair.viewmodel.ModelBrowserEvent
import sh.hnet.comfychair.viewmodel.ModelBrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBrowserScreen(
    viewModel: ModelBrowserViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Track whether the current provider has a key configured
    val isProviderConfigured = when (uiState.selectedProvider) {
        ModelProvider.CIVITAI -> viewModel.getCivitaiApiKey().isNotBlank()
        ModelProvider.HUGGINGFACE -> viewModel.getHuggingFaceApiKey().isNotBlank()
    }

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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Browser") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
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
                    onKeySaved = { key ->
                        when (uiState.selectedProvider) {
                            ModelProvider.CIVITAI -> viewModel.setCivitaiApiKey(key)
                            ModelProvider.HUGGINGFACE -> viewModel.setHuggingFaceApiKey(key)
                        }
                    }
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

                // Filter toggle + NSFW + API key row
                if (uiState.selectedProvider == ModelProvider.CIVITAI) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.showFilters,
                                onClick = { viewModel.toggleFilters() },
                                label = { Text("Filters") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = "Filters",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("NSFW", style = MaterialTheme.typography.labelSmall)
                                Switch(
                                    checked = viewModel.getShowNsfw(),
                                    onCheckedChange = { viewModel.setShowNsfw(it) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.setCivitaiApiKey("") }) {
                            Text("Change Key", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Expandable filter section
                    if (uiState.showFilters) {
                        SearchFilters(uiState = uiState, viewModel = viewModel)
                    }
                } else {
                    // HuggingFace — just the API key change
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.setHuggingFaceApiKey("") }) {
                            Text("Change API Key", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.searchResults) { model ->
                        ModelGridCard(
                            model = model,
                            onClick = { viewModel.selectModel(model) }
                        )
                    }
                }
            }
        }
    }

    // Model detail bottom sheet
    if (uiState.selectedModel != null) {
        ModelDetailBottomSheet(
            model = uiState.selectedModel!!,
            viewModel = viewModel,
            onDismiss = { viewModel.clearSelection() }
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

                // Base model + tags in FlowRow
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Base model chip
                    model.baseModel?.let { baseModel ->
                        SuggestionChip(
                            onClick = { },
                            label = { 
                                Text(
                                    baseModel,
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            }
                        )
                    }
                    
                    // Up to 3 tags
                    model.tags.take(3).forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { 
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelSmall
                                ) 
                            }
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
    viewModel: ModelBrowserViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showDownloadDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Show community images or model details
            if (uiState.showCommunityImages) {
                CommunityImagesScreen(
                    images = uiState.communityImages,
                    isLoading = uiState.isLoadingCommunityImages,
                    hasMore = uiState.hasMoreCommunityImages,
                    onLoadMore = { viewModel.loadMoreCommunityImages() },
                    onBack = { viewModel.toggleCommunityImages() },
                    onImportWorkflow = { json ->
                        // Handle workflow import - for now just show toast
                        Toast.makeText(context, "Workflow imported", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Community Images button (only for Civitai)
                        if (uiState.selectedProvider == ModelProvider.CIVITAI) {
                            Button(
                                onClick = { viewModel.toggleCommunityImages() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("🖼 Community Images")
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                                .padding(bottom = 80.dp) // Space for FAB
                        ) {
                // Image gallery (if version has images)
                uiState.selectedVersion?.let { version ->
                    if (version.images.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            lazyItems(version.images) { image ->
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
                            onClick = { viewModel.selectVersion(version) },
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

                    // Download FAB (pinned bottom-right)
                    SmallFloatingActionButton(
                        onClick = { showDownloadDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
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
            viewModel = viewModel,
            onDismiss = { showDownloadDialog = false }
        )
    }
}

@Composable
fun SearchFilters(
    uiState: ModelBrowserUiState,
    viewModel: ModelBrowserViewModel
) {
    val modelTypes = listOf("Checkpoint", "LORA", "LoCon", "TextualInversion", "VAE", "Controlnet", "Upscaler")
    val baseModels = listOf("Illustrious", "NoobAI", "Pony", "SDXL 1.0", "SD 1.5", "Flux.1 D", "Flux.1 S")
    val sortOptions = listOf("Most Downloaded", "Highest Rated", "Newest")
    val periodOptions = listOf("AllTime", "Month", "Week", "Day")

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
                    onClick = { viewModel.setFilterSort(sort) },
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
                    onClick = { viewModel.setFilterPeriod(period) },
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
                onClick = { viewModel.setFilterModelType(null) },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) }
            )
            modelTypes.forEach { type ->
                FilterChip(
                    selected = uiState.filterModelType == type,
                    onClick = { viewModel.setFilterModelType(type) },
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
                onClick = { viewModel.setFilterBaseModel(null) },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) }
            )
            baseModels.forEach { model ->
                FilterChip(
                    selected = uiState.filterBaseModel == model,
                    onClick = { viewModel.setFilterBaseModel(model) },
                    label = { Text(model, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
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
    viewModel: ModelBrowserViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
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
                            onClick = { viewModel.selectModelType(type) },
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
                            onClick = { viewModel.selectFile(file) },
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
                                viewModel.downloadModel(subfolder.trim())
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
