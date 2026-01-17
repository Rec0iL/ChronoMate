package com.example.chronomate.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.chronomate.model.ChronoData
import com.example.chronomate.ui.components.ShotRow

@Composable
fun HistoryScreen(data: ChronoData) {
    if (data.shots.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No history available", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(data.shots.asReversed().mapIndexed { index, shot -> index to shot }) { (reversedIndex, shot) ->
                val index = data.shots.size - reversedIndex
                ShotRow(shot = shot, index = index)
            }
        }
    }
}
