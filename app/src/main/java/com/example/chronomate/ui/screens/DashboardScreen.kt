package com.example.chronomate.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chronomate.R
import com.example.chronomate.model.ChronoData
import com.example.chronomate.model.WeightType
import com.example.chronomate.ui.components.HeroSection
import com.example.chronomate.ui.components.ShotChart
import com.example.chronomate.ui.components.StatsGrid
import com.example.chronomate.ui.components.WeightChipRow
import com.example.chronomate.model.availableWeights
import com.example.chronomate.model.isSelected
import com.example.chronomate.viewmodel.ChronoViewModel

@Composable
fun DashboardScreen(data: ChronoData, viewModel: ChronoViewModel) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollState = rememberScrollState()

    val weights = data.availableWeights()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${data.chronoType.displayName} shot chronograph",
            style = MaterialTheme.typography.titleLarge,
            color = if (MaterialTheme.colorScheme.surface == Color.White || MaterialTheme.colorScheme.surface.red > 0.9f) 
                Color(0xFF2E7D32) else Color(0xFF88FF11),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (data.ballisticMode == com.example.chronomate.model.BallisticMode.PCP) stringResource(R.string.diablo_weight_label) else stringResource(R.string.bb_weight_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            if (weights.isEmpty()) {
                Text(
                    stringResource(R.string.no_custom_weights_dashboard),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                WeightChipRow(
                    options = weights,
                    isSelected = { data.isSelected(it) },
                    onSelect = { viewModel.selectWeightOption(it) }
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
