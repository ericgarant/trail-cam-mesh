# ESP32 Trail Camera Mesh Network

A deer hunting trail camera system built on ESP32-WROVER CAM modules. Devices detect motion via PIR sensor, capture images, and relay data through an ESP-NOW mesh network to a gateway device connected to an Android phone via Bluetooth LE.

## Features

- **PIR Motion Detection**: Interrupt-driven motion sensing with configurable debounce and cooldown
- **Camera Capture**: OV2640 camera with JPEG compression (320x240 for fast mesh transfer)
- **ESP-NOW Mesh Network**: Multi-hop mesh networking with store-and-forward relay
- **BLE Phone Connectivity**: Gateway device connects to Android app via Bluetooth LE
- **Visual Feedback**: LED patterns indicate device state and events

## Hardware Requirements

- **Freenove ESP32-WROVER CAM** (with OV2640 camera)
- **HC-SR501 PIR Sensor** or similar
- USB-C cable for programming
- Power supply (5V, recommend battery pack for field use)

### Wiring

| Component | GPIO Pin |
|-----------|----------|
| PIR Sensor OUT | GPIO 13 |
| PIR Sensor VCC | 5V |
| PIR Sensor GND | GND |
| Status LED | GPIO 2 (onboard) |

## Project Structure

```
R3Ev2/
├── platformio.ini              # PlatformIO configuration
├── include/
│   └── config.h                # Device configuration
└── src/
    ├── main.cpp                # Main application
    ├── pir_sensor.cpp/.h       # PIR motion detection
    ├── camera.cpp/.h           # Camera capture
    ├── led_indicator.cpp/.h    # LED patterns
    ├── mesh_network.cpp/.h     # ESP-NOW mesh
    ├── ble_gateway.cpp/.h      # BLE for phone
    └── message_protocol.cpp/.h # Message formats
```

## Configuration

Edit `include/config.h` before flashing each device:

```cpp
// Unique ID for each device (1-254)
#define DEVICE_ID 1

// Device role: ROLE_SENSOR or ROLE_GATEWAY
#define DEVICE_ROLE ROLE_SENSOR
```

### Device Roles

- **ROLE_SENSOR**: Standard trail camera node
  - Detects motion, captures images
  - Relays messages through mesh
  
- **ROLE_GATEWAY**: Gateway node (one per mesh)
  - Same as sensor, plus BLE connectivity
  - Connects to Android companion app

## Building & Flashing

### Prerequisites

1. Install [PlatformIO](https://platformio.org/install)
2. Install VS Code + PlatformIO extension (recommended)

### Build

```bash
# Build firmware
pio run

# Build and upload to connected device
pio run --target upload

# Monitor serial output
pio device monitor
```

### Flashing Multiple Devices

1. Edit `config.h` with unique `DEVICE_ID`
2. Set `DEVICE_ROLE` (one device should be `ROLE_GATEWAY`)
3. Flash the device
4. Repeat for each device

## Network Architecture

```
[Sensor 1] ──ESP-NOW──> [Sensor 2] ──ESP-NOW──> [Gateway] <──BLE──> [Phone App]
    │                       │                       │
  PIR+CAM                 PIR+CAM                PIR+CAM+BLE
```

### Communication Protocol

- **ESP-NOW**: 250 byte packets, ~200m range per hop
- **Mesh Routing**: Automatic node discovery and relay
- **Image Transfer**: JPEG chunked into 200-byte packets
- **BLE**: GATT service with characteristics for:
  - Motion alerts (notify)
  - Image data (chunked notify)
  - Device status
  - Commands from phone

## Message Types

| Type | Description |
|------|-------------|
| HEARTBEAT | Node alive/discovery |
| MOTION_ALERT | Motion detected |
| IMAGE_START | Begin image transfer |
| IMAGE_CHUNK | Image data packet |
| IMAGE_END | Image transfer complete |
| ACK/NACK | Acknowledgments |

## LED Patterns

| Pattern | Meaning |
|---------|---------|
| Slow blink | Idle, waiting for motion |
| Fast blink | Initializing |
| Quick burst | Motion detected |
| Rapid blink | Transmitting data |
| SOS pattern | Error state |
| Solid on | BLE connected (gateway) |

## Field Deployment Tips

1. **Power**: Use 5V battery pack with sufficient capacity
2. **Placement**: Mount at animal height, PIR sensor facing trail
3. **Range**: Keep nodes within 50-200m of each other for reliable mesh
4. **Weather**: Protect in weatherproof enclosure
5. **Gateway Position**: Place gateway where phone can reach via BLE

## Troubleshooting

### Camera fails to initialize
- Check camera ribbon cable connection
- Ensure PSRAM is enabled in build flags
- Try reducing frame size in config.h

### Mesh not connecting
- Verify all devices use same `MESH_CHANNEL`
- Check devices are within range
- Monitor serial output for discovery messages

### BLE not advertising
- Ensure device role is `ROLE_GATEWAY`
- Restart device after changing role
- Check phone Bluetooth is enabled

## Future Enhancements

- [ ] SD card storage for offline buffering
- [ ] Deep sleep for battery optimization
- [ ] OTA firmware updates via BLE
- [ ] Android companion app
- [ ] Temperature/humidity sensors
- [ ] Solar power integration

## License

MIT License - feel free to modify for your hunting needs!


