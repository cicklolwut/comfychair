package sh.hnet.comfychair.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import sh.hnet.comfychair.model.EnhancementPrompt
import sh.hnet.comfychair.model.PromptTag
import sh.hnet.comfychair.model.PromptEnhancementProvider
import sh.hnet.comfychair.service.OpenRouterModels
import sh.hnet.comfychair.ui.components.SettingsScreenScaffold
import sh.hnet.comfychair.viewmodel.PromptEnhancementViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showModelPicker by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<EnhancementPrompt?>(null) }
    var showNewPromptDialog by remember { mutableStateOf(false) }
    var promptToDelete by remember { mutableStateOf<EnhancementPrompt?>(null) }

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
            // --- Provider / Connection Section ---
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
                        OutlinedTextField(
                            value = uiState.model,
                            onValueChange = { viewModel.setModel(it) },
                            label = { Text(stringResource(R.string.label_enhancement_model)) },
                            placeholder = { Text(PromptEnhancementProvider.defaultModel(uiState.provider)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Save & Test button
                    Button(
                        onClick = { viewModel.testConnection() },
                        enabled = uiState.apiKey.isNotBlank() && !uiState.isTesting &&
                            (uiState.provider != PromptEnhancementProvider.CUSTOM || uiState.customBaseUrl.isNotBlank()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("  Testing…", modifier = Modifier.padding(start = 8.dp))
                        } else {
                            Text(stringResource(R.string.button_save_and_test))
                        }
                    }

                    // Test result
                    uiState.testResult?.let { result ->
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isValidated) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                        )
                    }

                    // Validation status
                    if (uiState.isValidated) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.label_connection_verified),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            // --- Include Examples Toggle ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setIncludeExamples(!uiState.includeExamples) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.label_include_examples),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.label_include_examples_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = uiState.includeExamples,
                        onCheckedChange = { viewModel.setIncludeExamples(it) }
                    )
                }
            }

            // --- Output Fields ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.label_output_fields),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.label_output_fields_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    sh.hnet.comfychair.model.EnhancementOutputField.entries.forEach { field ->
                        val isPrompt = field == sh.hnet.comfychair.model.EnhancementOutputField.PROMPT
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isPrompt) {
                                    viewModel.setOutputFieldEnabled(
                                        field,
                                        field !in uiState.enabledOutputFields
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = field.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isPrompt) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                            )
                            androidx.compose.material3.Switch(
                                checked = field in uiState.enabledOutputFields,
                                onCheckedChange = { checked ->
                                    viewModel.setOutputFieldEnabled(field, checked)
                                },
                                enabled = !isPrompt // Prompt is always on
                            )
                        }
                    }
                }
            }

            // --- Enhancement Prompts Library ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_enhancement_prompts),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row {
                            IconButton(onClick = { viewModel.restoreBuiltinPrompts() }) {
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = stringResource(R.string.button_restore_defaults),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { showNewPromptDialog = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.button_add_prompt),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Prompt list
                    uiState.availablePrompts.forEach { prompt ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingPrompt = prompt }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = prompt.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (prompt.isBuiltIn) {
                                            Text(
                                                text = " (built-in)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    // Tags
                                    FlowRow(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        prompt.tags.forEach { tag ->
                                            AssistChip(
                                                onClick = {},
                                                label = {
                                                    Text(
                                                        tag.displayName,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                },
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                    // Preview
                                    Text(
                                        text = prompt.systemPrompt.take(80) +
                                            if (prompt.systemPrompt.length > 80) "…" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                // Edit & Delete icons
                                IconButton(onClick = { editingPrompt = prompt }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { promptToDelete = prompt }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.availablePrompts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.label_no_prompts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.restoreBuiltinPrompts() }) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                stringResource(R.string.button_restore_defaults),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Prompt Editor Dialog (edit existing) ---
    editingPrompt?.let { prompt ->
        PromptEditorDialog(
            prompt = prompt,
            isNew = false,
            onSave = { updated ->
                viewModel.updatePrompt(updated)
                editingPrompt = null
            },
            onDismiss = { editingPrompt = null }
        )
    }

    // --- New Prompt Dialog ---
    if (showNewPromptDialog) {
        PromptEditorDialog(
            prompt = EnhancementPrompt(
                name = "",
                systemPrompt = "",
                tags = setOf(PromptTag.TEXT_TO_IMAGE)
            ),
            isNew = true,
            onSave = { newPrompt ->
                viewModel.addPrompt(newPrompt)
                showNewPromptDialog = false
            },
            onDismiss = { showNewPromptDialog = false }
        )
    }

    // --- Delete Confirmation ---
    promptToDelete?.let { prompt ->
        AlertDialog(
            onDismissRequest = { promptToDelete = null },
            title = { Text(stringResource(R.string.title_delete_prompt)) },
            text = {
                Text(
                    if (prompt.isBuiltIn)
                        stringResource(R.string.msg_delete_builtin_prompt, prompt.name)
                    else
                        stringResource(R.string.msg_delete_custom_prompt, prompt.name)
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.deletePrompt(prompt.id)
                    promptToDelete = null
                }) {
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { promptToDelete = null }) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptEditorDialog(
    prompt: EnhancementPrompt,
    isNew: Boolean,
    onSave: (EnhancementPrompt) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(prompt.name) }
    var systemPrompt by remember { mutableStateOf(prompt.systemPrompt) }
    var selectedTags by remember { mutableStateOf(prompt.tags) }
    var exampleInput by remember { mutableStateOf(prompt.exampleInput) }
    var exampleOutput by remember { mutableStateOf(prompt.exampleOutput) }
    var workflowNamesText by remember { mutableStateOf(prompt.workflowNames.joinToString(", ")) }
    var modelNamesText by remember { mutableStateOf(prompt.modelNames.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) stringResource(R.string.title_new_prompt)
                else stringResource(R.string.title_edit_prompt)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_prompt_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Tags
                Text(
                    text = stringResource(R.string.label_prompt_tags),
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PromptTag.entries.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = {
                                selectedTags = if (tag in selectedTags)
                                    selectedTags - tag
                                else
                                    selectedTags + tag
                            },
                            label = { Text(tag.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // System Prompt
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.label_enhancement_system_prompt)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    minLines = 5
                )

                // Example (optional)
                Text(
                    text = stringResource(R.string.label_example_optional),
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = exampleInput,
                    onValueChange = { exampleInput = it },
                    label = { Text(stringResource(R.string.label_example_input)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = exampleOutput,
                    onValueChange = { exampleOutput = it },
                    label = { Text(stringResource(R.string.label_example_output)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Workflow/Model associations (optional)
                Text(
                    text = stringResource(R.string.label_associations_optional),
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = workflowNamesText,
                    onValueChange = { workflowNamesText = it },
                    label = { Text(stringResource(R.string.label_workflow_names)) },
                    placeholder = { Text(stringResource(R.string.placeholder_workflow_names)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelNamesText,
                    onValueChange = { modelNamesText = it },
                    label = { Text(stringResource(R.string.label_model_names)) },
                    placeholder = { Text(stringResource(R.string.placeholder_model_names)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(prompt.copy(
                        name = name.trim(),
                        systemPrompt = systemPrompt,
                        tags = selectedTags,
                        exampleInput = exampleInput.trim(),
                        exampleOutput = exampleOutput.trim(),
                        workflowNames = workflowNamesText.split(",")
                            .map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                        modelNames = modelNamesText.split(",")
                            .map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    ))
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank() && selectedTags.isNotEmpty()
            ) {
                Text(stringResource(R.string.button_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
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
