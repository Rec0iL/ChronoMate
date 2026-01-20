package com.example.chronomate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chronomate.model.Screen
import com.example.chronomate.ui.components.LogoImage
import com.example.chronomate.ui.components.StatusBadge
import com.example.chronomate.ui.screens.DashboardScreen
import com.example.chronomate.ui.screens.HistoryScreen
import com.example.chronomate.ui.screens.OrgaChronoScreen
import com.example.chronomate.ui.screens.BallisticsScreen
import com.example.chronomate.ui.screens.SettingsScreen
import com.example.chronomate.ui.screens.ExportScreen
import com.example.chronomate.ui.theme.ChronoMateTheme
import com.example.chronomate.viewmodel.ChronoViewModel
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Load and apply language before super.onCreate and setContent
        val prefs = getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "en") ?: "en"
        
        // Handle potential legacy codes like zh-rTW or zh-rCN
        val languageTag = when(lang) {
            "zh-rTW" -> "zh-TW"
            "zh-rCN" -> "zh-CN"
            else -> lang
        }
        
        val locale = if (languageTag.contains("-")) {
            val parts = languageTag.split("-")
            Locale(parts[0], parts[1])
        } else {
            Locale(languageTag)
        }
        
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel: ChronoViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ChronoViewModel(context) as T
                }
            })
            val data by viewModel.uiState.collectAsStateWithLifecycle()

            ChronoMateTheme(darkTheme = data.isDarkMode) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions.values.all { it }) {
                        viewModel.connectToChronoWifi(context)
                    }
                }

                LaunchedEffect(Unit) {
                    val requiredPermissions = arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_WIFI_STATE,
                        Manifest.permission.CHANGE_NETWORK_STATE
                    )
                    
                    val allGranted = requiredPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (allGranted) {
                        if (data.wifiStatus == "Disconnected") {
                            viewModel.connectToChronoWifi(context)
                        }
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChronoApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronoApp(viewModel: ChronoViewModel) {
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var currentRoute by rememberSaveable { mutableStateOf(Screen.Dashboard.route) }
    
    val screens = listOf(
        Screen.Dashboard, 
        Screen.OrgaChrono, 
        Screen.Trajectory, 
        Screen.History, 
        Screen.Export,
        Screen.Settings
    )
    val currentScreen = screens.find { it.route == currentRoute } ?: Screen.Dashboard
    
    val context = LocalContext.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        LogoImage(modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        "ChronoMate",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    screens.forEach { screen ->
                        NavigationDrawerItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.titleResId)) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                currentRoute = screen.route
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                LogoImage(modifier = Modifier.fillMaxSize())
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(currentScreen.titleResId), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(data.wifiStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        StatusBadge(
                            isConnected = data.isConnected,
                            wifiStatus = data.wifiStatus,
                            onClick = { viewModel.connectToChronoWifi(context) }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (currentScreen) {
                    Screen.Dashboard -> DashboardScreen(data, viewModel)
                    Screen.OrgaChrono -> OrgaChronoScreen(data, viewModel)
                    Screen.Trajectory -> BallisticsScreen(data, viewModel)
                    Screen.History -> HistoryScreen(data)
                    Screen.Export -> ExportScreen(data, viewModel)
                    Screen.Settings -> SettingsScreen(data, viewModel)
                }
            }
        }
    }
}
