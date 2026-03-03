# Tag Picker Redesign Implementation Plan

## Overview
Replace comma-separated text fields for workflow/model name associations with a slide-up bottom sheet featuring collapsible sections and toggleable tags.

---

## 1. Existing Components to Reuse

### Bottom Sheet Pattern
**File:** `ui/components/NodeBrowserBottomSheet.kt`
- **Lines 40-50:** `ModalBottomSheet` setup with `SheetState`
- **Lines 90-120:** Column layout with title, search, and content
- Uses `rememberModalBottomSheetState(skipPartiallyExpanded = true)` for full expansion

**File:** `ui/components/MetadataBottomSheet.kt`
- **Lines 55-72:** Alternative `ModalBottomSheet` pattern with `WindowInsets`
- Uses `NoOverscrollContainer` for scroll content

### Collapsible Chip Sections (PERFECT REUSE)
**File:** `ui/components/NodeBrowserBottomSheet.kt`
- **Lines 340-430:** `ExpandableFilterChipRow` composable
  - Handles expand/collapse toggle with arrow icons
  - Uses `FlowRow` (expanded) vs `LazyRow` (collapsed)
  - `FilledTonalIconButton` for expand/collapse button
  - `FilterChip` with checkmark icon for selection state
  - Animates content size changes

### Filter Chips
**File:** `ui/screens/PromptEnhancementSettingsScreen.kt`
- **Lines 580-595:** `FilterChip` usage for PromptTag selection
- Already demonstrates multi-select toggle pattern

### Scroll Container
**File:** `ui/components/shared/NoOverscrollContainer.kt`
- Removes overscroll glow for cleaner UI

---

## 2. Data Sources

### Workflow Names
```kotlin
// WorkflowManager.kt (singleton)
WorkflowManager.getAllWorkflows(): List<Workflow>

data class Workflow(
    val id: String,
    val name: String,
    val type: WorkflowType,
    ...
)
```
**Access pattern:** Already used in ViewModels via `WorkflowManager.getWorkflowsByType(type)`

### Model Names (checkpoints + unets)
```kotlin
// ConnectionManager.kt (singleton)
ConnectionManager.modelCache: StateFlow<ModelCache>

data class ModelCache(
    val checkpoints: List<String>,
    val unets: List<String>,
    ...
)
```
**Access pattern:** Already collected in generation ViewModels

---

## 3. Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                  PromptEditorDialog                         │
│                                                             │
│  [Workflow Names] TextField (read-only) [📋 Edit]          │
│  [Model Names] TextField (read-only) [📋 Edit]              │
│                                                             │
└────────────────────────┬────────────────────────────────────┘
                         │ onClick
                         ▼
┌─────────────────────────────────────────────────────────────┐
│           TagPickerBottomSheet (NEW)                        │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  📋 Workflow Types (collapsible)                     │   │
│  │  [TTI] [ITI-Inpaint] [ITI-Edit] [TTV] [ITV]         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  📄 Workflow Names (collapsible)                     │   │
│  │  [Flux Dev] [SDXL Lightning] [Illustrious v2] ...   │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  🤖 Model Names (collapsible)                        │   │
│  │  [flux1-dev.safetensors] [sd_xl_base_1.0] ...       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  [Done]                                                     │
└─────────────────────────────────────────────────────────────┘
                         │
        ┌────────────────┴────────────────┐
        ▼                                 ▼
