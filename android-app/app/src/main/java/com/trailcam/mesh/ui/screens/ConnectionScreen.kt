package com.trailcam.mesh.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trailcam.mesh.data.BleDevice
import com.trailcam.mesh.data.ConnectionState
import com.trailcam.mesh.ui.theme.TrailCamDimens

@Composable
fun ConnectionScreen(
    connectionState: ConnectionState,
    discoveredDevices: List<BleDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    ConnectionSection(
        connectionState = connectionState,
        discoveredDevices = discoveredDevices,
        onStartScan = onStartScan,
        onStopScan = onStopScan,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
        modifier = modifier
            .fillMaxSize()
            .padding(TrailCamDimens.ScreenPadding)
    )
}

@Composable
fun ConnectionSection(
    connectionState: ConnectionState,
    discoveredDevices: List<BleDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status + primary action card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(TrailCamDimens.CardCornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionState) {
                    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                    ConnectionState.SCANNING, ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            ConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                            ConnectionState.SCANNING -> Icons.Default.BluetoothSearching
                            ConnectionState.CONNECTING, ConnectionState.DISCOVERING_SERVICES -> Icons.Default.Bluetooth
                            else -> Icons.Default.BluetoothDisabled
                        },
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED -> "Connected"
                                ConnectionState.SCANNING -> "Scanning..."
                                ConnectionState.CONNECTING -> "Connecting..."
                                ConnectionState.DISCOVERING_SERVICES -> "Discovering services..."
                                else -> "Disconnected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED -> "Gateway connected. Alerts and status updates will appear here."
                                ConnectionState.SCANNING -> "Searching for Gateway. Tap Stop when you're done."
                                ConnectionState.CONNECTING -> "Connecting to Gateway..."
                                ConnectionState.DISCOVERING_SERVICES -> "Finalizing connection to Gateway..."
                                else -> "Tap Scan to find your Gateway."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(TrailCamDimens.ContentSpacingSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (connectionState == ConnectionState.CONNECTED) {
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disconnect")
                        }
                    } else {
                        Button(
                            onClick = if (connectionState == ConnectionState.SCANNING) onStopScan else onStartScan,
                            enabled = connectionState != ConnectionState.CONNECTING
                        ) {
                            if (connectionState == ConnectionState.SCANNING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop Scan")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(TrailCamDimens.ContentSpacingLarge))

        // Discovered Devices
        if (discoveredDevices.isNotEmpty()) {
            Text(
                text = "Discovered devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                discoveredDevices.forEach { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onConnect(device.address) },
                        enabled = connectionState == ConnectionState.DISCONNECTED ||
                                  connectionState == ConnectionState.SCANNING
                    )
                }
            }
        } else if (connectionState != ConnectionState.SCANNING && connectionState != ConnectionState.CONNECTED) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TrailCamDimens.ContentSpacingLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No devices found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Make sure your Trail Cam Gateway is powered on",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BleDevice,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Signal strength indicator
            val signalIcon = when {
                device.rssi > -60 -> Icons.Default.SignalCellular4Bar
                device.rssi > -70 -> Icons.Default.SignalCellular4Bar
                device.rssi > -80 -> Icons.Default.SignalCellular4Bar
                else -> Icons.Default.SignalCellular0Bar
            }
            Icon(
                imageVector = signalIcon,
                contentDescription = "Signal: ${device.rssi} dBm",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


