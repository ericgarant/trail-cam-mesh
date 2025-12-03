#ifndef MESH_NETWORK_H
#define MESH_NETWORK_H

#include <Arduino.h>
#include <esp_now.h>
#include <esp_wifi.h>
#include <WiFi.h>
#include <vector>
#include <queue>
#include "config.h"
#include "message_protocol.h"

// Node information in routing table
struct MeshNode {
    uint16_t nodeId;
    uint8_t macAddress[6];
    int8_t rssi;
    uint8_t hopCount;
    uint32_t lastSeen;
    bool isGateway;
    bool isReachable;
};

// Pending message for retry/relay
struct PendingMessage {
    MeshMessage message;
    uint8_t retriesLeft;
    uint32_t nextRetryTime;
    bool waitingAck;
};

// Callback types
typedef void (*MessageCallback)(const MeshMessage& msg);
typedef void (*NodeCallback)(const MeshNode& node);

class MeshNetwork {
public:
    MeshNetwork();
    
    // Initialize ESP-NOW mesh
    bool begin();
    
    // Main update loop
    void update();
    
    // Send message to specific node or gateway
    bool sendMessage(const MeshMessage& msg);
    
    // Send message to all nodes (broadcast)
    bool broadcast(const MeshMessage& msg);
    
    // Send image in chunks
    bool sendImage(const uint8_t* imageData, size_t imageLength, uint16_t imageId);
    
    // Send motion alert
    bool sendMotionAlert(uint32_t timestamp, uint16_t imageId, bool hasImage);
    
    // Send heartbeat
    void sendHeartbeat();
    
    // Get routing table
    std::vector<MeshNode>& getNodes();
    
    // Find best route to gateway
    MeshNode* findGatewayRoute();
    
    // Set callbacks
    void setMessageCallback(MessageCallback callback);
    void setNodeDiscoveredCallback(NodeCallback callback);
    
    // Get local device info
    uint16_t getDeviceId();
    void getMacAddress(uint8_t* mac);
    
    // Statistics
    uint32_t getMessagesSent();
    uint32_t getMessagesReceived();
    uint32_t getMessagesRelayed();

private:
    // ESP-NOW callbacks (static for C callback)
    static void onDataSent(const uint8_t* mac, esp_now_send_status_t status);
    static void onDataReceived(const uint8_t* mac, const uint8_t* data, int len);
    
    // Internal message handling
    void handleReceivedMessage(const uint8_t* mac, const uint8_t* data, int len);
    void processMessage(const MeshMessage& msg, const uint8_t* senderMac);
    void relayMessage(const MeshMessage& msg);
    
    // Routing
    void updateRoutingTable(uint16_t nodeId, const uint8_t* mac, int8_t rssi, uint8_t hopCount, bool isGateway);
    void pruneRoutingTable();
    MeshNode* findNode(uint16_t nodeId);
    MeshNode* findNodeByMac(const uint8_t* mac);
    
    // Peer management
    bool addPeer(const uint8_t* mac);
    bool removePeer(const uint8_t* mac);
    
    // Message queue
    void processMessageQueue();
    void queueMessage(const MeshMessage& msg, uint8_t retries);
    
    // Static instance for callbacks
    static MeshNetwork* _instance;
    
    // Node list and message queue
    std::vector<MeshNode> _nodes;
    std::queue<PendingMessage> _messageQueue;
    
    // Callbacks
    MessageCallback _messageCallback;
    NodeCallback _nodeCallback;
    
    // Timing
    unsigned long _lastHeartbeat;
    unsigned long _lastPrune;
    
    // Statistics
    uint32_t _messagesSent;
    uint32_t _messagesReceived;
    uint32_t _messagesRelayed;
    
    // Send status
    volatile bool _sendInProgress;
    volatile bool _lastSendSuccess;
    
    // Local device info
    uint8_t _macAddress[6];
    
    // Image transfer state
    bool _imageTransferInProgress;
    uint16_t _currentImageId;
    uint16_t _currentChunk;
    uint16_t _totalChunks;
};

// Global instance
extern MeshNetwork meshNetwork;

#endif // MESH_NETWORK_H
