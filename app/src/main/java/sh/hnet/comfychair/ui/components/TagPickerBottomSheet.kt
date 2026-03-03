package sh.hnet.comfychair.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.WorkflowManager
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.ui.components.shared.ExpandableFilterChipRowMultiSelect
import sh.hnet.comfychair.ui.components.shared.NoOverscrollContainer

/**
 * Bottom sheet for selecting workflow and model name associations.
 * Used in the prompt editor to replace comma-separated text fields.
 *
 * @param selectedWorkflowNames Currently selected workflow names
 * @param selectedModelNames Currently selected model names
 * @param onWorkflowNamesChanged Callback when workflow selection changes
 * @param onModelNamesChanged Callback when model selection changes
 * @param sheetState The modal sheet state
 * @param onDismiss Callback when sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerBottomSheet(
    selectedWorkflowNames: Set<String>,
    selectedModelNames: Set<String>,
    onWorkflowNamesChanged: (Set<String>) -> Unit,
    onModelNamesChanged: (Set<String>) -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    // Get available workflows
    val workflows = remember { WorkflowManager.getAllWorkflows() }
    val workflowNames = remember(workflows) {
        workflows.map { it.name }.distinct().sorted()
    }

    // Get available models from ConnectionManager cache
    val modelCache by ConnectionManager.modelCache.collectAsState()
    val modelNames = remember(modelCache) {
        (modelCache.checkpoints + modelCache.unets).distinct().sorted()
    }

    // Expansion state for sections
    var workflowsExpanded by remember { mutableStateOf(false) }
    var modelsExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title
            Text(
                text = stringResource(R.string.title_select_associations),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Scrollable content
            NoOverscrollContainer(modifier = Modifier.weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: Workflow Names
                    if (workflowNames.isNotEmpty()) {
                        CollapsibleTagSection(
                            title = stringResource(R.string.label_workflow_names),
                            options = workflowNames,
                            selectedOptions = selectedWorkflowNames,
                            onSelectionChanged = onWorkflowNamesChanged,
                            expanded = workflowsExpanded,
                            onExpandedChanged = { workflowsExpanded = it }
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.label_no_workflows_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Section 2: Model Names (checkpoints + unets)
                    if (modelNames.isNotEmpty()) {
                        CollapsibleTagSection(
                            title = stringResource(R.string.label_model_names),
                            options = modelNames,
                            selectedOptions = selectedModelNames,
                            onSelectionChanged = onModelNamesChanged,
                            expanded = modelsExpanded,
                            onExpandedChanged = { modelsExpanded = it }
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.label_no_models_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Done button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.button_done))
            }
        }
    }
}

/**
 * A collapsible section with a title, selection count badge, and toggleable chips.
 */
@Composable
private fun CollapsibleTagSection(
    title: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit
) {
    Column {
        // Section header with title and selection count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Selection count badge
            if (selectedOptions.isNotEmpty()) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = stringResource(R.string.label_selected_count, selectedOptions.size),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chips with multi-select support
        ExpandableFilterChipRowMultiSelect(
            options = options,
            selectedOptions = selectedOptions,
            onOptionToggled = { option ->
                val newSet = if (option in selectedOptions) {
                    selectedOptions - option
                } else {
                    selectedOptions + option
                }
                onSelectionChanged(newSet)
            },
            expanded = expanded,
            onExpandedChange = onExpandedChanged
        )
    }
}
