package com.trailcam.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.trailcam.mesh.data.*
import com.trailcam.mesh.util.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BLE Manager for connecting to Trail Camera Gateway
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_TIMEOUT_MS = 10000L
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var bleScanner: BluetoothLeScanner? = null
    
    private val handler = Handler(Looper.getMainLooper())
    
    // State flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()
    
    private val _motionAlerts = MutableStateFlow<List<MotionAlert>>(emptyList())
    val motionAlerts: StateFlow<List<MotionAlert>> = _motionAlerts.asStateFlow()
    
    private val _capturedImages = MutableStateFlow<List<CapturedImage>>(emptyList())
    val capturedImages: StateFlow<List<CapturedImage>> = _capturedImages.asStateFlow()
    
    private val _nodeStatuses = MutableStateFlow<Map<Int, NodeStatus>>(emptyMap())
    val nodeStatuses: StateFlow<Map<Int, NodeStatus>> = _nodeStatuses.asStateFlow()
    
    // Image reception state
    private var currentImageReception: ImageReception? = null
    
    // Characteristics
    private var motionCharacteristic: BluetoothGattCharacteristic? = null
    private var imageCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    
    // Callback for new motion alerts
    var onMotionAlert: ((MotionAlert) -> Unit)? = null
    var onImageReceived: ((CapturedImage) -> Unit)? = null
    
    /**
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    
    /**
     * Start scanning for Trail Camera gateways
     */
    fun startScan() {
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }
        
        _connectionState.value = ConnectionState.SCANNING
        _discoveredDevices.value = emptyList()
        
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(BleUuids.SERVICE_UUID))
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        
        // Stop scan after timeout
        handler.postDelayed({
            stopScan()
        }, SCAN_TIMEOUT_MS)
        
        Log.d(TAG, "BLE scan started")
    }
    
    /**
     * Stop scanning
     */
    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        Log.d(TAG, "BLE scan stopped")
    }
    
    /**
     * Connect to a gateway device
     */
    fun connect(address: String) {
        stopScan()
        
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.e(TAG, "Device not found: $address")
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        
        Log.d(TAG, "Connecting to $address")
    }
    
    /**
     * Disconnect from gateway
     */
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        
        motionCharacteristic = null
        imageCharacteristic = null
        statusCharacteristic = null
        commandCharacteristic = null
        
        Log.d(TAG, "Disconnected")
    }
    
    /**
     * Send command to gateway
     */
    fun sendCommand(command: Byte, data: ByteArray = byteArrayOf()) {
        val characteristic = commandCharacteristic ?: return
        val gatt = bluetoothGatt ?: return
        
        val payload = byteArrayOf(command) + data
        characteristic.value = payload
        gatt.writeCharacteristic(characteristic)
        
        Log.d(TAG, "Sent command: ${command.toInt()}")
    }
    
    /**
     * Request status from all nodes
     */
    fun requestStatus() {
        sendCommand(Commands.REQUEST_STATUS)
    }
    
    /**
     * Ping the mesh network
     */
    fun pingMesh() {
        sendCommand(Commands.PING_MESH)
    }
    
    /**
     * Clear all alerts and images
     */
    fun clearAlerts() {
        _motionAlerts.value = emptyList()
        _capturedImages.value = emptyList()
        Log.d(TAG, "Cleared all alerts and images")
    }
    
    /**
     * Delete a specific alert and its associated image
     */
    fun deleteAlert(alert: MotionAlert) {
        // Remove the alert
        val currentAlerts = _motionAlerts.value.toMutableList()
        currentAlerts.removeAll { it.nodeId == alert.nodeId && it.receivedAt == alert.receivedAt }
        _motionAlerts.value = currentAlerts
        
        // Remove associated image (within 5 second window)
        val currentImages = _capturedImages.value.toMutableList()
        currentImages.removeAll { 
            it.nodeId == alert.nodeId && 
            kotlin.math.abs(it.timestamp - alert.receivedAt) < 5000 
        }
        _capturedImages.value = currentImages
        
        Log.d(TAG, "Deleted alert from node ${alert.nodeId}")
    }
    
    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = BleDevice(
                name = result.device.name ?: "Unknown",
                address = result.device.address,
                rssi = result.rssi
            )
            
            val currentDevices = _discoveredDevices.value.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.address == device.address }
            
            if (existingIndex >= 0) {
                currentDevices[existingIndex] = device
            } else {
                currentDevices.add(device)
            }
            
            _discoveredDevices.value = currentDevices
            Log.d(TAG, "Found device: ${device.name} (${device.address})")
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
    
    // GATT callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = ConnectionState.DISCOVERING_SERVICES
                    // Request larger MTU for image transfer (512 bytes)
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu, status: $status")
            // Now discover services after MTU is negotiated
            gatt.discoverServices()
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }
            
            val service = gatt.getService(BleUuids.SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Trail Cam service not found")
                disconnect()
                return
            }
            
            // Get characteristics
            motionCharacteristic = service.getCharacteristic(BleUuids.CHAR_MOTION_UUID)
            imageCharacteristic = service.getCharacteristic(BleUuids.CHAR_IMAGE_UUID)
            statusCharacteristic = service.getCharacteristic(BleUuids.CHAR_STATUS_UUID)
            commandCharacteristic = service.getCharacteristic(BleUuids.CHAR_COMMAND_UUID)
            
            // Enable notifications
            enableNotifications(gatt, motionCharacteristic)
            
            _connectionState.value = ConnectionState.CONNECTED
            Log.d(TAG, "Services discovered and configured")
        }
        
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // Enable next notification after previous one completes
            when (descriptor.characteristic.uuid) {
                BleUuids.CHAR_MOTION_UUID -> enableNotifications(gatt, imageCharacteristic)
                BleUuids.CHAR_IMAGE_UUID -> enableNotifications(gatt, statusCharacteristic)
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                BleUuids.CHAR_MOTION_UUID -> handleMotionNotification(characteristic.value)
                BleUuids.CHAR_IMAGE_UUID -> handleImageNotification(characteristic.value)
                BleUuids.CHAR_STATUS_UUID -> handleStatusNotification(characteristic.value)
            }
        }
    }
    
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        characteristic ?: return
        
        gatt.setCharacteristicNotification(characteristic, true)
        
        val descriptor = characteristic.getDescriptor(BleUuids.CCCD_UUID)
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }
    
    // Track recent alerts for deduplication
    private var lastAlertTime = 0L
    private val GLOBAL_ALERT_COOLDOWN_MS = 3000L // Ignore any alert within 3 seconds of last one
    
    private fun handleMotionNotification(data: ByteArray) {
        if (data.size < 7) return
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val nodeId = buffer.short.toInt() and 0xFFFF
        val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
        val hasImage = buffer.get() != 0.toByte()
        
        // Global deduplication: ignore ANY alert within cooldown window
        // This prevents false gateway alerts triggered by electrical noise during sensor alert processing
        val now = System.currentTimeMillis()
        if ((now - lastAlertTime) < GLOBAL_ALERT_COOLDOWN_MS) {
            Log.d(TAG, "Ignoring alert from node $nodeId (within ${GLOBAL_ALERT_COOLDOWN_MS}ms global cooldown)")
            return
        }
        lastAlertTime = now
        
        val alert = MotionAlert(nodeId, timestamp, hasImage)
        
        val currentAlerts = _motionAlerts.value.toMutableList()
        currentAlerts.add(0, alert) // Add to front
        if (currentAlerts.size > 100) {
            currentAlerts.removeAt(currentAlerts.lastIndex)
        }
        _motionAlerts.value = currentAlerts
        
        // Show push notification
        NotificationHelper.showMotionAlert(context, alert)
        
        onMotionAlert?.invoke(alert)
        
        Log.d(TAG, "Motion alert from node $nodeId, hasImage=$hasImage")
    }
    
    private fun handleImageNotification(data: ByteArray) {
        if (data.isEmpty()) return
        
        val marker = data[0]
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        when (marker.toInt()) {
            0x01 -> { // Image start
                if (data.size < 12) return
                buffer.get() // Skip marker
                val nodeId = buffer.short.toInt() and 0xFFFF
                val imageId = buffer.short.toInt() and 0xFFFF
                val totalSize = buffer.int
                val totalChunks = buffer.short.toInt() and 0xFFFF
                
                currentImageReception = ImageReception(nodeId, imageId, totalSize, totalChunks)
                Log.d(TAG, "Image start: node=$nodeId, id=$imageId, size=$totalSize, chunks=$totalChunks")
            }
            0x00 -> { // Image chunk
                if (data.size < 6) return
                val reception = currentImageReception ?: return
                
                buffer.get() // Skip marker
                val chunkIndex = buffer.short.toInt() and 0xFFFF
                val totalChunks = buffer.short.toInt() and 0xFFFF
                val chunkData = data.copyOfRange(5, data.size)
                
                // Copy chunk data to buffer
                val offset = chunkIndex * 240 // Chunk size from ESP32
                val copyLen = minOf(chunkData.size, reception.buffer.size - offset)
                if (offset < reception.buffer.size && copyLen > 0) {
                    System.arraycopy(chunkData, 0, reception.buffer, offset, copyLen)
                    reception.receivedChunks++
                }
                
                Log.d(TAG, "Image chunk ${chunkIndex + 1}/$totalChunks")
            }
            0x02 -> { // Image end
                val reception = currentImageReception ?: return
                
                if (reception.isComplete || reception.receivedChunks > 0) {
                    val image = CapturedImage(
                        nodeId = reception.nodeId,
                        imageId = reception.imageId,
                        data = reception.buffer.copyOf()
                    )
                    
                    val currentImages = _capturedImages.value.toMutableList()
                    currentImages.add(0, image)
                    if (currentImages.size > 50) {
                        currentImages.removeAt(currentImages.lastIndex)
                    }
                    _capturedImages.value = currentImages
                    
                    onImageReceived?.invoke(image)
                    
                    Log.d(TAG, "Image complete: ${reception.buffer.size} bytes")
                }
                
                currentImageReception = null
            }
        }
    }
    
    private fun handleStatusNotification(data: ByteArray) {
        if (data.size < 5) return
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val nodeId = buffer.short.toInt() and 0xFFFF
        val battery = buffer.get().toInt() and 0xFF
        val rssi = buffer.get().toInt()
        val meshNodes = buffer.get().toInt() and 0xFF
        
        val status = NodeStatus(nodeId, battery, rssi, meshNodes)
        
        val currentStatuses = _nodeStatuses.value.toMutableMap()
        currentStatuses[nodeId] = status
        _nodeStatuses.value = currentStatuses
        
        Log.d(TAG, "Status from node $nodeId: battery=$battery%, rssi=$rssi")
    }
}

