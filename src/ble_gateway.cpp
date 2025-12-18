#include "ble_gateway.h"

// Global instance
BleGateway bleGateway;

BleGateway::BleGateway()
    : _server(nullptr)
    , _service(nullptr)
    , _motionChar(nullptr)
    , _imageChar(nullptr)
    , _statusChar(nullptr)
    , _commandChar(nullptr)
    , _advertising(nullptr)
    , _state(BleState::DISCONNECTED)
    , _initialized(false)
    , _connectCallback(nullptr)
    , _commandCallback(nullptr)
    , _disconnectTime(0) {
    
    // Initialize image reception state
    _imageReception.buffer = nullptr;
    _imageReception.active = false;
    _imageReception.complete = false;
}

BleGateway::~BleGateway() {
    if (_imageReception.buffer) {
        free(_imageReception.buffer);
    }
}

bool BleGateway::begin() {
    // Only initialize on gateway devices
    if (DEVICE_ROLE != ROLE_GATEWAY) {
        DEBUG_PRINTLN("[BLE] Not a gateway device, skipping BLE init");
        return false;
    }
    
    if (_initialized) {
        DEBUG_PRINTLN("[BLE] Already initialized");
        return true;
    }
    
    DEBUG_PRINTLN("[BLE] Initializing BLE Gateway...");
    
    // Initialize BLE
    BLEDevice::init(BLE_DEVICE_NAME);
    BLEDevice::setMTU(BLE_MTU_SIZE);
    
    // Create server
    _server = BLEDevice::createServer();
    _server->setCallbacks(this);
    
    // Create service
    _service = _server->createService(SERVICE_UUID);
    
    // Create characteristics
    
    // Motion Alert characteristic (notify)
    _motionChar = _service->createCharacteristic(
        CHAR_MOTION_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    _motionChar->addDescriptor(new BLE2902());
    
    // Image Data characteristic (notify, for streaming image chunks)
    _imageChar = _service->createCharacteristic(
        CHAR_IMAGE_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    _imageChar->addDescriptor(new BLE2902());
    
    // Status characteristic (read/notify)
    _statusChar = _service->createCharacteristic(
        CHAR_STATUS_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    _statusChar->addDescriptor(new BLE2902());
    
    // Command characteristic (write from phone)
    _commandChar = _service->createCharacteristic(
        CHAR_COMMAND_UUID,
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_WRITE_NR
    );
    _commandChar->setCallbacks(this);
    
    // Start service
    _service->start();
    
    // Start advertising
    startAdvertising();
    
    _initialized = true;
    _state = BleState::ADVERTISING;
    
    DEBUG_PRINTLN("[BLE] Gateway initialized and advertising");
    
    return true;
}

void BleGateway::stop() {
    if (!_initialized) {
        return;
    }
    
    if (_advertising) {
        _advertising->stop();
    }
    
    BLEDevice::deinit();
    _initialized = false;
    _state = BleState::DISCONNECTED;
    
    DEBUG_PRINTLN("[BLE] Gateway stopped");
}

void BleGateway::update() {
    if (!_initialized) {
        return;
    }
    
    // Handle reconnection after disconnect
    if (_state == BleState::DISCONNECTED && _disconnectTime > 0) {
        if (millis() - _disconnectTime >= RECONNECT_DELAY) {
            startAdvertising();
            _disconnectTime = 0;
        }
    }
    
    // Check for image reception timeout
    if (_imageReception.active) {
        if (millis() - _imageReception.startTime > IMG_TRANSFER_TIMEOUT_MS) {
            DEBUG_PRINTLN("[BLE] Image reception timeout");
            if (_imageReception.buffer) {
                free(_imageReception.buffer);
                _imageReception.buffer = nullptr;
            }
            _imageReception.active = false;
        }
    }
}

void BleGateway::startAdvertising() {
    _advertising = BLEDevice::getAdvertising();
    _advertising->addServiceUUID(SERVICE_UUID);
    _advertising->setScanResponse(true);
    _advertising->setMinPreferred(0x06);  // For iPhone
    _advertising->setMinPreferred(0x12);
    _advertising->start();
    
    _state = BleState::ADVERTISING;
    DEBUG_PRINTLN("[BLE] Advertising started");
}

BleState BleGateway::getState() {
    return _state;
}

bool BleGateway::isConnected() {
    return _state == BleState::CONNECTED;
}

void BleGateway::onConnect(BLEServer* server) {
    _state = BleState::CONNECTED;
    DEBUG_PRINTLN("[BLE] Client connected");
    
    if (_connectCallback) {
        _connectCallback(true);
    }
}

void BleGateway::onDisconnect(BLEServer* server) {
    _state = BleState::DISCONNECTED;
    _disconnectTime = millis();
    DEBUG_PRINTLN("[BLE] Client disconnected");
    
    if (_connectCallback) {
        _connectCallback(false);
    }
}

void BleGateway::onWrite(BLECharacteristic* characteristic) {
    if (characteristic == _commandChar) {
        std::string value = characteristic->getValue();
        
        if (value.length() > 0) {
            uint8_t command = value[0];
            const uint8_t* data = (const uint8_t*)value.data() + 1;
            size_t dataLen = value.length() - 1;
            
            DEBUG_PRINTF("[BLE] Received command: 0x%02X, data len: %d\n", command, dataLen);
            
            if (_commandCallback) {
                _commandCallback(command, data, dataLen);
            }
        }
    }
}

bool BleGateway::notifyMotionAlert(uint16_t nodeId, uint32_t timestamp, bool hasImage, const uint16_t* path, uint8_t pathLength) {
    if (!isConnected()) {
        return false;
    }
    
    // Build path with gateway appended (if path provided)
    uint16_t fullPath[MAX_PATH_LENGTH + 1];  // MAX_PATH_LENGTH + 1 for gateway
    uint8_t fullPathLength = 0;
    
    if (path != nullptr && pathLength > 0) {
        // Copy existing path
        uint8_t copyLength = pathLength > MAX_PATH_LENGTH ? MAX_PATH_LENGTH : pathLength;
        memcpy(fullPath, path, copyLength * sizeof(uint16_t));
        fullPathLength = copyLength;
        
        // Append gateway node ID if not already last and there's room
        if (fullPathLength < (MAX_PATH_LENGTH + 1) && 
            (fullPathLength == 0 || fullPath[fullPathLength - 1] != DEVICE_ID)) {
            fullPath[fullPathLength] = DEVICE_ID;
            fullPathLength++;
        }
    } else {
        // No path provided, just include gateway
        fullPath[0] = DEVICE_ID;
        fullPathLength = 1;
    }
    
    // Pack motion alert data with path
    // Format: [nodeId(2), timestamp(4), hasImage(1), pathLength(1), path[...](up to (MAX_PATH_LENGTH+1)*2 bytes)]
    uint8_t data[8 + 1 + ((MAX_PATH_LENGTH + 1) * 2)];  // Base 8 bytes + pathLength + max path
    size_t offset = 0;
    
    // Node ID (2 bytes)
    data[offset++] = nodeId & 0xFF;
    data[offset++] = (nodeId >> 8) & 0xFF;
    
    // Timestamp (4 bytes)
    data[offset++] = timestamp & 0xFF;
    data[offset++] = (timestamp >> 8) & 0xFF;
    data[offset++] = (timestamp >> 16) & 0xFF;
    data[offset++] = (timestamp >> 24) & 0xFF;
    
    // Has image (1 byte)
    data[offset++] = hasImage ? 1 : 0;
    
    // Path length (1 byte)
    data[offset++] = fullPathLength;
    
    // Path array (2 bytes per node ID)
    for (uint8_t i = 0; i < fullPathLength; i++) {
        data[offset++] = fullPath[i] & 0xFF;
        data[offset++] = (fullPath[i] >> 8) & 0xFF;
    }
    
    _motionChar->setValue(data, offset);
    _motionChar->notify();
    
    DEBUG_PRINTF("[BLE] Motion alert sent: node=%d, hasImage=%d, pathLength=%d\n", 
        nodeId, hasImage, fullPathLength);
    if (fullPathLength > 0) {
        DEBUG_PRINT("[BLE] Path: ");
        for (uint8_t i = 0; i < fullPathLength; i++) {
            DEBUG_PRINTF("%d", fullPath[i]);
            if (i < fullPathLength - 1) {
                DEBUG_PRINT(" -> ");
            }
        }
        DEBUG_PRINTLN("");
    }
    
    return true;
}

bool BleGateway::notifyStatus(uint16_t nodeId, uint8_t battery, int8_t rssi, uint8_t meshNodes) {
    if (!isConnected()) {
        return false;
    }
    
    uint8_t data[6];
    data[0] = nodeId & 0xFF;
    data[1] = (nodeId >> 8) & 0xFF;
    data[2] = battery;
    data[3] = (uint8_t)rssi;
    data[4] = meshNodes;
    data[5] = 0;  // Reserved
    
    _statusChar->setValue(data, sizeof(data));
    _statusChar->notify();
    
    return true;
}

bool BleGateway::sendImageToPhone(const uint8_t* imageData, size_t length, uint16_t nodeId, uint16_t imageId) {
    if (!isConnected()) {
        DEBUG_PRINTLN("[BLE] Cannot send image - not connected");
        return false;
    }
    
    DEBUG_PRINTF("[BLE] Sending image to phone: %u bytes\n", length);
    
    // BLE MTU is typically 512, but we use smaller chunks for reliability
    const size_t CHUNK_SIZE = 240;  // Leave room for header
    uint16_t totalChunks = (length + CHUNK_SIZE - 1) / CHUNK_SIZE;
    
    // Send image header first
    uint8_t header[12];
    header[0] = 0x01;  // Image start marker
    header[1] = nodeId & 0xFF;
    header[2] = (nodeId >> 8) & 0xFF;
    header[3] = imageId & 0xFF;
    header[4] = (imageId >> 8) & 0xFF;
    header[5] = length & 0xFF;
    header[6] = (length >> 8) & 0xFF;
    header[7] = (length >> 16) & 0xFF;
    header[8] = (length >> 24) & 0xFF;
    header[9] = totalChunks & 0xFF;
    header[10] = (totalChunks >> 8) & 0xFF;
    header[11] = 0;  // Reserved
    
    _imageChar->setValue(header, sizeof(header));
    _imageChar->notify();
    delay(20);  // Small delay for phone to process
    
    // Send chunks
    for (uint16_t i = 0; i < totalChunks; i++) {
        size_t offset = i * CHUNK_SIZE;
        size_t chunkLen = min(CHUNK_SIZE, length - offset);
        
        sendImageChunkToBle(imageData + offset, chunkLen, i, totalChunks);
        delay(10);  // Delay between chunks
    }
    
    // Send end marker
    uint8_t footer[4];
    footer[0] = 0x02;  // Image end marker
    footer[1] = imageId & 0xFF;
    footer[2] = (imageId >> 8) & 0xFF;
    footer[3] = 0;
    
    _imageChar->setValue(footer, sizeof(footer));
    _imageChar->notify();
    
    DEBUG_PRINTLN("[BLE] Image sent to phone");
    
    return true;
}

void BleGateway::sendImageChunkToBle(const uint8_t* data, size_t length, uint16_t chunkIndex, uint16_t totalChunks) {
    // Format: [0x00][chunkIndex 2b][totalChunks 2b][data...]
    uint8_t packet[256];
    packet[0] = 0x00;  // Chunk marker
    packet[1] = chunkIndex & 0xFF;
    packet[2] = (chunkIndex >> 8) & 0xFF;
    packet[3] = totalChunks & 0xFF;
    packet[4] = (totalChunks >> 8) & 0xFF;
    
    memcpy(packet + 5, data, length);
    
    _imageChar->setValue(packet, 5 + length);
    _imageChar->notify();
}

void BleGateway::handleImageStart(uint16_t sourceNode, uint16_t imageId, uint32_t size, uint16_t chunks) {
    DEBUG_PRINTF("[BLE] Image start from node %d: id=%d, size=%u, chunks=%d\n",
        sourceNode, imageId, size, chunks);
    
    // Free any existing buffer
    if (_imageReception.buffer) {
        free(_imageReception.buffer);
    }
    
    // Allocate buffer for image
    _imageReception.buffer = (uint8_t*)ps_malloc(size);
    if (!_imageReception.buffer) {
        DEBUG_PRINTLN("[BLE] Failed to allocate image buffer");
        return;
    }
    
    _imageReception.imageId = imageId;
    _imageReception.sourceNode = sourceNode;
    _imageReception.totalSize = size;
    _imageReception.totalChunks = chunks;
    _imageReception.receivedChunks = 0;
    _imageReception.startTime = millis();
    _imageReception.complete = false;
    _imageReception.active = true;
    
    memset(_imageReception.buffer, 0, size);
}

void BleGateway::handleImageChunk(uint16_t sourceNode, uint16_t imageId, uint16_t chunkIndex, const uint8_t* data, uint8_t size) {
    if (!_imageReception.active || _imageReception.imageId != imageId) {
        DEBUG_PRINTLN("[BLE] Unexpected image chunk");
        return;
    }
    
    // Calculate offset and copy data
    size_t offset = chunkIndex * IMG_CHUNK_SIZE;
    if (offset + size <= _imageReception.totalSize) {
        memcpy(_imageReception.buffer + offset, data, size);
        _imageReception.receivedChunks++;
        
        DEBUG_PRINTF("[BLE] Image chunk %d/%d received\n", 
            chunkIndex + 1, _imageReception.totalChunks);
    }
}

void BleGateway::handleImageEnd(uint16_t sourceNode, uint16_t imageId) {
    if (!_imageReception.active || _imageReception.imageId != imageId) {
        return;
    }
    
    DEBUG_PRINTF("[BLE] Image transfer complete: %d/%d chunks received\n",
        _imageReception.receivedChunks, _imageReception.totalChunks);
    
    // Check if all chunks received
    if (_imageReception.receivedChunks >= _imageReception.totalChunks) {
        _imageReception.complete = true;
        
        // Forward to phone if connected
        if (isConnected()) {
            sendImageToPhone(
                _imageReception.buffer,
                _imageReception.totalSize,
                _imageReception.sourceNode,
                _imageReception.imageId
            );
        }
    }
    
    // Cleanup
    if (_imageReception.buffer) {
        free(_imageReception.buffer);
        _imageReception.buffer = nullptr;
    }
    _imageReception.active = false;
}

void BleGateway::setConnectCallback(BleConnectCallback callback) {
    _connectCallback = callback;
}

void BleGateway::setCommandCallback(BleCommandCallback callback) {
    _commandCallback = callback;
}


