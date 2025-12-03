# Trail Cam Mesh - Android Companion App

Android companion app for the ESP32 Trail Camera Mesh Network system. Connects to the gateway device via Bluetooth LE to receive motion alerts and captured images.

## Features

- **BLE Connection**: Scan and connect to Trail Cam Gateway
- **Motion Alerts**: Real-time notifications when cameras detect movement
- **Image Viewer**: View captured images from trail cameras
- **Mesh Status**: Monitor battery and signal strength of all cameras
- **Push Notifications**: Get alerted even when app is in background

## Screenshots

The app has 4 main screens:

1. **Connect** - Scan for and connect to the gateway
2. **Alerts** - List of motion detection events
3. **Images** - Gallery of captured images
4. **Status** - Mesh network node status

## Requirements

- Android 8.0 (API 26) or higher
- Bluetooth LE support
- Location permission (required for BLE scanning)

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17

### Steps

1. Open the `android-app` folder in Android Studio
2. Wait for Gradle sync to complete
3. Connect an Android device or start an emulator
4. Click Run (or press Shift+F10)

### Command Line Build

```bash
cd android-app
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Usage

1. **Power on** your Trail Cam Gateway (ESP32 with ROLE_GATEWAY)
2. **Open the app** and grant Bluetooth/Location permissions
3. **Tap "Scan for Gateway"** to find your device
4. **Tap on the device** to connect
5. **Navigate to Alerts/Images** to see detections

## Architecture

```
com.trailcam.mesh/
├── MainActivity.kt          # Main entry point with navigation
├── TrailCamApplication.kt   # Application class
├── ble/
│   ├── BleManager.kt        # BLE scanning, connection, data handling
│   └── BleService.kt        # Foreground service for background operation
├── data/
│   └── Models.kt            # Data classes matching ESP32 protocol
└── ui/
    ├── MainViewModel.kt     # UI state management
    ├── theme/Theme.kt       # Forest-inspired color theme
    └── screens/
        ├── ConnectionScreen.kt
        ├── AlertsScreen.kt
        ├── ImagesScreen.kt
        └── StatusScreen.kt
```

## BLE Protocol

The app communicates with the ESP32 gateway using these GATT characteristics:

| UUID | Name | Description |
|------|------|-------------|
| `beb5483e-...` | Motion | Notifications for motion alerts |
| `cba1d466-...` | Image | Chunked image data transfer |
| `2c957792-...` | Status | Node status updates |
| `f27b53ad-...` | Command | Commands to gateway |

## Permissions

| Permission | Purpose |
|------------|---------|
| BLUETOOTH_SCAN | Discover nearby BLE devices |
| BLUETOOTH_CONNECT | Connect to gateway |
| ACCESS_FINE_LOCATION | Required for BLE scanning |
| POST_NOTIFICATIONS | Motion alert notifications |
| FOREGROUND_SERVICE | Keep connection alive in background |

## Troubleshooting

### Can't find gateway
- Ensure ESP32 gateway is powered on
- Check that DEVICE_ROLE is set to ROLE_GATEWAY
- Move closer to the gateway (within 10m)

### Connection drops
- Keep phone within Bluetooth range
- Avoid physical obstructions
- Check gateway battery level

### Images not loading
- Image transfer can take several seconds
- Check mesh network connectivity
- Verify ESP32 camera is working

## License

MIT License