WorkflowManager.getAllWorkflows()   ConnectionManager.modelCache
```

---

## 4. New Components Needed

### 4.1 TagPickerBottomSheet.kt
**Location:** `ui/components/TagPickerBottomSheet.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerBottomSheet(
    selectedWorkflowNames: Set<String>,
    selectedModelNames: Set<String>,
    onWorkflowNamesChanged: (Set<String>) -> Unit,
    onModelNamesChanged: (Set<String>) -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit
)
```

**Structure:**
1. Header: Title "Select Associations"
2. Collapsible Section: PromptTag types (already have enum)
3. Collapsible Section: Workflow names (from WorkflowManager)
4. Collapsible Section: Model names (from ModelCache — checkpoints + unets)
5. Done button

**Reuses:**
- `ExpandableFilterChipRow` directly from NodeBrowserBottomSheet (extract to shared component)
- `ModalBottomSheet` pattern
- `FilterChip` with selection state
- `NoOverscrollContainer`

### 4.2 Extract ExpandableFilterChipRow
**Location:** `ui/components/shared/ExpandableFilterChipRow.kt`

Extract the existing `ExpandableFilterChipRow` from `NodeBrowserBottomSheet.kt` (lines 340-430) into a shared component. This is a straightforward copy with no modifications needed.

---

## 5. Implementation Steps

### Step 1: Extract ExpandableFilterChipRow (~60 lines)
**File:** `ui/components/shared/ExpandableFilterChipRow.kt`

1. Copy `ExpandableFilterChipRow` from `NodeBrowserBottomSheet.kt` (lines 340-430)
2. Add `selectedOptions: Set<String>` parameter for multi-select (current is single-select)
3. Modify click handler to toggle set membership
4. Update `NodeBrowserBottomSheet.kt` to import from shared location

**Changes:**
- Add `isMultiSelect: Boolean = false` parameter
- When `isMultiSelect`, use `selectedOptions: Set<String>` instead of `selectedOption: String?`

### Step 2: Create TagPickerBottomSheet (~180 lines)
**File:** `ui/components/TagPickerBottomSheet.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagPickerBottomSheet(
    // Input: current selections
    selectedWorkflowNames: Set<String>,
    selectedModelNames: Set<String>,
    // Callbacks
    onWorkflowNamesChanged: (Set<String>) -> Unit,
    onModelNamesChanged: (Set<String>) -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    // Collect available options
    val workflows = remember { WorkflowManager.getAllWorkflows() }
    val modelCache by ConnectionManager.modelCache.collectAsState()
    
    // Expansion state
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
            
            NoOverscrollContainer(modifier = Modifier.weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section 1: Workflow Names
                    if (workflows.isNotEmpty()) {
                        CollapsibleSection(
                            title = stringResource(R.string.label_workflow_names),
                            options = workflows.map { it.name },
                            selectedOptions = selectedWorkflowNames,
                            onSelectionChanged = onWorkflowNamesChanged,
                            expanded = workflowsExpanded,
                            onExpandedChanged = { workflowsExpanded = it }
                        )
                    }
                    
                    // Section 2: Model Names (checkpoints + unets)
                    val modelNames = remember(modelCache) {
                        (modelCache.checkpoints + modelCache.unets).distinct().sorted()
                    }
                    if (modelNames.isNotEmpty()) {
                        CollapsibleSection(
                            title = stringResource(R.string.label_model_names),
                            options = modelNames,
                            selectedOptions = selectedModelNames,
                            onSelectionChanged = onModelNamesChanged,
                            expanded = modelsExpanded,
                            onExpandedChanged = { modelsExpanded = it }
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

@Composable
private fun CollapsibleSection(
    title: String,
    options: List<String>,
    selectedOptions: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit
) {
    Column {
        // Section header
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
                    label = { Text("${selectedOptions.size} selected") },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Chips (reuse ExpandableFilterChipRow with multi-select)
        ExpandableFilterChipRow(
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
            onExpandedChange = onExpandedChanged,
            isMultiSelect = true
        )
    }
}
```

### Step 3: Modify PromptEditorDialog (~50 lines changed)
**File:** `ui/screens/PromptEnhancementSettingsScreen.kt`

Replace the text fields (lines 618-635) with read-only displays + edit buttons:

```kotlin
// Before (lines 618-635):
OutlinedTextField(
    value = workflowNamesText,
    onValueChange = { workflowNamesText = it },
    label = { Text(stringResource(R.string.label_workflow_names)) },
    ...
)
OutlinedTextField(
    value = modelNamesText,
    onValueChange = { modelNamesText = it },
    label = { Text(stringResource(R.string.label_model_names)) },
    ...
)

// After:
var showTagPicker by remember { mutableStateOf(false) }
var workflowNames by remember { mutableStateOf(prompt.workflowNames) }
var modelNames by remember { mutableStateOf(prompt.modelNames) }

// Workflow Names row
AssociationField(
    label = stringResource(R.string.label_workflow_names),
    values = workflowNames,
    placeholder = stringResource(R.string.placeholder_all_workflows),
    onClick = { showTagPicker = true }
)

// Model Names row  
AssociationField(
    label = stringResource(R.string.label_model_names),
    values = modelNames,
    placeholder = stringResource(R.string.placeholder_all_models),
    onClick = { showTagPicker = true }
)

// Bottom sheet
if (showTagPicker) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    TagPickerBottomSheet(
        selectedWorkflowNames = workflowNames,
        selectedModelNames = modelNames,
        onWorkflowNamesChanged = { workflowNames = it },
        onModelNamesChanged = { modelNames = it },
        sheetState = sheetState,
        onDismiss = { showTagPicker = false }
    )
}
```

### Step 4: Add AssociationField helper (~40 lines)
**Location:** Same file or shared components

```kotlin
@Composable
private fun AssociationField(
    label: String,
    values: Set<String>,
    placeholder: String,
    onClick: () -> Unit
) {
    OutlinedTextField(
        value = if (values.isEmpty()) "" else values.joinToString(", "),
        onValueChange = { },
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        readOnly = true,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        trailingIcon = {
            IconButton(onClick = onClick) {
                Icon(Icons.Default.Edit, contentDescription = null)
            }
        }
    )
}
```

### Step 5: Add String Resources (~5 lines)
**File:** `res/values/strings.xml`

```xml
<string name="title_select_associations">Select Associations</string>
<string name="placeholder_all_workflows">All workflows (tap to filter)</string>
<string name="placeholder_all_models">All models (tap to filter)</string>
<string name="button_done">Done</string>
```

---

## 6. Estimated Line Count

| Component | Lines | Type |
|-----------|-------|------|
| `ExpandableFilterChipRow.kt` (extract) | ~90 | Refactor |
| `TagPickerBottomSheet.kt` | ~180 | New |
| `PromptEditorDialog` changes | ~50 | Modify |
| `AssociationField` helper | ~40 | New |
| String resources | ~5 | New |
| `NodeBrowserBottomSheet.kt` import update | ~5 | Modify |
| **Total** | **~370** | |

---

## 7. Gotchas & Constraints

### Performance
- **Large model lists:** ModelCache can have 100+ models. Use `LazyRow` in collapsed mode (already in ExpandableFilterChipRow).
- **Don't collect in composable:** Use `remember {}` for workflow list, not in recomposition path.

### State Management
- **Sheet state:** Must use `rememberModalBottomSheetState()` at the call site, not inside the composable.
- **Selection state hoisting:** Selections should be hoisted to `PromptEditorDialog` level so they persist when sheet closes/reopens.

### UX Considerations
- **Empty states:** Show helpful text when no workflows/models are loaded (server not connected).
- **Search:** Consider adding search field for large model lists (can reuse pattern from NodeBrowserBottomSheet).
- **Clear all:** Add a "Clear" button to quickly deselect all in a section.

### Compatibility
- **Compose Material 3:** Uses `ModalBottomSheet` which requires `@OptIn(ExperimentalMaterial3Api::class)`.
- **ExpandableFilterChipRow:** Currently uses `ExperimentalMaterial3ExpressiveApi` for `FilledTonalIconButton`.

### Data Consistency
- **Workflow names vs IDs:** Currently stores workflow names (strings). If a workflow is renamed, the association breaks. Consider storing IDs instead, but this requires migration. For now, keep using names for backward compatibility.

---

## 8. Future Enhancements (Out of Scope)

1. **Search within sections:** Add search field for filtering chips
2. **Model grouping:** Group models by type (checkpoint, unet, lora)
3. **Regex patterns:** Allow pattern matching like `flux*` instead of exact names
4. **Workflow ID storage:** Migrate to IDs for more robust associations

---

## 9. Testing Checklist

- [ ] Bottom sheet opens when edit button clicked
- [ ] Selections persist when sheet is dismissed and reopened
- [ ] Empty workflow/model lists show helpful message
- [ ] Large lists scroll properly in collapsed/expanded modes
- [ ] Expand/collapse animation is smooth
- [ ] Selected count badge updates correctly
- [ ] Saved prompt includes correct workflow/model names
- [ ] Back button dismisses sheet properly
- [ ] Works on both phone and tablet layouts
