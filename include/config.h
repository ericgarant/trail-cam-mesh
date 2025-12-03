#ifndef CONFIG_H
#define CONFIG_H

#include <Arduino.h>

// ============================================================================
// DEVICE CONFIGURATION
// ============================================================================
// Change these settings for each device before flashing

// Device Role: ROLE_SENSOR or ROLE_GATEWAY
// Gateway nodes have BLE enabled for phone communication
#define DEVICE_ROLE ROLE_SENSOR

// Unique device ID (1-254, 0 is reserved, 255 is broadcast)
#define DEVICE_ID 3

// Device roles enumeration
#define ROLE_SENSOR  0
#define ROLE_GATEWAY 1

// ============================================================================
// PIN CONFIGURATION - Freenove ESP32-WROVER CAM
// ============================================================================

// PIR Sensor
#define PIR_PIN 13              // GPIO 13 - motion detection input
#define PIR_DEBOUNCE_MS 500     // Debounce time in milliseconds
#define PIR_COOLDOWN_MS 5000    // Cooldown between triggers (5 seconds)

// Status LED
#define LED_PIN 2               // GPIO 2 - onboard blue LED
#define LED_ACTIVE_LOW false    // Set true if LED is active low

// ============================================================================
// CAMERA CONFIGURATION - Freenove ESP32-WROVER CAM pinout
// ============================================================================

#define PWDN_GPIO_NUM    -1     // Power down not used
#define RESET_GPIO_NUM   -1     // Reset not used
#define XCLK_GPIO_NUM    21
#define SIOD_GPIO_NUM    26     // I2C SDA
#define SIOC_GPIO_NUM    27     // I2C SCL

#define Y9_GPIO_NUM      35
#define Y8_GPIO_NUM      34
#define Y7_GPIO_NUM      39
#define Y6_GPIO_NUM      36
#define Y5_GPIO_NUM      19
#define Y4_GPIO_NUM      18
#define Y3_GPIO_NUM      5
#define Y2_GPIO_NUM      4

#define VSYNC_GPIO_NUM   25
#define HREF_GPIO_NUM    23
#define PCLK_GPIO_NUM    22

// Camera settings
#define CAMERA_FRAME_SIZE FRAMESIZE_QVGA  // 320x240 for fast mesh transfer
#define CAMERA_JPEG_QUALITY 12            // 0-63, lower = higher quality
#define CAMERA_FB_COUNT 2                 // Frame buffer count (PSRAM allows more)

// ============================================================================
// MESH NETWORK CONFIGURATION
// ============================================================================

// ESP-NOW settings
#define MESH_CHANNEL 1                    // WiFi channel (1-13)
#define MESH_MAX_NODES 16                 // Maximum nodes in mesh
#define MESH_HEARTBEAT_INTERVAL_MS 10000  // Heartbeat every 10 seconds
#define MESH_ROUTE_TIMEOUT_MS 30000       // Route expires after 30s no heartbeat

// Message settings
#define MSG_MAX_PAYLOAD_SIZE 200          // Max payload per ESP-NOW packet
#define MSG_HEADER_SIZE 10                // Header size in bytes
#define MSG_MAX_RETRIES 3                 // Retry count for failed sends
#define MSG_RETRY_DELAY_MS 100            // Delay between retries

// Image transfer
#define IMG_CHUNK_SIZE 190                // Bytes per image chunk (leaves room for 4-byte header + padding)
#define IMG_MAX_CHUNKS 150                // Max chunks per image (~28KB max)
#define IMG_TRANSFER_TIMEOUT_MS 30000     // Timeout for complete image transfer

// ============================================================================
// BLE CONFIGURATION (Gateway only)
// ============================================================================

#define BLE_DEVICE_NAME "TrailCam-GW"     // BLE advertised name
#define BLE_MTU_SIZE 512                  // Maximum transmission unit

// BLE UUIDs
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_MOTION_UUID    "beb5483e-36e1-4688-b7f5-ea07361b26a8"  // Motion alerts
#define CHAR_IMAGE_UUID     "cba1d466-344c-4be3-ab3f-189f80dd7518"  // Image data
#define CHAR_STATUS_UUID    "2c957792-46f0-4d8c-9a76-3c0e8fb4a4d5"  // Device status
#define CHAR_COMMAND_UUID   "f27b53ad-c63d-49a0-8c0f-9f297e6cc520"  // Commands from phone

// ============================================================================
// SYSTEM CONFIGURATION
// ============================================================================

#define SERIAL_BAUD 115200                // Serial monitor baud rate
#define WATCHDOG_TIMEOUT_S 30             // Watchdog timer (seconds)

// Debug settings
#define DEBUG_ENABLED true
#if DEBUG_ENABLED
    #define DEBUG_PRINT(x) Serial.print(x)
    #define DEBUG_PRINTLN(x) Serial.println(x)
    #define DEBUG_PRINTF(...) Serial.printf(__VA_ARGS__)
#else
    #define DEBUG_PRINT(x)
    #define DEBUG_PRINTLN(x)
    #define DEBUG_PRINTF(...)
#endif

#endif // CONFIG_H

