package sh.hnet.comfychair.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import sh.hnet.comfychair.model.ModelProvider
import sh.hnet.comfychair.model.ModelSearchResult
import sh.hnet.comfychair.model.ModelType
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

                // NSFW toggle (Civitai only)
                if (uiState.selectedProvider == ModelProvider.CIVITAI) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Include NSFW",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = viewModel.getShowNsfw(),
                            onCheckedChange = { viewModel.setShowNsfw(it) }
                        )
                    }
                }

                // Clear API key option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        when (uiState.selectedProvider) {
                            ModelProvider.CIVITAI -> viewModel.setCivitaiApiKey("")
                            ModelProvider.HUGGINGFACE -> viewModel.setHuggingFaceApiKey("")
                        }
                    }) {
                        Text(
                            "Change API Key",
                            style = MaterialTheme.typography.labelSmall
                        )
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

                // Search results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.searchResults) { model ->
                        ModelResultCard(
                            model = model,
                            onClick = { viewModel.selectModel(model) }
                        )
                    }
                }
            }
        }
    }

    // Model detail dialog
    uiState.selectedModel?.let { model ->
        ModelDetailDialog(
            model = model,
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
fun ModelResultCard(
    model: ModelSearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium
            )

            model.creator?.let { creator ->
                Text(
                    text = "by $creator",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            model.description?.let { desc ->
                Text(
                    text = desc.take(150) + if (desc.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
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
        }
    }
}

@Composable
fun ModelDetailDialog(
    model: ModelSearchResult,
    viewModel: ModelBrowserViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Version selection
                Text("Select Version:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                model.versions.forEach { version ->
                    FilterChip(
                        selected = uiState.selectedVersion == version,
                        onClick = { viewModel.selectVersion(version) },
                        label = { Text(version.name) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

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

                // Model type selection
                Text("Select Model Type:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                ModelType.values().forEach { type ->
                    FilterChip(
                        selected = uiState.selectedModelType == type,
                        onClick = { viewModel.selectModelType(type) },
                        label = { Text(type.displayName) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                            onClick = { viewModel.downloadModel() },
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
