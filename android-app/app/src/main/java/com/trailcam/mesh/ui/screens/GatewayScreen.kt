package com.trailcam.mesh.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.trailcam.mesh.data.BleDevice
import com.trailcam.mesh.data.ConnectionState
import com.trailcam.mesh.data.ImageSettings
import com.trailcam.mesh.data.NodeStatus
import com.trailcam.mesh.ui.theme.TrailCamDimens

@Composable
fun GatewayScreen(
    connectionState: ConnectionState,
    discoveredDevices: List<BleDevice>,
    nodeStatuses: Map<Int, NodeStatus>,
    nodeNames: Map<Int, String>,
    nodeImageSettings: Map<Int, ImageSettings>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRequestStatus: () -> Unit,
    onPingMesh: () -> Unit,
    onSetNodeName: (Int, String) -> Unit,
    onSetImageVFlip: (Int, Boolean) -> Unit,
    onSetImageHMirror: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(TrailCamDimens.ScreenPadding)
    ) {
        // Connection section at the top
        Text(
            text = "Gateway connection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(TrailCamDimens.ContentSpacingSmall))

        ConnectionSection(
            connectionState = connectionState,
            discoveredDevices = discoveredDevices,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            modifier = Modifier.fillMaxWidth()
        )

        // Slightly tighter spacing now that the connection card
        // includes the primary action button.
        Spacer(modifier = Modifier.height(TrailCamDimens.ContentSpacingMedium))

        // Mesh / network section
        Text(
            text = "Mesh nodes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(TrailCamDimens.ContentSpacingSmall))

        StatusSection(
            connectionState = connectionState,
            nodeStatuses = nodeStatuses,
            nodeNames = nodeNames,
            nodeImageSettings = nodeImageSettings,
            onRequestStatus = onRequestStatus,
            onPingMesh = onPingMesh,
            onSetNodeName = onSetNodeName,
            onSetImageVFlip = onSetImageVFlip,
            onSetImageHMirror = onSetImageHMirror,
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}

