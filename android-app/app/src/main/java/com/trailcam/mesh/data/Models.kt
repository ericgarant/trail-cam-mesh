package com.trailcam.mesh.data

import java.util.UUID

/**
 * BLE UUIDs matching the ESP32 gateway
 */
object BleUuids {
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val CHAR_MOTION_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    val CHAR_IMAGE_UUID: UUID = UUID.fromString("cba1d466-344c-4be3-ab3f-189f80dd7518")
    val CHAR_STATUS_UUID: UUID = UUID.fromString("2c957792-46f0-4d8c-9a76-3c0e8fb4a4d5")
    val CHAR_COMMAND_UUID: UUID = UUID.fromString("f27b53ad-c63d-49a0-8c0f-9f297e6cc520")
    
    // Client Characteristic Configuration Descriptor for notifications
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

/**
 * Motion alert from a trail camera
 */
data class MotionAlert(
    val nodeId: Int,
    val timestamp: Long,
    val hasImage: Boolean,
    val receivedAt: Long = System.currentTimeMillis()
)

/**
 * Captured image from a trail camera
 */
data class CapturedImage(
    val nodeId: Int,
    val imageId: Int,
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CapturedImage
        return nodeId == other.nodeId && imageId == other.imageId
    }

    override fun hashCode(): Int {
        var result = nodeId
        result = 31 * result + imageId
        return result
    }
}

/**
 * Trail camera node status
 */
data class NodeStatus(
    val nodeId: Int,
    val battery: Int,
    val rssi: Int,
    val meshNodes: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Discovered BLE device
 */
data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)

/**
 * Connection state
 */
enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES
}

/**
 * Image reception state for reassembly
 */
data class ImageReception(
    val nodeId: Int,
    val imageId: Int,
    val totalSize: Int,
    val totalChunks: Int,
    var receivedChunks: Int = 0,
    val buffer: ByteArray = ByteArray(totalSize)
) {
    val isComplete: Boolean
        get() = receivedChunks >= totalChunks
        
    val progress: Float
        get() = if (totalChunks > 0) receivedChunks.toFloat() / totalChunks else 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImageReception
        return nodeId == other.nodeId && imageId == other.imageId
    }

    override fun hashCode(): Int {
        var result = nodeId
        result = 31 * result + imageId
        return result
    }
}

/**
 * Commands that can be sent to the gateway
 */
object Commands {
    const val REQUEST_STATUS: Byte = 0x01
    const val FORCE_CAPTURE: Byte = 0x02
    const val PING_MESH: Byte = 0x03
}


