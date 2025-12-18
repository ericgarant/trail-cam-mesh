package com.trailcam.mesh.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages image display settings (flip/mirror) for mesh nodes with persistent storage
 */
class NodeImageSettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "node_image_settings"
        private const val KEY_VFLIP_PREFIX = "vflip_node_"
        private const val KEY_HMIRROR_PREFIX = "hmirror_node_"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _nodeImageSettings = MutableStateFlow<Map<Int, ImageSettings>>(loadAllSettings())
    val nodeImageSettings: StateFlow<Map<Int, ImageSettings>> = _nodeImageSettings.asStateFlow()
    
    private fun loadAllSettings(): Map<Int, ImageSettings> {
        val settings = mutableMapOf<Int, ImageSettings>()
        
        // Find all node IDs from stored preferences
        val nodeIds = mutableSetOf<Int>()
        prefs.all.keys.forEach { key ->
            when {
                key.startsWith(KEY_VFLIP_PREFIX) -> {
                    val nodeId = key.removePrefix(KEY_VFLIP_PREFIX).toIntOrNull()
                    nodeId?.let { nodeIds.add(it) }
                }
                key.startsWith(KEY_HMIRROR_PREFIX) -> {
                    val nodeId = key.removePrefix(KEY_HMIRROR_PREFIX).toIntOrNull()
                    nodeId?.let { nodeIds.add(it) }
                }
            }
        }
        
        // Load settings for each node
        nodeIds.forEach { nodeId ->
            val vflip = prefs.getBoolean("$KEY_VFLIP_PREFIX$nodeId", false)
            val hmirror = prefs.getBoolean("$KEY_HMIRROR_PREFIX$nodeId", false)
            settings[nodeId] = ImageSettings(vflip, hmirror)
        }
        
        return settings
    }
    
    fun setImageSettings(nodeId: Int, settings: ImageSettings) {
        prefs.edit()
            .putBoolean("$KEY_VFLIP_PREFIX$nodeId", settings.vflip)
            .putBoolean("$KEY_HMIRROR_PREFIX$nodeId", settings.hmirror)
            .apply()
        _nodeImageSettings.value = loadAllSettings()
    }
    
    fun setVFlip(nodeId: Int, vflip: Boolean) {
        val current = getImageSettings(nodeId)
        setImageSettings(nodeId, current.copy(vflip = vflip))
    }
    
    fun setHMirror(nodeId: Int, hmirror: Boolean) {
        val current = getImageSettings(nodeId)
        setImageSettings(nodeId, current.copy(hmirror = hmirror))
    }
    
    fun getImageSettings(nodeId: Int): ImageSettings {
        return _nodeImageSettings.value[nodeId] ?: ImageSettings()
    }
}



