package com.trailcam.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.trailcam.mesh.ui.theme.TrailCamTheme

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
                
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { 
                                Row {
                                    Icon(
                                        imageVector = Icons.Default.Pets,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Trail Cam Mesh")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            actions = {
                                // Connection status indicator
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = when (connectionState) {
                                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                        ConnectionState.SCANNING, ConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (connectionState) {
                                                ConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                                                else -> Icons.Default.BluetoothDisabled
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = when (connectionState) {
                                                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimary
                                                ConnectionState.SCANNING, ConnectionState.CONNECTING -> MaterialTheme.colorScheme.onTertiary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                                label = { Text("Connect") },
                                selected = selectedTab == 0,
                                onClick = { viewModel.selectTab(0) }
                            )
                            NavigationBarItem(
                                icon = { 
                                    BadgedBox(
                                        badge = {
                                            if (motionAlerts.isNotEmpty()) {
                                                Badge { Text("${motionAlerts.size}") }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null)
                                    }
                                },
                                label = { Text("Alerts") },
                                selected = selectedTab == 1,
                                onClick = { viewModel.selectTab(1) }
                            )
                            NavigationBarItem(
                                icon = { 
                                    BadgedBox(
                                        badge = {
                                            if (capturedImages.isNotEmpty()) {
                                                Badge { Text("${capturedImages.size}") }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    }
                                },
                                label = { Text("Images") },
                                selected = selectedTab == 2,
                                onClick = { viewModel.selectTab(2) }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Status") },
                                selected = selectedTab == 3,
                                onClick = { viewModel.selectTab(3) }
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
                                onClearAlerts = { viewModel.clearAlerts() },
                                onDeleteAlert = { viewModel.deleteAlert(it) },
                                modifier = Modifier.padding(padding)
                            )
                            2 -> ImagesScreen(
                                images = capturedImages,
                                selectedImage = selectedImage,
                                nodeImageSettings = nodeImageSettings,
                                onImageClick = { viewModel.selectImage(it) },
                                onDismissImage = { viewModel.selectImage(null) },
                                modifier = Modifier.padding(padding)
                            )
                            3 -> StatusScreen(
                                connectionState = connectionState,
                                nodeStatuses = nodeStatuses,
                                nodeNames = nodeNames,
                                nodeImageSettings = nodeImageSettings,
                                onRequestStatus = { viewModel.requestStatus() },
                                onPingMesh = { viewModel.pingMesh() },
                                onSetNodeName = { nodeId, name -> viewModel.setNodeName(nodeId, name) },
                                onSetImageVFlip = { nodeId, vflip -> viewModel.setImageVFlip(nodeId, vflip) },
                                onSetImageHMirror = { nodeId, hmirror -> viewModel.setImageHMirror(nodeId, hmirror) },
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
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
            text = "Trail Cam Mesh needs Bluetooth and Location permissions to discover and connect to your trail cameras.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}
