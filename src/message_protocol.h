#ifndef MESSAGE_PROTOCOL_H
#define MESSAGE_PROTOCOL_H

#include <Arduino.h>
#include "config.h"

// Message types
enum class MessageType : uint8_t {
    HEARTBEAT       = 0x01,  // Node alive announcement
    MOTION_ALERT    = 0x02,  // Motion detected notification
    IMAGE_START     = 0x10,  // Image transfer start
    IMAGE_CHUNK     = 0x11,  // Image data chunk
    IMAGE_END       = 0x12,  // Image transfer complete
    ACK             = 0x20,  // Acknowledgment
    NACK            = 0x21,  // Negative acknowledgment
    DISCOVER        = 0x30,  // Node discovery request
    DISCOVER_RESP   = 0x31,  // Node discovery response
    STATUS_REQUEST  = 0x40,  // Request node status
    STATUS_RESPONSE = 0x41,  // Node status response
    COMMAND         = 0x50,  // Command from gateway/phone
};

// Broadcast address for mesh
#define BROADCAST_ID 0xFFFF
#define GATEWAY_ID 0x0000

// Path tracking configuration
#define MAX_PATH_LENGTH 8  // Maximum number of nodes in routing path

// Message header structure (10 bytes)
#pragma pack(push, 1)
struct MessageHeader {
    uint16_t sourceId;      // Source device ID
    uint16_t destId;        // Destination device ID (0xFFFF = gateway)
    uint8_t  messageType;   // MessageType enum
    uint16_t sequenceNum;   // Message sequence number
    uint16_t chunkIndex;    // Chunk index for multi-part messages
    uint8_t  checksum;      // Simple checksum
};

// Complete message structure
struct MeshMessage {
    MessageHeader header;
    uint8_t payload[MSG_MAX_PAYLOAD_SIZE];
    uint8_t payloadLength;
};

// Motion alert payload
struct MotionAlertPayload {
    uint32_t timestamp;     // Time of detection
    uint8_t  sensorId;      // Which sensor triggered
    uint16_t imageId;       // Associated image ID (if any)
    uint8_t  hasImage;      // Whether image is being sent
    uint8_t  pathLength;    // Number of nodes in routing path (0 = no path tracking)
    uint16_t path[MAX_PATH_LENGTH];  // Routing path: [sourceNode, relay1, relay2, ..., gateway]
};

// Image start payload
struct ImageStartPayload {
    uint16_t imageId;       // Unique image identifier
    uint32_t totalSize;     // Total image size in bytes
    uint16_t totalChunks;   // Number of chunks
    uint32_t timestamp;     // Capture timestamp
};

// Image chunk payload
struct ImageChunkPayload {
    uint16_t imageId;       // Image identifier
    uint16_t chunkIndex;    // Current chunk index
    uint8_t  chunkSize;     // Size of this chunk
    uint8_t  data[IMG_CHUNK_SIZE];  // Chunk data
};

// Heartbeat payload
struct HeartbeatPayload {
    uint8_t  nodeId;        // Node identifier
    uint8_t  role;          // ROLE_SENSOR or ROLE_GATEWAY
    int8_t   rssi;          // Signal strength
    uint8_t  batteryLevel;  // Battery percentage (0-100)
    uint8_t  hopCount;      // Hops to gateway
    uint32_t uptime;        // Seconds since boot
};

// Status response payload
struct StatusPayload {
    uint8_t  nodeId;
    uint8_t  role;
    int8_t   rssi;
    uint8_t  batteryLevel;
    uint32_t uptime;
    uint32_t motionCount;   // Total motion events
    uint32_t imagesSent;    // Total images sent
    uint8_t  meshNodes;     // Known nodes in mesh
};
#pragma pack(pop)

// Message protocol helper class
class MessageProtocol {
public:
    MessageProtocol();
    
    // Create message with header
    static MeshMessage createMessage(
        uint16_t sourceId,
        uint16_t destId,
        MessageType type,
        uint16_t sequence = 0,
        uint16_t chunkIndex = 0
    );
    
    // Add payload to message
    static bool setPayload(MeshMessage& msg, const void* data, uint8_t length);
    
    // Calculate checksum
    static uint8_t calculateChecksum(const MeshMessage& msg);
    
    // Verify message checksum
    static bool verifyChecksum(const MeshMessage& msg);
    
    // Serialize message to byte array
    static size_t serialize(const MeshMessage& msg, uint8_t* buffer, size_t bufferSize);
    
    // Deserialize byte array to message
    static bool deserialize(const uint8_t* buffer, size_t length, MeshMessage& msg);
    
    // Create specific message types
    static MeshMessage createMotionAlert(uint16_t sourceId, uint32_t timestamp, uint16_t imageId, bool hasImage);
    static MeshMessage createHeartbeat(uint16_t sourceId, int8_t rssi, uint8_t battery, uint8_t hopCount);
    static MeshMessage createImageStart(uint16_t sourceId, uint16_t imageId, uint32_t size, uint16_t chunks);
    static MeshMessage createImageChunk(uint16_t sourceId, uint16_t imageId, uint16_t chunkIndex, const uint8_t* data, uint8_t size);
    static MeshMessage createAck(uint16_t sourceId, uint16_t destId, uint16_t sequence);
    
    // Path tracking helpers for motion alerts
    static bool appendToPath(MeshMessage& msg, uint16_t nodeId);
    static bool getPath(const MeshMessage& msg, uint16_t* path, uint8_t* pathLength);
    
    // Get next sequence number
    static uint16_t getNextSequence();

private:
    static uint16_t _sequenceCounter;
};

#endif // MESSAGE_PROTOCOL_H


