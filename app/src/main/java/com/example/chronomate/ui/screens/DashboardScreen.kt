package com.example.chronomate.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chronomate.R
import com.example.chronomate.model.ChronoData
import com.example.chronomate.ui.components.HeroSection
import com.example.chronomate.ui.components.ShotChart
import com.example.chronomate.ui.components.StatsGrid
import com.example.chronomate.viewmodel.ChronoViewModel

@Composable
fun DashboardScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(R.string.bb_weight_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        val weights = listOf(0.20f, 0.23f, 0.25f, 0.28f, 0.30f, 0.32f, 0.36f, 0.40f, 0.43f, 0.45f)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(weights) { weight ->
                FilterChip(
                    selected = data.selectedWeight == weight,
                    onClick = { viewModel.setWeight(weight) },
                    label = { Text("%.2f".format(weight)) }
                )
            }
        }

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    HeroSection(data = data)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatsGrid(data = data)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = stringResource(R.string.velocity_trend),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    ShotChart(shots = data.shots, modifier = Modifier.height(250.dp).fillMaxWidth())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                HeroSection(data = data)
                Spacer(modifier = Modifier.height(12.dp))
                StatsGrid(data = data)
                Spacer(modifier = Modifier.height(16.dp))
                if (data.shots.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.velocity_trend),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ShotChart(shots = data.shots, modifier = Modifier.height(200.dp).fillMaxWidth())
                } else {
                    Box(modifier = Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_shots_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
