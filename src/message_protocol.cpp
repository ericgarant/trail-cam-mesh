#include "message_protocol.h"

// Static member initialization
uint16_t MessageProtocol::_sequenceCounter = 0;

MessageProtocol::MessageProtocol() {
}

MeshMessage MessageProtocol::createMessage(
    uint16_t sourceId,
    uint16_t destId,
    MessageType type,
    uint16_t sequence,
    uint16_t chunkIndex
) {
    MeshMessage msg;
    memset(&msg, 0, sizeof(MeshMessage));
    
    msg.header.sourceId = sourceId;
    msg.header.destId = destId;
    msg.header.messageType = static_cast<uint8_t>(type);
    msg.header.sequenceNum = (sequence == 0) ? getNextSequence() : sequence;
    msg.header.chunkIndex = chunkIndex;
    msg.header.checksum = 0;
    msg.payloadLength = 0;
    
    return msg;
}

bool MessageProtocol::setPayload(MeshMessage& msg, const void* data, uint8_t length) {
    if (length > MSG_MAX_PAYLOAD_SIZE) {
        DEBUG_PRINTLN("[MSG] Payload too large");
        return false;
    }
    
    memcpy(msg.payload, data, length);
    msg.payloadLength = length;
    
    // Update checksum
    msg.header.checksum = calculateChecksum(msg);
    
    return true;
}

uint8_t MessageProtocol::calculateChecksum(const MeshMessage& msg) {
    uint8_t checksum = 0;
    
    // XOR all header bytes except checksum itself
    const uint8_t* headerBytes = reinterpret_cast<const uint8_t*>(&msg.header);
    for (size_t i = 0; i < sizeof(MessageHeader) - 1; i++) {
        checksum ^= headerBytes[i];
    }
    
    // XOR all payload bytes
    for (uint8_t i = 0; i < msg.payloadLength; i++) {
        checksum ^= msg.payload[i];
    }
    
    return checksum;
}

bool MessageProtocol::verifyChecksum(const MeshMessage& msg) {
    uint8_t calculated = 0;
    
    // XOR all header bytes except checksum itself
    const uint8_t* headerBytes = reinterpret_cast<const uint8_t*>(&msg.header);
    for (size_t i = 0; i < sizeof(MessageHeader) - 1; i++) {
        calculated ^= headerBytes[i];
    }
    
    // XOR all payload bytes
    for (uint8_t i = 0; i < msg.payloadLength; i++) {
        calculated ^= msg.payload[i];
    }
    
    return calculated == msg.header.checksum;
}

size_t MessageProtocol::serialize(const MeshMessage& msg, uint8_t* buffer, size_t bufferSize) {
    size_t totalSize = sizeof(MessageHeader) + 1 + msg.payloadLength;  // +1 for payloadLength byte
    
    if (bufferSize < totalSize) {
        DEBUG_PRINTLN("[MSG] Buffer too small for serialization");
        return 0;
    }
    
    size_t offset = 0;
    
    // Copy header
    memcpy(buffer + offset, &msg.header, sizeof(MessageHeader));
    offset += sizeof(MessageHeader);
    
    // Copy payload length
    buffer[offset] = msg.payloadLength;
    offset++;
    
    // Copy payload
    if (msg.payloadLength > 0) {
        memcpy(buffer + offset, msg.payload, msg.payloadLength);
        offset += msg.payloadLength;
    }
    
    return offset;
}

bool MessageProtocol::deserialize(const uint8_t* buffer, size_t length, MeshMessage& msg) {
    // Minimum size check: header + payloadLength byte
    if (length < sizeof(MessageHeader) + 1) {
        DEBUG_PRINTLN("[MSG] Buffer too small for deserialization");
        return false;
    }
    
    size_t offset = 0;
    
    // Copy header
    memcpy(&msg.header, buffer + offset, sizeof(MessageHeader));
    offset += sizeof(MessageHeader);
    
    // Get payload length
    msg.payloadLength = buffer[offset];
    offset++;
    
    // Validate payload length
    if (msg.payloadLength > MSG_MAX_PAYLOAD_SIZE) {
        DEBUG_PRINTLN("[MSG] Invalid payload length");
        return false;
    }
    
    // Check if buffer contains full payload
    if (length < offset + msg.payloadLength) {
        DEBUG_PRINTLN("[MSG] Buffer doesn't contain full payload");
        return false;
    }
    
    // Copy payload
    if (msg.payloadLength > 0) {
        memcpy(msg.payload, buffer + offset, msg.payloadLength);
    }
    
    // Verify checksum
    if (!verifyChecksum(msg)) {
        DEBUG_PRINTLN("[MSG] Checksum verification failed");
        return false;
    }
    
    return true;
}

