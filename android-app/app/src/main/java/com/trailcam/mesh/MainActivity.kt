package com.trailcam.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.trailcam.mesh.data.ConnectionState
import com.trailcam.mesh.ui.MainViewModel
import com.trailcam.mesh.ui.screens.*
import com.trailcam.mesh.ui.theme.TrailCamDimens
import com.trailcam.mesh.ui.theme.TrailCamStatusColors
import com.trailcam.mesh.ui.theme.TrailCamTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Handle result if needed */ }
    
    private var pendingTabNavigation: Int? = null
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle navigation from notification tap
        intent.getIntExtra("tab", -1).takeIf { it >= 0 }?.let {
            pendingTabNavigation = it
        }
    }
    
    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check initial intent for tab navigation
        intent?.getIntExtra("tab", -1)?.takeIf { it >= 0 }?.let {
            pendingTabNavigation = it
        }
        
        setContent {
            TrailCamTheme {
                val viewModel: MainViewModel = viewModel()
                
                // Handle pending tab navigation from notification
                LaunchedEffect(pendingTabNavigation) {
                    pendingTabNavigation?.let { tab ->
                        viewModel.selectTab(tab)
                        pendingTabNavigation = null
                    }
                }
                
                // Collect states
                val connectionState by viewModel.connectionState.collectAsState()
                val discoveredDevices by viewModel.discoveredDevices.collectAsState()
                val motionAlerts by viewModel.motionAlerts.collectAsState()
                val capturedImages by viewModel.capturedImages.collectAsState()
                val nodeStatuses by viewModel.nodeStatuses.collectAsState()
                val nodeNames by viewModel.nodeNames.collectAsState()
                val nodeImageSettings by viewModel.nodeImageSettings.collectAsState()
                val selectedTab by viewModel.selectedTab.collectAsState()
                val selectedImage by viewModel.selectedImage.collectAsState()
                
                // Permissions
                val permissionsToRequest = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                
                val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)
                
                LaunchedEffect(Unit) {
                    if (!permissionsState.allPermissionsGranted) {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                }
                
                val snackbarHostState = remember { SnackbarHostState() }
                val snackbarScope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                val sectionTitle = when (selectedTab) {
                                    0 -> "Connection"
                                    1 -> "Alerts"
                                    2 -> "Gallery"
                                    else -> "Network Status"
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Pets,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Trail Cam Mesh",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = sectionTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            actions = {
                                // Compact connection status indicator: green when connected, red when not.
                                // Use a colored circular background with a high-contrast icon so it's always visible.
                                val isConnected = connectionState == ConnectionState.CONNECTED
                                Surface(
                                    shape = CircleShape,
                                    color = if (isConnected) {
                                        TrailCamStatusColors.Connected
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isConnected) {
                                            Icons.Default.BluetoothConnected
                                        } else {
                                            Icons.Default.BluetoothDisabled
                                        },
                                        contentDescription = if (isConnected) {
                                            "Connected"
                                        } else {
                                            "Not connected"
                                        },
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            val navBarItemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                                label = { Text("Connect") },
                                selected = selectedTab == 0,
                                onClick = { viewModel.selectTab(0) },
                                colors = navBarItemColors
                            )
                            NavigationBarItem(
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (motionAlerts.isNotEmpty()) {
                                                val count = motionAlerts.size
                                                val label = if (count > 9) "9+" else "$count"
                                                Badge { Text(label) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null)
                                    }
                                },
                                label = { Text("Alerts") },
                                selected = selectedTab == 1,
                                onClick = { viewModel.selectTab(1) },
                                colors = navBarItemColors
                            )
                            NavigationBarItem(
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (capturedImages.isNotEmpty()) {
                                                val count = capturedImages.size
                                                val label = if (count > 9) "9+" else "$count"
                                                Badge { Text(label) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    }
                                },
                                label = { Text("Images") },
                                selected = selectedTab == 2,
                                onClick = { viewModel.selectTab(2) },
                                colors = navBarItemColors
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Status") },
                                selected = selectedTab == 3,
                                onClick = { viewModel.selectTab(3) },
                                colors = navBarItemColors
                            )
                        }
                    }
                ) { padding ->
                    // Check permissions first
                    if (!permissionsState.allPermissionsGranted) {
                        PermissionsScreen(
                            onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() },
                            modifier = Modifier.padding(padding)
                        )
                    } else {
                        // Main content
                        when (selectedTab) {
                            0 -> ConnectionScreen(
                                connectionState = connectionState,
                                discoveredDevices = discoveredDevices,
                                onStartScan = { 
                                    if (!viewModel.isBluetoothEnabled()) {
                                        enableBluetoothLauncher.launch(
                                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                        )
                                    } else {
                                        viewModel.startScan()
                                    }
                                },
                                onStopScan = { viewModel.stopScan() },
                                onConnect = { viewModel.connect(it) },
                                onDisconnect = { viewModel.disconnect() },
                                modifier = Modifier.padding(padding)
                            )
                            1 -> AlertsScreen(
                                alerts = motionAlerts,
                                images = capturedImages,
                                nodeNames = nodeNames,
                                onClearAlerts = {
                                    viewModel.clearAlerts()
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar("Alerts cleared")
                                    }
                                },
                                onDeleteAlert = { alert ->
                                    viewModel.deleteAlert(alert)
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar("Alert deleted")
                                    }
                                },
                                modifier = Modifier.padding(padding)
                            )
                            2 -> ImagesScreen(
                                images = capturedImages,
                                selectedImage = selectedImage,
                                nodeImageSettings = nodeImageSettings,
                                nodeNames = nodeNames,
                                onImageClick = { viewModel.selectImage(it) },
                                onDismissImage = { viewModel.selectImage(null) },
                                modifier = Modifier.padding(padding)
                            )
                            3 -> StatusScreen(
                                connectionState = connectionState,
                                nodeStatuses = nodeStatuses,
                                nodeNames = nodeNames,
                                nodeImageSettings = nodeImageSettings,
                                onRequestStatus = {
                                    viewModel.requestStatus()
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar("Requested node status")
                                    }
                                },
                                onPingMesh = {
                                    viewModel.pingMesh()
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar("Ping sent to mesh")
                                    }
                                },
                                onSetNodeName = { nodeId, name ->
                                    viewModel.setNodeName(nodeId, name)
                                    val displayName = name.ifBlank { "Camera $nodeId" }
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar("Saved name for $displayName")
                                    }
                                },
                                onSetImageVFlip = { nodeId, vflip ->
                                    viewModel.setImageVFlip(nodeId, vflip)
                                },
                                onSetImageHMirror = { nodeId, hmirror ->
                                    viewModel.setImageHMirror(nodeId, hmirror)
                                },
                                modifier = Modifier.padding(padding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsScreen(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMore by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = TrailCamDimens.ScreenPadding,
                vertical = TrailCamDimens.ContentSpacingLarge
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Trail Cam Mesh needs a few permissions so it can discover your gateway, receive alerts, and notify you reliably.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(TrailCamDimens.ContentSpacingSmall)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Bluetooth", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Scan for and connect to your Trail Cam Gateway.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Location", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Required by Android for Bluetooth scanning in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Notifications", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Let you know when motion is detected, even if the app is closed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(TrailCamDimens.ContentSpacingLarge))
        Button(onClick = onRequestPermissions) {
            Text("Enable permissions to continue")
        }
        Spacer(modifier = Modifier.height(TrailCamDimens.ContentSpacingSmall))
        TextButton(onClick = { showMore = !showMore }) {
            Text(if (showMore) "Hide details" else "Learn more")
        }
        AnimatedVisibility(visible = showMore) {
            Text(
                text = "Permissions are only used to talk to your Trail Cam Gateway and deliver motion alerts. You can change them anytime in Android Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = TrailCamDimens.ContentSpacingSmall)
            )
        }
    }
}
