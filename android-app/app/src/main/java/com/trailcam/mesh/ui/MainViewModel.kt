package com.trailcam.mesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trailcam.mesh.ble.BleManager
import com.trailcam.mesh.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bleManager = BleManager(application)
    val nodeNameManager = NodeNameManager(application)
    
    // UI State
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val discoveredDevices: StateFlow<List<BleDevice>> = bleManager.discoveredDevices
    val motionAlerts: StateFlow<List<MotionAlert>> = bleManager.motionAlerts
    val capturedImages: StateFlow<List<CapturedImage>> = bleManager.capturedImages
    val nodeStatuses: StateFlow<Map<Int, NodeStatus>> = bleManager.nodeStatuses
    val nodeNames: StateFlow<Map<Int, String>> = nodeNameManager.nodeNames
    
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    
    private val _selectedImage = MutableStateFlow<CapturedImage?>(null)
    val selectedImage: StateFlow<CapturedImage?> = _selectedImage.asStateFlow()
    
    fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()
    
    fun startScan() {
        bleManager.startScan()
    }
    
    fun stopScan() {
        bleManager.stopScan()
    }
    
    fun connect(address: String) {
        bleManager.connect(address)
    }
    
    fun disconnect() {
        bleManager.disconnect()
    }
    
    fun selectTab(index: Int) {
        _selectedTab.value = index
    }
    
    fun selectImage(image: CapturedImage?) {
        _selectedImage.value = image
    }
    
    fun requestStatus() {
        bleManager.requestStatus()
    }
    
    fun pingMesh() {
        bleManager.pingMesh()
    }
    
    fun clearAlerts() {
        bleManager.clearAlerts()
    }
    
    fun deleteAlert(alert: MotionAlert) {
        bleManager.deleteAlert(alert)
    }
    
    fun setNodeName(nodeId: Int, name: String) {
        nodeNameManager.setNodeName(nodeId, name)
    }
    
    fun getNodeName(nodeId: Int): String {
        return nodeNameManager.getNodeName(nodeId)
    }
    
    override fun onCleared() {
        bleManager.disconnect()
        super.onCleared()
    }
}
