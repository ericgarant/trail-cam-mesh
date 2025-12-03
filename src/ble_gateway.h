#ifndef BLE_GATEWAY_H
#define BLE_GATEWAY_H

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <vector>
#include "config.h"
#include "message_protocol.h"

// BLE connection state
enum class BleState {
    DISCONNECTED,
    CONNECTED,
    ADVERTISING
};

// Image reception state for reassembly
struct ImageReception {
    uint16_t imageId;
    uint16_t sourceNode;
    uint32_t totalSize;
    uint16_t totalChunks;
    uint16_t receivedChunks;
    uint8_t* buffer;
    uint32_t startTime;
    bool complete;
    bool active;
};

// Callback types
typedef void (*BleConnectCallback)(bool connected);
typedef void (*BleCommandCallback)(uint8_t command, const uint8_t* data, size_t length);

class BleGateway : public BLEServerCallbacks, public BLECharacteristicCallbacks {
public:
    BleGateway();
    ~BleGateway();
    
    // Initialize BLE (only on gateway devices)
    bool begin();
    
    // Stop BLE
    void stop();
    
    // Update loop
    void update();
    
    // Check connection state
    BleState getState();
    bool isConnected();
    
    // Send notifications to phone
    bool notifyMotionAlert(uint16_t nodeId, uint32_t timestamp, bool hasImage);
    bool notifyStatus(uint16_t nodeId, uint8_t battery, int8_t rssi, uint8_t meshNodes);
    
    // Send image data to phone (chunked)
    bool sendImageToPhone(const uint8_t* imageData, size_t length, uint16_t nodeId, uint16_t imageId);
    
    // Handle incoming image from mesh for forwarding to phone
    void handleImageStart(uint16_t sourceNode, uint16_t imageId, uint32_t size, uint16_t chunks);
    void handleImageChunk(uint16_t sourceNode, uint16_t imageId, uint16_t chunkIndex, const uint8_t* data, uint8_t size);
    void handleImageEnd(uint16_t sourceNode, uint16_t imageId);
    
    // Set callbacks
    void setConnectCallback(BleConnectCallback callback);
    void setCommandCallback(BleCommandCallback callback);
    
    // BLE Callbacks (inherited)
    void onConnect(BLEServer* server) override;
    void onDisconnect(BLEServer* server) override;
    void onWrite(BLECharacteristic* characteristic) override;

private:
    void startAdvertising();
    void sendImageChunkToBle(const uint8_t* data, size_t length, uint16_t chunkIndex, uint16_t totalChunks);
    
    // BLE objects
    BLEServer* _server;
    BLEService* _service;
    BLECharacteristic* _motionChar;
    BLECharacteristic* _imageChar;
    BLECharacteristic* _statusChar;
    BLECharacteristic* _commandChar;
    BLEAdvertising* _advertising;
    
    // State
    BleState _state;
    bool _initialized;
    
    // Image reception from mesh
    ImageReception _imageReception;
    
    // Callbacks
    BleConnectCallback _connectCallback;
    BleCommandCallback _commandCallback;
    
    // Reconnect handling
    unsigned long _disconnectTime;
    static const unsigned long RECONNECT_DELAY = 500;
};

// Global instance (only used on gateway)
extern BleGateway bleGateway;

#endif // BLE_GATEWAY_H