MeshMessage MessageProtocol::createMotionAlert(uint16_t sourceId, uint32_t timestamp, uint16_t imageId, bool hasImage) {
    MeshMessage msg = createMessage(sourceId, GATEWAY_ID, MessageType::MOTION_ALERT);
    
    MotionAlertPayload payload;
    memset(&payload, 0, sizeof(MotionAlertPayload));  // Initialize all fields including path
    payload.timestamp = timestamp;
    payload.sensorId = sourceId & 0xFF;
    payload.imageId = imageId;
    payload.hasImage = hasImage ? 1 : 0;
    // Initialize path with source node as first entry
    payload.pathLength = 1;
    payload.path[0] = sourceId;
    
    setPayload(msg, &payload, sizeof(MotionAlertPayload));
    
    return msg;
}

MeshMessage MessageProtocol::createHeartbeat(uint16_t sourceId, int8_t rssi, uint8_t battery, uint8_t hopCount) {
    MeshMessage msg = createMessage(sourceId, BROADCAST_ID, MessageType::HEARTBEAT);
    
    HeartbeatPayload payload;
    payload.nodeId = sourceId & 0xFF;
    payload.role = DEVICE_ROLE;
    payload.rssi = rssi;
    payload.batteryLevel = battery;
    payload.hopCount = hopCount;
    payload.uptime = millis() / 1000;
    
    setPayload(msg, &payload, sizeof(HeartbeatPayload));
    
    return msg;
}

MeshMessage MessageProtocol::createImageStart(uint16_t sourceId, uint16_t imageId, uint32_t size, uint16_t chunks) {
    MeshMessage msg = createMessage(sourceId, GATEWAY_ID, MessageType::IMAGE_START);
    
    ImageStartPayload payload;
    payload.imageId = imageId;
    payload.totalSize = size;
    payload.totalChunks = chunks;
    payload.timestamp = millis();
    
    setPayload(msg, &payload, sizeof(ImageStartPayload));
    
    return msg;
}

MeshMessage MessageProtocol::createImageChunk(uint16_t sourceId, uint16_t imageId, uint16_t chunkIndex, const uint8_t* data, uint8_t size) {
    MeshMessage msg = createMessage(sourceId, GATEWAY_ID, MessageType::IMAGE_CHUNK, 0, chunkIndex);
    
    // Create chunk payload with header info followed by data
    uint8_t chunkPayload[4 + IMG_CHUNK_SIZE];  // imageId(2) + chunkIndex(2) + data
    chunkPayload[0] = imageId & 0xFF;
    chunkPayload[1] = (imageId >> 8) & 0xFF;
    chunkPayload[2] = chunkIndex & 0xFF;
    chunkPayload[3] = (chunkIndex >> 8) & 0xFF;
    
    if (size > IMG_CHUNK_SIZE) {
        size = IMG_CHUNK_SIZE;
    }
    memcpy(chunkPayload + 4, data, size);
    
    setPayload(msg, chunkPayload, 4 + size);
    
    return msg;
}

MeshMessage MessageProtocol::createAck(uint16_t sourceId, uint16_t destId, uint16_t sequence) {
    MeshMessage msg = createMessage(sourceId, destId, MessageType::ACK, sequence);
    return msg;
}

uint16_t MessageProtocol::getNextSequence() {
    return ++_sequenceCounter;
}

bool MessageProtocol::appendToPath(MeshMessage& msg, uint16_t nodeId) {
    // Only works for MOTION_ALERT messages
    if (static_cast<MessageType>(msg.header.messageType) != MessageType::MOTION_ALERT) {
        return false;
    }
    
    // Check if payload is the right size
    if (msg.payloadLength < sizeof(MotionAlertPayload)) {
        // Old format without path - need to handle backward compatibility
        // For now, we'll only append if path tracking is already present
        return false;
    }
    
    MotionAlertPayload* payload = reinterpret_cast<MotionAlertPayload*>(msg.payload);
    
    // Check if path is full
    if (payload->pathLength >= MAX_PATH_LENGTH) {
        DEBUG_PRINTLN("[MSG] Path is full, cannot append");
        return false;
    }
    
    // Append node ID to path
    payload->path[payload->pathLength] = nodeId;
    payload->pathLength++;
    
    // Recalculate checksum since payload changed
    msg.header.checksum = calculateChecksum(msg);
    
    return true;
}

bool MessageProtocol::getPath(const MeshMessage& msg, uint16_t* path, uint8_t* pathLength) {
    // Only works for MOTION_ALERT messages
    if (static_cast<MessageType>(msg.header.messageType) != MessageType::MOTION_ALERT) {
        return false;
    }
    
    // Check if payload is the right size
    if (msg.payloadLength < sizeof(MotionAlertPayload)) {
        // Old format without path
        *pathLength = 0;
        return true;  // Return true but with empty path for backward compatibility
    }
    
    const MotionAlertPayload* payload = reinterpret_cast<const MotionAlertPayload*>(msg.payload);
    
    *pathLength = payload->pathLength;
    if (path && *pathLength > 0) {
        memcpy(path, payload->path, *pathLength * sizeof(uint16_t));
    }
    
    return true;
}


