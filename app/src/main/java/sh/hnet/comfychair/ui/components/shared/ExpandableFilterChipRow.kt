package sh.hnet.comfychair.ui.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Expandable row of filter chips that can be collapsed to a horizontal scrolling row
 * or expanded to a wrapping flow layout.
 *
 * Supports both single-select and multi-select modes.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandableFilterChipRow(
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String?) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    // Only show expand/collapse if there are enough options
    val canExpand = options.size > 3

    if (expanded) {
        // Expanded mode: wrapping flow layout with collapse button first
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Collapse button first (FilledTonalIconButton for visual distinction)
            if (canExpand) {
                FilledTonalIconButton(
                    onClick = { onExpandedChange(false) }
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null
                    )
                }
            }
            options.forEach { option ->
                key(option) {
                    val isSelected = option == selectedOption
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onOptionSelected(if (isSelected) null else option)
                        },
                        label = { androidx.compose.material3.Text(option) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    } else {
        // Collapsed mode: horizontal scrolling with expand button first
        NoOverscrollContainer(modifier = modifier.fillMaxWidth()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Expand button first (FilledTonalIconButton for visual distinction)
                if (canExpand) {
                    item(key = "expand_chip") {
                        FilledTonalIconButton(
                            onClick = { onExpandedChange(true) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    }
                }
                items(
                    items = options,
                    key = { "chip_$it" }
                ) { option ->
                    val isSelected = option == selectedOption
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onOptionSelected(if (isSelected) null else option)
                        },
                        label = { androidx.compose.material3.Text(option) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}

/**
 * Multi-select variant of ExpandableFilterChipRow.
 * Allows multiple options to be selected simultaneously.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandableFilterChipRowMultiSelect(
    options: List<String>,
    selectedOptions: Set<String>,
    onOptionToggled: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    // Only show expand/collapse if there are enough options
    val canExpand = options.size > 3

    if (expanded) {
        // Expanded mode: wrapping flow layout with collapse button first
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Collapse button first
            if (canExpand) {
                FilledTonalIconButton(
                    onClick = { onExpandedChange(false) }
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null
                    )
                }
            }
            options.forEach { option ->
                key(option) {
                    val isSelected = option in selectedOptions
                    FilterChip(
                        selected = isSelected,
                        onClick = { onOptionToggled(option) },
                        label = { androidx.compose.material3.Text(option) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    } else {
        // Collapsed mode: horizontal scrolling with expand button first
        NoOverscrollContainer(modifier = modifier.fillMaxWidth()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Expand button first
                if (canExpand) {
                    item(key = "expand_chip") {
                        FilledTonalIconButton(
                            onClick = { onExpandedChange(true) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    }
                }
                items(
                    items = options,
                    key = { "chip_$it" }
                ) { option ->
                    val isSelected = option in selectedOptions
                    FilterChip(
                        selected = isSelected,
                        onClick = { onOptionToggled(option) },
                        label = { androidx.compose.material3.Text(option) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}
