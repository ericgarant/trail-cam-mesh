package com.trailcam.mesh.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages custom names for mesh nodes with persistent storage
 */
class NodeNameManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "node_names"
        private const val KEY_PREFIX = "node_"
        
        // Suggested names for quick selection
        val SUGGESTED_NAMES = listOf(
            "Oak Stand",
            "Creek Crossing", 
            "Food Plot",
            "Ridge Line",
            "Pine Thicket",
            "Water Hole",
            "Corn Field",
            "Apple Trees",
            "Trail Junction",
            "Bedding Area",
            "Fence Line",
            "Gateway"
        )
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _nodeNames = MutableStateFlow<Map<Int, String>>(loadAllNames())
    val nodeNames: StateFlow<Map<Int, String>> = _nodeNames.asStateFlow()
    
    private fun loadAllNames(): Map<Int, String> {
        val names = mutableMapOf<Int, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is String) {
                val nodeId = key.removePrefix(KEY_PREFIX).toIntOrNull()
                if (nodeId != null) {
                    names[nodeId] = value
                }
            }
        }
        return names
    }
    
    fun setNodeName(nodeId: Int, name: String) {
        if (name.isBlank()) {
            // Remove custom name
            prefs.edit().remove("$KEY_PREFIX$nodeId").apply()
        } else {
            prefs.edit().putString("$KEY_PREFIX$nodeId", name.trim()).apply()
        }
        _nodeNames.value = loadAllNames()
    }
    
    fun getNodeName(nodeId: Int): String {
        return _nodeNames.value[nodeId] ?: getDefaultName(nodeId)
    }
    
    fun getDefaultName(nodeId: Int): String {
        return if (nodeId == 1) "Gateway" else "Camera $nodeId"
    }
    
    fun hasCustomName(nodeId: Int): Boolean {
        return _nodeNames.value.containsKey(nodeId)
    }
}


