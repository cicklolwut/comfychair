package sh.hnet.comfychair.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.hnet.comfychair.R
import sh.hnet.comfychair.service.OpenRouterModels
import sh.hnet.comfychair.model.PromptEnhancementMode
import sh.hnet.comfychair.model.PromptEnhancementProvider
import sh.hnet.comfychair.storage.PromptEnhancementSettings
import sh.hnet.comfychair.ui.components.SettingsScreenScaffold
import sh.hnet.comfychair.viewmodel.PromptEnhancementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEnhancementSettingsScreen(
    onNavigateToGeneration: () -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: PromptEnhancementViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var apiKeyVisible by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var editingPromptMode by remember { mutableStateOf<PromptEnhancementMode?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }

    // Load OpenRouter models when provider is OPENROUTER
    LaunchedEffect(uiState.provider) {
        if (uiState.provider == PromptEnhancementProvider.OPENROUTER && uiState.openRouterModels.isEmpty()) {
            viewModel.fetchOpenRouterModels()
        }
    }

    // Error toast
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.nav_prompt_enhancement_settings),
        onNavigateToGeneration = onNavigateToGeneration,
        onLogout = onLogout,
        horizontalPadding = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Provider Section ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.label_enhancement_provider),
                        style = MaterialTheme.typography.titleSmall
                    )

                    // Provider dropdown
                    ExposedDropdownMenuBox(
                        expanded = providerExpanded,
                        onExpandedChange = { providerExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = uiState.provider.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false }
                        ) {
                            PromptEnhancementProvider.entries.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        viewModel.setProvider(provider)
                                        providerExpanded = false
                                    },
                                    leadingIcon = if (provider == uiState.provider) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // API Key
                    OutlinedTextField(
                        value = uiState.apiKey,
                        onValueChange = { viewModel.setApiKey(it) },
                        label = { Text(stringResource(R.string.label_enhancement_api_key)) },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    if (apiKeyVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Custom base URL (only for Custom provider)
                    AnimatedVisibility(visible = uiState.provider == PromptEnhancementProvider.CUSTOM) {
                        OutlinedTextField(
                            value = uiState.customBaseUrl,
                            onValueChange = { viewModel.setCustomBaseUrl(it) },
                            label = { Text(stringResource(R.string.label_enhancement_base_url)) },
                            placeholder = { Text("https://api.example.com/v1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Model selection
                    if (uiState.provider == PromptEnhancementProvider.OPENROUTER) {
                        // OpenRouter: clickable field that opens model picker dialog
                        OutlinedTextField(
                            value = uiState.model,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.label_enhancement_model)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showModelPicker = true },
                            trailingIcon = {
                                if (uiState.isLoadingModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    IconButton(onClick = { showModelPicker = true }) {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                    }
                                }
                            }
                        )
                    } else {
                        // OpenAI/Custom: free text input
                        OutlinedTextField(
                            value = uiState.model,
                            onValueChange = { viewModel.setModel(it) },
                            label = { Text(stringResource(R.string.label_enhancement_model)) },
                            placeholder = { Text(PromptEnhancementProvider.defaultModel(uiState.provider)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // --- System Prompts Section ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_enhancement_system_prompts),
                            style = MaterialTheme.typography.titleSmall
                        )
                        TextButton(onClick = { viewModel.resetAllSystemPrompts() }) {
                            Text(stringResource(R.string.button_reset_all_prompts))
                        }
                    }

                    PromptEnhancementMode.entries.forEach { mode ->
                        val displayName = when (mode) {
                            PromptEnhancementMode.TEXT_TO_IMAGE -> stringResource(R.string.label_mode_txt2img)
                            PromptEnhancementMode.IMAGE_TO_IMAGE -> stringResource(R.string.label_mode_img2img)
                            PromptEnhancementMode.TEXT_TO_VIDEO -> stringResource(R.string.label_mode_txt2vid)
                            PromptEnhancementMode.IMAGE_TO_VIDEO -> stringResource(R.string.label_mode_img2vid)
                        }
                        val currentPrompt = uiState.systemPrompts[mode] ?: ""
                        val isDefault = currentPrompt == PromptEnhancementSettings.DEFAULT_PROMPTS[mode]

                        OutlinedButton(
                            onClick = { editingPromptMode = mode },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(displayName)
                                    if (!isDefault) {
                                        Text(
                                            text = stringResource(R.string.label_customized),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = currentPrompt.take(80) + if (currentPrompt.length > 80) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- System Prompt Editor Dialog ---
    editingPromptMode?.let { mode ->
        val displayName = when (mode) {
            PromptEnhancementMode.TEXT_TO_IMAGE -> stringResource(R.string.label_mode_txt2img)
            PromptEnhancementMode.IMAGE_TO_IMAGE -> stringResource(R.string.label_mode_img2img)
            PromptEnhancementMode.TEXT_TO_VIDEO -> stringResource(R.string.label_mode_txt2vid)
            PromptEnhancementMode.IMAGE_TO_VIDEO -> stringResource(R.string.label_mode_img2vid)
        }
        var editText by remember(mode) {
            mutableStateOf(uiState.systemPrompts[mode] ?: "")
        }

        AlertDialog(
            onDismissRequest = { editingPromptMode = null },
            title = { Text(displayName) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        minLines = 5
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            viewModel.resetSystemPrompt(mode)
                            editText = PromptEnhancementSettings.DEFAULT_PROMPTS[mode] ?: ""
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.button_reset_to_default),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setSystemPrompt(mode, editText)
                    editingPromptMode = null
                }) {
                    Text(stringResource(R.string.button_save))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingPromptMode = null }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }

    // --- OpenRouter Model Picker Dialog ---
    if (showModelPicker) {
        OpenRouterModelPickerDialog(
            models = uiState.openRouterModels,
            selectedModel = uiState.model,
            filter = uiState.modelFilter,
            isLoading = uiState.isLoadingModels,
            onFilterChange = { viewModel.setModelFilter(it) },
            onModelSelected = { modelId ->
                viewModel.setModel(modelId)
                showModelPicker = false
            },
            onRefresh = { viewModel.fetchOpenRouterModels(forceRefresh = true) },
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun OpenRouterModelPickerDialog(
    models: List<OpenRouterModels.Model>,
    selectedModel: String,
    filter: String,
    isLoading: Boolean,
    onFilterChange: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val filteredModels = remember(models, filter) {
        if (filter.isBlank()) models
        else models.filter {
            it.id.contains(filter, ignoreCase = true) ||
            it.name.contains(filter, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.title_select_model))
                IconButton(onClick = onRefresh) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.button_refresh))
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Filter field
                OutlinedTextField(
                    value = filter,
                    onValueChange = onFilterChange,
                    placeholder = { Text(stringResource(R.string.placeholder_filter_models)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.label_model_count, filteredModels.size, models.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Model list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(filteredModels, key = { it.id }) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onModelSelected(model.id) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (model.id == selectedModel) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 4.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (model.id == selectedModel)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = model.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}


