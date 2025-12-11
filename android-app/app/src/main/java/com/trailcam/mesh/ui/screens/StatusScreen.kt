package com.trailcam.mesh.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.trailcam.mesh.data.ConnectionState
import com.trailcam.mesh.data.ImageSettings
import com.trailcam.mesh.data.NodeNameManager
import com.trailcam.mesh.data.NodeStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatusScreen(
    connectionState: ConnectionState,
    nodeStatuses: Map<Int, NodeStatus>,
    nodeNames: Map<Int, String>,
    nodeImageSettings: Map<Int, ImageSettings>,
    onRequestStatus: () -> Unit,
    onPingMesh: () -> Unit,
    onSetNodeName: (Int, String) -> Unit,
    onSetImageVFlip: (Int, Boolean) -> Unit,
    onSetImageHMirror: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingNodeId by remember { mutableStateOf<Int?>(null) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRequestStatus,
                enabled = connectionState == ConnectionState.CONNECTED,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
            OutlinedButton(
                onClick = onPingMesh,
                enabled = connectionState == ConnectionState.CONNECTED,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Sensors, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ping Mesh")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Gateway status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (connectionState == ConnectionState.CONNECTED)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (connectionState == ConnectionState.CONNECTED)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = nodeNames[1] ?: "Gateway",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (connectionState == ConnectionState.CONNECTED)
                            "Connected via Bluetooth"
                        else
                            "Not connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Node statuses
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mesh Nodes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap to rename",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (nodeStatuses.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DevicesOther,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No nodes detected yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (connectionState == ConnectionState.CONNECTED) {
                        Text(
                            text = "Tap 'Ping Mesh' to discover nodes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodeStatuses.values.toList(), key = { it.nodeId }) { status ->
                    NodeStatusCard(
                        status = status,
                        customName = nodeNames[status.nodeId],
                        onEditClick = { editingNodeId = status.nodeId }
                    )
                }
            }
        }
    }
    
    // Edit name dialog
    editingNodeId?.let { nodeId ->
        val status = nodeStatuses[nodeId]
        val imageSettings = nodeImageSettings[nodeId] ?: ImageSettings()
        NodeNameDialog(
            nodeId = nodeId,
            currentName = nodeNames[nodeId] ?: "",
            imageSettings = imageSettings,
            isGateway = status?.rssi == 0,
            onDismiss = { editingNodeId = null },
            onSave = { name ->
                onSetNodeName(nodeId, name)
                editingNodeId = null
            },
            onImageSettingsChanged = { vflip, hmirror ->
                onSetImageVFlip(nodeId, vflip)
                onSetImageHMirror(nodeId, hmirror)
            }
        )
    }
}

@Composable
private fun NodeStatusCard(
    status: NodeStatus,
    customName: String?,
    onEditClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val isGateway = status.rssi == 0
    val displayName = customName ?: if (isGateway) "Gateway" else "Camera ${status.nodeId}"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isGateway) 
                MaterialTheme.colorScheme.secondaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Node icon
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isGateway) 
                    MaterialTheme.colorScheme.secondary 
                else 
                    MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isGateway) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    } else {
                        Text(
                            text = "${status.nodeId}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (customName != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = if (isGateway) 
                        "Hub device • ${status.meshNodes} node${if (status.meshNodes != 1) "s" else ""} in mesh"
                    else 
                        "Last seen: ${dateFormat.format(Date(status.lastSeen))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Status indicators
            Column(horizontalAlignment = Alignment.End) {
                // Battery
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            status.battery > 75 -> Icons.Default.BatteryFull
                            status.battery > 50 -> Icons.Default.Battery5Bar
                            status.battery > 25 -> Icons.Default.Battery3Bar
                            else -> Icons.Default.Battery1Bar
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when {
                            status.battery > 25 -> MaterialTheme.colorScheme.primary
                            status.battery > 10 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${status.battery}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Signal (hide for gateway)
                if (!isGateway) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellular4Bar,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${status.rssi} dBm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeNameDialog(
    nodeId: Int,
    currentName: String,
    imageSettings: ImageSettings,
    isGateway: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onImageSettingsChanged: (vflip: Boolean, hmirror: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var vflip by remember { mutableStateOf(imageSettings.vflip) }
    var hmirror by remember { mutableStateOf(imageSettings.hmirror) }
    val defaultName = if (isGateway) "Gateway" else "Camera $nodeId"
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Rename ${defaultName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Custom name") },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { name = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Suggested names
                Text(
                    text = "Suggestions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(NodeNameManager.SUGGESTED_NAMES) { suggestion ->
                        SuggestionChip(
                            onClick = { name = suggestion },
                            label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Image display settings
                if (!isGateway) {
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Image Display",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Flip Vertically",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = vflip,
                            onCheckedChange = { 
                                vflip = it
                                onImageSettingsChanged(vflip, hmirror)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mirror Horizontally",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = hmirror,
                            onCheckedChange = { 
                                hmirror = it
                                onImageSettingsChanged(vflip, hmirror)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(name) }
                    ) {
                        Text(if (name.isBlank()) "Reset to Default" else "Save")
                    }
                }
            }
        }
    }
}
