/**
 * ESP32 Trail Camera Mesh Network
 * 
 * Deer hunting trail camera system using ESP-NOW mesh networking.
 * Detects motion via PIR sensor, captures images, and relays data
 * through the mesh to a gateway device connected to an Android phone via BLE.
 * 
 * Hardware: Freenove ESP32-WROVER CAM
 * 
 * Configuration:
 *   - Edit include/config.h to set DEVICE_ID and DEVICE_ROLE
 *   - ROLE_SENSOR: Standard camera node
 *   - ROLE_GATEWAY: Gateway node with BLE for phone connection
 */

#include <Arduino.h>
#include "config.h"
#include "pir_sensor.h"
#include "camera.h"
#include "led_indicator.h"
#include "mesh_network.h"
#include "message_protocol.h"

#if DEVICE_ROLE == ROLE_GATEWAY
#include "ble_gateway.h"
#endif

// ============================================================================
// Global State
// ============================================================================

static uint16_t imageCounter = 0;
static bool motionPending = false;
static uint32_t motionTimestamp = 0;

// ============================================================================
// Callback Functions
// ============================================================================

/**
 * Called when PIR sensor detects motion
 */
void onMotionDetected() {
    DEBUG_PRINTLN("[MAIN] Motion detected!");
    
    // Flash LED to indicate motion
    ledIndicator.flash(3, 100, 100);
    
    // Set flag for main loop to handle
    motionPending = true;
    motionTimestamp = millis();
}

/**
 * Handle motion detection in main loop
 */
void handleMotion() {
    if (!motionPending) {
        return;
    }
    
    motionPending = false;
    
    DEBUG_PRINTLN("[MAIN] Processing motion event...");
    
    // Set LED to transmit pattern
    ledIndicator.setPattern(LedPattern::BLINK_TRANSMIT);
    
    // Capture image
    bool hasImage = false;
    uint16_t imageId = 0;
    
    if (camera.capture()) {
        hasImage = true;
        imageId = ++imageCounter;
        
        DEBUG_PRINTF("[MAIN] Image captured: %u bytes, ID: %d\n", 
            camera.getImageLength(), imageId);
    } else {
        DEBUG_PRINTLN("[MAIN] Image capture failed");
    }
    
    #if DEVICE_ROLE == ROLE_GATEWAY
    // Gateway sends directly to phone via BLE
    if (bleGateway.isConnected()) {
        // Send motion alert directly to phone
        bleGateway.notifyMotionAlert(DEVICE_ID, motionTimestamp, hasImage);
        
        // Send image directly to phone if captured
        if (hasImage) {
            bleGateway.sendImageToPhone(
                camera.getImageData(),
                camera.getImageLength(),
                DEVICE_ID,
                imageId
            );
        }
    }
    // Also broadcast to mesh so other nodes know
    meshNetwork.sendHeartbeat();
    #else
    // Sensor nodes send via mesh to gateway
    meshNetwork.sendMotionAlert(motionTimestamp, imageId, hasImage);
    
    // Send image if captured
    if (hasImage) {
        meshNetwork.sendImage(
            camera.getImageData(),
            camera.getImageLength(),
            imageId
        );
    }
    #endif
    
    // Release camera buffer
    if (hasImage) {
        camera.releaseFrame();
    }
    
    // Return LED to slow blink (idle)
    ledIndicator.setPattern(LedPattern::BLINK_SLOW);
    
    DEBUG_PRINTLN("[MAIN] Motion event processed");
}

/**
 * Called when a mesh message is received
 */
void onMeshMessage(const MeshMessage& msg) {
    MessageType type = static_cast<MessageType>(msg.header.messageType);
    
    DEBUG_PRINTF("[MAIN] Mesh message received: type=%d, from=%d\n",
        msg.header.messageType, msg.header.sourceId);
    
    switch (type) {
        case MessageType::MOTION_ALERT: {
            MotionAlertPayload* payload = (MotionAlertPayload*)msg.payload;
            DEBUG_PRINTF("[MAIN] Motion alert from node %d, hasImage=%d\n",
                msg.header.sourceId, payload->hasImage);
            
            #if DEVICE_ROLE == ROLE_GATEWAY
            // Forward to phone via BLE
            bleGateway.notifyMotionAlert(
                msg.header.sourceId,
                payload->timestamp,
                payload->hasImage
            );
            #endif
            break;
        }
        
        case MessageType::IMAGE_START: {
            #if DEVICE_ROLE == ROLE_GATEWAY
            ImageStartPayload* payload = (ImageStartPayload*)msg.payload;
            bleGateway.handleImageStart(
                msg.header.sourceId,
                payload->imageId,
                payload->totalSize,
                payload->totalChunks
            );
            #endif
            break;
        }
        
        case MessageType::IMAGE_CHUNK: {
            #if DEVICE_ROLE == ROLE_GATEWAY
            // Extract chunk data
            uint16_t imageId = msg.payload[0] | (msg.payload[1] << 8);
            uint16_t chunkIndex = msg.payload[2] | (msg.payload[3] << 8);
            bleGateway.handleImageChunk(
                msg.header.sourceId,
                imageId,
                chunkIndex,
                msg.payload + 4,
                msg.payloadLength - 4
            );
            #endif
            break;
        }
        
        case MessageType::IMAGE_END: {
            #if DEVICE_ROLE == ROLE_GATEWAY
            uint16_t imageId = msg.payload[0] | (msg.payload[1] << 8);
            bleGateway.handleImageEnd(msg.header.sourceId, imageId);
            #endif
            break;
        }
        
        case MessageType::STATUS_REQUEST: {
            // Send status response
            // TODO: Implement status response
            break;
        }
        
        default:
            break;
    }
}

