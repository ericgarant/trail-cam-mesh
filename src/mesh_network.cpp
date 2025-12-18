#include "mesh_network.h"

// Static instance pointer for callbacks
MeshNetwork* MeshNetwork::_instance = nullptr;

// Global instance
MeshNetwork meshNetwork;

// Broadcast MAC address for ESP-NOW
static const uint8_t BROADCAST_MAC[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};

MeshNetwork::MeshNetwork()
    : _messageCallback(nullptr)
    , _nodeCallback(nullptr)
    , _lastHeartbeat(0)
    , _lastPrune(0)
    , _messagesSent(0)
    , _messagesReceived(0)
    , _messagesRelayed(0)
    , _sendInProgress(false)
    , _lastSendSuccess(false)
    , _imageTransferInProgress(false)
    , _currentImageId(0)
    , _currentChunk(0)
    , _totalChunks(0) {
    
    _instance = this;
    memset(_macAddress, 0, 6);
}

bool MeshNetwork::begin() {
    DEBUG_PRINTLN("[MESH] Initializing ESP-NOW mesh network...");
    
    // Set WiFi mode to station for ESP-NOW
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    
    // Get MAC address
    WiFi.macAddress(_macAddress);
    DEBUG_PRINTF("[MESH] MAC Address: %02X:%02X:%02X:%02X:%02X:%02X\n",
        _macAddress[0], _macAddress[1], _macAddress[2],
        _macAddress[3], _macAddress[4], _macAddress[5]);
    
    // Set WiFi channel
    esp_wifi_set_channel(MESH_CHANNEL, WIFI_SECOND_CHAN_NONE);
    
    // Initialize ESP-NOW
    if (esp_now_init() != ESP_OK) {
        DEBUG_PRINTLN("[MESH] ESP-NOW init failed");
        return false;
    }
    
    // Register callbacks
    esp_now_register_send_cb(onDataSent);
    esp_now_register_recv_cb(onDataReceived);
    
    // Add broadcast peer for discovery
    addPeer(BROADCAST_MAC);
    
    DEBUG_PRINTLN("[MESH] ESP-NOW initialized successfully");
    DEBUG_PRINTF("[MESH] Device ID: %d, Role: %s\n", 
        DEVICE_ID, 
        DEVICE_ROLE == ROLE_GATEWAY ? "GATEWAY" : "SENSOR");
    
    // Send initial heartbeat
    sendHeartbeat();
    
    return true;
}

void MeshNetwork::update() {
    unsigned long currentTime = millis();
    
    // Send periodic heartbeat
    if (currentTime - _lastHeartbeat >= MESH_HEARTBEAT_INTERVAL_MS) {
        sendHeartbeat();
        _lastHeartbeat = currentTime;
    }
    
    // Prune stale routes
    if (currentTime - _lastPrune >= MESH_ROUTE_TIMEOUT_MS / 2) {
        pruneRoutingTable();
        _lastPrune = currentTime;
    }
    
    // Process pending messages
    processMessageQueue();
}

void MeshNetwork::onDataSent(const uint8_t* mac, esp_now_send_status_t status) {
    if (_instance) {
        _instance->_sendInProgress = false;
        _instance->_lastSendSuccess = (status == ESP_NOW_SEND_SUCCESS);
        
        if (status == ESP_NOW_SEND_SUCCESS) {
            _instance->_messagesSent++;
            DEBUG_PRINTLN("[MESH] Send success");
        } else {
            DEBUG_PRINTLN("[MESH] Send failed");
        }
    }
}

void MeshNetwork::onDataReceived(const uint8_t* mac, const uint8_t* data, int len) {
    if (_instance) {
        _instance->handleReceivedMessage(mac, data, len);
    }
}

