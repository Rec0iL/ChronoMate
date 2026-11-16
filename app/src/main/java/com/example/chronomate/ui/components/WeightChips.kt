package com.example.chronomate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chronomate.model.WeightOption

/**
 * The weight chips (loaded weights of the active type, then custom weights when enabled).
 * [wrap] = true lays them out on several lines (Settings); false scrolls sideways (Dashboard,
 * Trajectory). Custom weights carry a small star so they stand apart from the standard ones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightChipRow(
    options: List<WeightOption>,
    isSelected: (WeightOption) -> Boolean,
    onSelect: (WeightOption) -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = false
) {
    @Composable
    fun Chip(option: WeightOption) {
        FilterChip(
            selected = isSelected(option),
            onClick = { onSelect(option) },
            label = { Text(option.label) },
            leadingIcon = if (option.isCustom) {
                { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) }
            } else null
        )
    }

    if (wrap) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { Chip(it) }
        }
    } else {
        LazyRow(
            modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(options, key = { i, o -> "$i-${o.label}" }) { _, option -> Chip(option) }
        }
    }
}