/**
 * Called when a new mesh node is discovered
 */
void onNodeDiscovered(const MeshNode& node) {
    DEBUG_PRINTF("[MAIN] Node discovered: ID=%d, Gateway=%s, RSSI=%d\n",
        node.nodeId, node.isGateway ? "YES" : "NO", node.rssi);
    
    // Quick flash to indicate new node
    ledIndicator.flash(1, 50, 50);
    
    // Notify phone via BLE (gateway only)
    #if DEVICE_ROLE == ROLE_GATEWAY
    if (bleGateway.isConnected()) {
        bleGateway.notifyStatus(node.nodeId, 100, node.rssi, meshNetwork.getNodes().size());
        DEBUG_PRINTLN("[MAIN] Sent node status to phone");
    }
    #endif
}

#if DEVICE_ROLE == ROLE_GATEWAY
/**
 * Called when BLE connection state changes
 */
void onBleConnect(bool connected) {
    DEBUG_PRINTF("[MAIN] BLE %s\n", connected ? "connected" : "disconnected");
    
    if (connected) {
        ledIndicator.setPattern(LedPattern::ON);
        delay(500);
        ledIndicator.setPattern(LedPattern::BLINK_SLOW);
    }
}

/**
 * Called when a command is received from the phone via BLE
 */
void onBleCommand(uint8_t command, const uint8_t* data, size_t length) {
    DEBUG_PRINTF("[MAIN] BLE command received: 0x%02X\n", command);
    
    switch (command) {
        case 0x01:  // Request status
            // TODO: Broadcast status request to mesh
            break;
            
        case 0x02:  // Force capture on specific node
            if (length >= 2) {
                uint16_t nodeId = data[0] | (data[1] << 8);
                DEBUG_PRINTF("[MAIN] Force capture request for node %d\n", nodeId);
                // TODO: Send capture command to node
            }
            break;
            
        case 0x03:  // Ping mesh
            meshNetwork.sendHeartbeat();
            // Send gateway's own status first
            {
                auto& nodes = meshNetwork.getNodes();
                uint8_t totalNodes = nodes.size() + 1;  // Include gateway itself
                
                // Send gateway status (device ID 1 is typically the gateway)
                bleGateway.notifyStatus(DEVICE_ID, 100, 0, totalNodes);
                delay(50);
                
                // Send all known sensor nodes to phone
                DEBUG_PRINTF("[MAIN] Sending %d nodes to phone\n", nodes.size());
                for (auto& node : nodes) {
                    bleGateway.notifyStatus(node.nodeId, 100, node.rssi, totalNodes);
                    delay(50);  // Small delay between notifications
                }
            }
            break;
            
        default:
            DEBUG_PRINTLN("[MAIN] Unknown BLE command");
            break;
    }
}
#endif

// ============================================================================
// Setup and Loop
// ============================================================================

void setup() {
    // Initialize serial
    Serial.begin(SERIAL_BAUD);
    delay(1000);
    
    DEBUG_PRINTLN("\n========================================");
    DEBUG_PRINTLN("  ESP32 Trail Camera Mesh Network");
    DEBUG_PRINTLN("========================================");
    DEBUG_PRINTF("Device ID: %d\n", DEVICE_ID);
    DEBUG_PRINTF("Role: %s\n", DEVICE_ROLE == ROLE_GATEWAY ? "GATEWAY" : "SENSOR");
    DEBUG_PRINTLN("----------------------------------------\n");
    
    // Initialize LED first for visual feedback
    ledIndicator.begin();
    ledIndicator.setPattern(LedPattern::BLINK_FAST);
    
    // Initialize camera
    DEBUG_PRINTLN("[MAIN] Initializing camera...");
    if (!camera.begin()) {
        DEBUG_PRINTLN("[MAIN] Camera init failed!");
        ledIndicator.setPattern(LedPattern::BLINK_ERROR);
        while (1) {
            ledIndicator.update();
            delay(10);
        }
    }
    
    // Initialize PIR sensor
    DEBUG_PRINTLN("[MAIN] Initializing PIR sensor...");
    pirSensor.begin();
    pirSensor.setMotionCallback(onMotionDetected);
    
    // Initialize mesh network
    DEBUG_PRINTLN("[MAIN] Initializing mesh network...");
    if (!meshNetwork.begin()) {
        DEBUG_PRINTLN("[MAIN] Mesh init failed!");
        ledIndicator.setPattern(LedPattern::BLINK_ERROR);
        while (1) {
            ledIndicator.update();
            delay(10);
        }
    }
    meshNetwork.setMessageCallback(onMeshMessage);
    meshNetwork.setNodeDiscoveredCallback(onNodeDiscovered);
    
    // Initialize BLE on gateway
    #if DEVICE_ROLE == ROLE_GATEWAY
    DEBUG_PRINTLN("[MAIN] Initializing BLE gateway...");
    if (!bleGateway.begin()) {
        DEBUG_PRINTLN("[MAIN] BLE init failed!");
        // Continue anyway, BLE is not critical
    } else {
        bleGateway.setConnectCallback(onBleConnect);
        bleGateway.setCommandCallback(onBleCommand);
    }
    #endif
    
    // Initialization complete
    ledIndicator.setPattern(LedPattern::BLINK_SLOW);
    DEBUG_PRINTLN("\n[MAIN] Initialization complete!");
    DEBUG_PRINTLN("[MAIN] Waiting for motion...\n");
}

void loop() {
    // Update all modules
    pirSensor.update();
    ledIndicator.update();
    meshNetwork.update();
    
    #if DEVICE_ROLE == ROLE_GATEWAY
    bleGateway.update();
    #endif
    
    // Handle pending motion events
    handleMotion();
    
    // Small delay to prevent watchdog issues
    delay(1);
}