void MeshNetwork::handleReceivedMessage(const uint8_t* mac, const uint8_t* data, int len) {
    _messagesReceived++;
    
    DEBUG_PRINTF("[MESH] Received %d bytes from %02X:%02X:%02X:%02X:%02X:%02X\n",
        len, mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    
    // Deserialize message
    MeshMessage msg;
    if (!MessageProtocol::deserialize(data, len, msg)) {
        DEBUG_PRINTLN("[MESH] Failed to deserialize message");
        return;
    }
    
    // Update routing table with sender info
    // Note: RSSI would need to be obtained differently in newer ESP-IDF
    updateRoutingTable(msg.header.sourceId, mac, -50, 1, false);
    
    // Process the message
    processMessage(msg, mac);
}

void MeshNetwork::processMessage(const MeshMessage& msg, const uint8_t* senderMac) {
    MessageType type = static_cast<MessageType>(msg.header.messageType);
    
    DEBUG_PRINTF("[MESH] Processing message type %d from node %d to %d\n",
        msg.header.messageType, msg.header.sourceId, msg.header.destId);
    
    // Check if message is for us
    bool isForUs = (msg.header.destId == DEVICE_ID) || 
                   (msg.header.destId == BROADCAST_ID) ||
                   (msg.header.destId == GATEWAY_ID && DEVICE_ROLE == ROLE_GATEWAY);
    
    // Handle heartbeat for routing table update
    if (type == MessageType::HEARTBEAT) {
        HeartbeatPayload* payload = (HeartbeatPayload*)msg.payload;
        updateRoutingTable(
            msg.header.sourceId,
            senderMac,
            payload->rssi,
            payload->hopCount,
            payload->role == ROLE_GATEWAY
        );
        
        // Notify callback
        if (_nodeCallback) {
            MeshNode* node = findNode(msg.header.sourceId);
            if (node) {
                _nodeCallback(*node);
            }
        }
        return;
    }
    
    // Handle discovery
    if (type == MessageType::DISCOVER) {
        // Respond with our info
        MeshMessage response = MessageProtocol::createHeartbeat(
            DEVICE_ID, -50, 100, 0
        );
        response.header.messageType = static_cast<uint8_t>(MessageType::DISCOVER_RESP);
        response.header.destId = msg.header.sourceId;
        sendMessage(response);
        return;
    }
    
    // If message is for us, process it
    if (isForUs) {
        // Handle ACK
        if (type == MessageType::ACK) {
            // Remove from pending queue (simplified - would need sequence matching)
            DEBUG_PRINTF("[MESH] Received ACK for seq %d\n", msg.header.sequenceNum);
            return;
        }
        
        // Call user callback for other message types
        if (_messageCallback) {
            _messageCallback(msg);
        }
        
        // Send ACK for certain message types
        if (type == MessageType::MOTION_ALERT || 
            type == MessageType::IMAGE_START ||
            type == MessageType::IMAGE_END) {
            MeshMessage ack = MessageProtocol::createAck(
                DEVICE_ID, msg.header.sourceId, msg.header.sequenceNum
            );
            sendMessage(ack);
        }
    }
    
    // Relay if not for us and we're not the source
    if (!isForUs && msg.header.sourceId != DEVICE_ID) {
        // Check if we should relay (message needs to reach gateway)
        if (msg.header.destId == GATEWAY_ID || msg.header.destId == BROADCAST_ID) {
            relayMessage(msg);
        }
    }
}

void MeshNetwork::relayMessage(const MeshMessage& msg) {
    DEBUG_PRINTF("[MESH] Relaying message from %d to %d\n",
        msg.header.sourceId, msg.header.destId);
    
    _messagesRelayed++;
    
    // Make a copy of the message so we can modify the path
    MeshMessage relayMsg = msg;
    
    // Append current node ID to path for MOTION_ALERT messages
    if (static_cast<MessageType>(relayMsg.header.messageType) == MessageType::MOTION_ALERT) {
        if (MessageProtocol::appendToPath(relayMsg, DEVICE_ID)) {
            DEBUG_PRINTF("[MESH] Added node %d to routing path\n", DEVICE_ID);
        }
    }
    
    // Find next hop
    MeshNode* nextHop = nullptr;
    
    if (relayMsg.header.destId == GATEWAY_ID) {
        nextHop = findGatewayRoute();
    } else if (relayMsg.header.destId != BROADCAST_ID) {
        nextHop = findNode(relayMsg.header.destId);
    }
    
    if (nextHop) {
        // Send to specific node
        addPeer(nextHop->macAddress);
        
        uint8_t buffer[250];
        size_t len = MessageProtocol::serialize(relayMsg, buffer, sizeof(buffer));
        
        esp_now_send(nextHop->macAddress, buffer, len);
    } else if (relayMsg.header.destId == BROADCAST_ID || relayMsg.header.destId == GATEWAY_ID) {
        // Broadcast if no specific route
        broadcast(relayMsg);
    }
}

bool MeshNetwork::sendMessage(const MeshMessage& msg) {
    // Find destination
    MeshNode* dest = nullptr;
    
    if (msg.header.destId == GATEWAY_ID) {
        dest = findGatewayRoute();
    } else if (msg.header.destId != BROADCAST_ID) {
        dest = findNode(msg.header.destId);
    }
    
    uint8_t buffer[250];
    size_t len = MessageProtocol::serialize(msg, buffer, sizeof(buffer));
    
    if (len == 0) {
        DEBUG_PRINTLN("[MESH] Serialization failed");
        return false;
    }
    
    const uint8_t* targetMac = dest ? dest->macAddress : BROADCAST_MAC;
    
    // Ensure peer is added
    addPeer(targetMac);
    
    // Send
    _sendInProgress = true;
    esp_err_t result = esp_now_send(targetMac, buffer, len);
    
    if (result != ESP_OK) {
        DEBUG_PRINTF("[MESH] esp_now_send error: %d\n", result);
        _sendInProgress = false;
        return false;
    }
    
    // Wait for callback (with timeout)
    unsigned long start = millis();
    while (_sendInProgress && (millis() - start < 100)) {
        delay(1);
    }
    
    return _lastSendSuccess;
}

bool MeshNetwork::broadcast(const MeshMessage& msg) {
    uint8_t buffer[250];
    size_t len = MessageProtocol::serialize(msg, buffer, sizeof(buffer));
    
    if (len == 0) {
        return false;
    }
    
    _sendInProgress = true;
    esp_err_t result = esp_now_send(BROADCAST_MAC, buffer, len);
    
    if (result != ESP_OK) {
        _sendInProgress = false;
        return false;
    }
    
    // Wait for callback
    unsigned long start = millis();
    while (_sendInProgress && (millis() - start < 100)) {
        delay(1);
    }
    
    return _lastSendSuccess;
}

bool MeshNetwork::sendImage(const uint8_t* imageData, size_t imageLength, uint16_t imageId) {
    if (_imageTransferInProgress) {
        DEBUG_PRINTLN("[MESH] Image transfer already in progress");
        return false;
    }
    
    // Calculate chunks
    uint16_t totalChunks = (imageLength + IMG_CHUNK_SIZE - 1) / IMG_CHUNK_SIZE;
    
    if (totalChunks > IMG_MAX_CHUNKS) {
        DEBUG_PRINTLN("[MESH] Image too large");
        return false;
    }
    
    DEBUG_PRINTF("[MESH] Starting image transfer: %u bytes, %u chunks\n", 
        imageLength, totalChunks);
    
    _imageTransferInProgress = true;
    _currentImageId = imageId;
    _currentChunk = 0;
    _totalChunks = totalChunks;
    
    // Send IMAGE_START
    MeshMessage startMsg = MessageProtocol::createImageStart(
        DEVICE_ID, imageId, imageLength, totalChunks
    );
    if (!sendMessage(startMsg)) {
        _imageTransferInProgress = false;
        return false;
    }
    
    // Send chunks
    for (uint16_t i = 0; i < totalChunks; i++) {
        size_t offset = i * IMG_CHUNK_SIZE;
        size_t chunkSize = min((size_t)IMG_CHUNK_SIZE, imageLength - offset);
        
        MeshMessage chunkMsg = MessageProtocol::createImageChunk(
            DEVICE_ID, imageId, i, imageData + offset, chunkSize
        );
        
        // Send with retry
        bool sent = false;
        for (int retry = 0; retry < MSG_MAX_RETRIES && !sent; retry++) {
            sent = sendMessage(chunkMsg);
            if (!sent) {
                delay(MSG_RETRY_DELAY_MS);
            }
        }
        
        if (!sent) {
            DEBUG_PRINTF("[MESH] Failed to send chunk %d\n", i);
            _imageTransferInProgress = false;
            return false;
        }
        
        _currentChunk = i + 1;
        
        // Small delay between chunks to avoid overwhelming receiver
        delay(10);
    }
    
    // Send IMAGE_END
    MeshMessage endMsg = MessageProtocol::createMessage(
        DEVICE_ID, GATEWAY_ID, MessageType::IMAGE_END
    );
    uint8_t endPayload[4];
    endPayload[0] = imageId & 0xFF;
    endPayload[1] = (imageId >> 8) & 0xFF;
    endPayload[2] = totalChunks & 0xFF;
    endPayload[3] = (totalChunks >> 8) & 0xFF;
    MessageProtocol::setPayload(endMsg, endPayload, 4);
    sendMessage(endMsg);
    
    _imageTransferInProgress = false;
    DEBUG_PRINTLN("[MESH] Image transfer complete");
    
    return true;
}

bool MeshNetwork::sendMotionAlert(uint32_t timestamp, uint16_t imageId, bool hasImage) {
    MeshMessage msg = MessageProtocol::createMotionAlert(
        DEVICE_ID, timestamp, imageId, hasImage
    );
    return sendMessage(msg);
}

void MeshNetwork::sendHeartbeat() {
    DEBUG_PRINTLN("[MESH] Sending heartbeat");
    
    // Calculate hop count to gateway
    uint8_t hopCount = 0;
    MeshNode* gateway = findGatewayRoute();
    if (gateway) {
        hopCount = gateway->hopCount + 1;
    }
    
    MeshMessage msg = MessageProtocol::createHeartbeat(
        DEVICE_ID,
        -50,  // RSSI placeholder
        100,  // Battery placeholder (would need ADC reading)
        hopCount
    );
    
    broadcast(msg);
}

void MeshNetwork::updateRoutingTable(uint16_t nodeId, const uint8_t* mac, 
    int8_t rssi, uint8_t hopCount, bool isGateway) {
    
    // Don't add ourselves
    if (nodeId == DEVICE_ID) {
        return;
    }
    
    // Find existing node
    MeshNode* existing = findNode(nodeId);
    
    if (existing) {
        // Update existing entry
        memcpy(existing->macAddress, mac, 6);
        existing->rssi = rssi;
        existing->hopCount = hopCount;
        existing->lastSeen = millis();
        existing->isGateway = isGateway;
        existing->isReachable = true;
    } else {
        // Add new node
        if (_nodes.size() < MESH_MAX_NODES) {
            MeshNode node;
            node.nodeId = nodeId;
            memcpy(node.macAddress, mac, 6);
            node.rssi = rssi;
            node.hopCount = hopCount;
            node.lastSeen = millis();
            node.isGateway = isGateway;
            node.isReachable = true;
            
            _nodes.push_back(node);
            
            // Add as ESP-NOW peer
            addPeer(mac);
            
            DEBUG_PRINTF("[MESH] New node discovered: %d (Gateway: %s)\n", 
                nodeId, isGateway ? "YES" : "NO");
            
            // Notify callback
            if (_nodeCallback) {
                _nodeCallback(node);
            }
        }
    }
}

void MeshNetwork::pruneRoutingTable() {
    unsigned long currentTime = millis();
    
    for (auto it = _nodes.begin(); it != _nodes.end(); ) {
        if (currentTime - it->lastSeen > MESH_ROUTE_TIMEOUT_MS) {
            DEBUG_PRINTF("[MESH] Removing stale node: %d\n", it->nodeId);
            removePeer(it->macAddress);
            it = _nodes.erase(it);
        } else {
            ++it;
        }
    }
}

MeshNode* MeshNetwork::findNode(uint16_t nodeId) {
    for (auto& node : _nodes) {
        if (node.nodeId == nodeId) {
            return &node;
        }
    }
    return nullptr;
}

MeshNode* MeshNetwork::findNodeByMac(const uint8_t* mac) {
    for (auto& node : _nodes) {
        if (memcmp(node.macAddress, mac, 6) == 0) {
            return &node;
        }
    }
    return nullptr;
}

MeshNode* MeshNetwork::findGatewayRoute() {
    MeshNode* bestRoute = nullptr;
    int8_t bestRssi = -128;
    
    for (auto& node : _nodes) {
        if (node.isGateway && node.isReachable) {
            if (node.rssi > bestRssi) {
                bestRssi = node.rssi;
                bestRoute = &node;
            }
        }
    }
    
    // If no direct gateway, find node with best path to gateway
    if (!bestRoute) {
        uint8_t minHops = 255;
        for (auto& node : _nodes) {
            if (node.isReachable && node.hopCount < minHops) {
                minHops = node.hopCount;
                bestRoute = &node;
            }
        }
    }
    
    return bestRoute;
}

bool MeshNetwork::addPeer(const uint8_t* mac) {
    // Check if already added
    if (esp_now_is_peer_exist(mac)) {
        return true;
    }
    
    esp_now_peer_info_t peerInfo = {};
    memcpy(peerInfo.peer_addr, mac, 6);
    peerInfo.channel = MESH_CHANNEL;
    peerInfo.encrypt = false;
    
    esp_err_t result = esp_now_add_peer(&peerInfo);
    if (result != ESP_OK) {
        DEBUG_PRINTF("[MESH] Failed to add peer: %d\n", result);
        return false;
    }
    
    return true;
}

bool MeshNetwork::removePeer(const uint8_t* mac) {
    if (!esp_now_is_peer_exist(mac)) {
        return true;
    }
    
    return esp_now_del_peer(mac) == ESP_OK;
}

void MeshNetwork::processMessageQueue() {
    // Process retry queue (simplified implementation)
    // A full implementation would handle ACK timeouts and retries
}

void MeshNetwork::queueMessage(const MeshMessage& msg, uint8_t retries) {
    PendingMessage pending;
    pending.message = msg;
    pending.retriesLeft = retries;
    pending.nextRetryTime = millis() + MSG_RETRY_DELAY_MS;
    pending.waitingAck = true;
    
    _messageQueue.push(pending);
}

std::vector<MeshNode>& MeshNetwork::getNodes() {
    return _nodes;
}

void MeshNetwork::setMessageCallback(MessageCallback callback) {
    _messageCallback = callback;
}

void MeshNetwork::setNodeDiscoveredCallback(NodeCallback callback) {
    _nodeCallback = callback;
}

uint16_t MeshNetwork::getDeviceId() {
    return DEVICE_ID;
}

void MeshNetwork::getMacAddress(uint8_t* mac) {
    memcpy(mac, _macAddress, 6);
}

uint32_t MeshNetwork::getMessagesSent() {
    return _messagesSent;
}

uint32_t MeshNetwork::getMessagesReceived() {
    return _messagesReceived;
}

uint32_t MeshNetwork::getMessagesRelayed() {
    return _messagesRelayed;
}
