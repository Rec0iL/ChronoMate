package com.example.chronomate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chronomate.R
import com.example.chronomate.model.ChronoData
import com.example.chronomate.viewmodel.ChronoViewModel

@Composable
fun ExportScreen(data: ChronoData, viewModel: ChronoViewModel) {
    var shotCount by remember { mutableStateOf(data.shots.size.coerceAtMost(10).coerceAtLeast(1)) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.export_session), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.export_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.report_settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.shots_to_include), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = shotCount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Slider(
                    value = shotCount.toFloat(),
                    onValueChange = { shotCount = it.toInt() },
                    valueRange = 1f..data.shots.size.coerceAtLeast(1).toFloat(),
                    steps = (data.shots.size - 1).coerceAtLeast(0)
                )
                
                Text(
                    stringResource(R.string.includes_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { viewModel.exportToPdf(context, shotCount) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = data.shots.isNotEmpty(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.generate_pdf), fontWeight = FontWeight.Bold)
        }

        if (data.shots.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.no_shots_session),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
