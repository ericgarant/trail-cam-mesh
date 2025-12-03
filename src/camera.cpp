#include "camera.h"

// Global instance
Camera camera;

Camera::Camera() 
    : _initialized(false)
    , _fb(nullptr) {
    _lastImage.data = nullptr;
    _lastImage.length = 0;
    _lastImage.timestamp = 0;
    _lastImage.valid = false;
}

bool Camera::begin() {
    if (_initialized) {
        DEBUG_PRINTLN("[CAM] Already initialized");
        return true;
    }
    
    // Camera configuration for Freenove ESP32-WROVER CAM
    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = Y2_GPIO_NUM;
    config.pin_d1 = Y3_GPIO_NUM;
    config.pin_d2 = Y4_GPIO_NUM;
    config.pin_d3 = Y5_GPIO_NUM;
    config.pin_d4 = Y6_GPIO_NUM;
    config.pin_d5 = Y7_GPIO_NUM;
    config.pin_d6 = Y8_GPIO_NUM;
    config.pin_d7 = Y9_GPIO_NUM;
    config.pin_xclk = XCLK_GPIO_NUM;
    config.pin_pclk = PCLK_GPIO_NUM;
    config.pin_vsync = VSYNC_GPIO_NUM;
    config.pin_href = HREF_GPIO_NUM;
    config.pin_sccb_sda = SIOD_GPIO_NUM;
    config.pin_sccb_scl = SIOC_GPIO_NUM;
    config.pin_pwdn = PWDN_GPIO_NUM;
    config.pin_reset = RESET_GPIO_NUM;
    config.xclk_freq_hz = 20000000;  // 20MHz XCLK
    config.pixel_format = PIXFORMAT_JPEG;
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.jpeg_quality = CAMERA_JPEG_QUALITY;
    config.fb_count = CAMERA_FB_COUNT;
    
    // Frame size based on PSRAM availability
    if (psramFound()) {
        DEBUG_PRINTLN("[CAM] PSRAM found, using larger buffer");
        config.frame_size = CAMERA_FRAME_SIZE;
        config.fb_count = 2;
    } else {
        DEBUG_PRINTLN("[CAM] No PSRAM, using smaller buffer");
        config.frame_size = FRAMESIZE_QVGA;
        config.fb_count = 1;
        config.fb_location = CAMERA_FB_IN_DRAM;
    }
    
    // Initialize camera
    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        DEBUG_PRINTF("[CAM] Init failed with error 0x%x\n", err);
        return false;
    }
    
    // Get sensor and apply settings
    sensor_t* sensor = esp_camera_sensor_get();
    if (sensor) {
        // Flip image if needed (depends on camera mounting)
        sensor->set_vflip(sensor, 0);
        sensor->set_hmirror(sensor, 0);
        
        // Adjust image quality settings
        sensor->set_brightness(sensor, 0);     // -2 to 2
        sensor->set_contrast(sensor, 0);       // -2 to 2
        sensor->set_saturation(sensor, 0);     // -2 to 2
        sensor->set_special_effect(sensor, 0); // 0 = No Effect
        sensor->set_whitebal(sensor, 1);       // Auto white balance
        sensor->set_awb_gain(sensor, 1);       // Auto WB gain
        sensor->set_wb_mode(sensor, 0);        // 0 = Auto
        sensor->set_exposure_ctrl(sensor, 1);  // Auto exposure
        sensor->set_aec2(sensor, 0);           // AEC DSP
        sensor->set_gain_ctrl(sensor, 1);      // Auto gain
        sensor->set_agc_gain(sensor, 0);       // AGC gain
        sensor->set_gainceiling(sensor, (gainceiling_t)0);
        sensor->set_bpc(sensor, 0);            // Black pixel correction
        sensor->set_wpc(sensor, 1);            // White pixel correction
        sensor->set_raw_gma(sensor, 1);        // Gamma correction
        sensor->set_lenc(sensor, 1);           // Lens correction
        sensor->set_dcw(sensor, 1);            // Downsize enable
    }
    
    _initialized = true;
    DEBUG_PRINTLN("[CAM] Camera initialized successfully");
    
    // Take a test capture to warm up
    capture();
    releaseFrame();
    
    return true;
}

bool Camera::capture() {
    if (!_initialized) {
        DEBUG_PRINTLN("[CAM] Not initialized");
        return false;
    }
    
    // Release previous frame if exists
    releaseFrame();
    
    // Capture new frame
    _fb = esp_camera_fb_get();
    if (!_fb) {
        DEBUG_PRINTLN("[CAM] Frame capture failed");
        _lastImage.valid = false;
        return false;
    }
    
    // Store image info
    _lastImage.data = _fb->buf;
    _lastImage.length = _fb->len;
    _lastImage.timestamp = millis();
    _lastImage.valid = true;
    
    DEBUG_PRINTF("[CAM] Captured image: %u bytes\n", _fb->len);
    
    return true;
}

CapturedImage Camera::getLastImage() {
    return _lastImage;
}

void Camera::releaseFrame() {
    if (_fb) {
        esp_camera_fb_return(_fb);
        _fb = nullptr;
        _lastImage.valid = false;
    }
}

bool Camera::isInitialized() {
    return _initialized;
}

uint8_t* Camera::getImageData() {
    if (_lastImage.valid) {
        return _lastImage.data;
    }
    return nullptr;
}

size_t Camera::getImageLength() {
    if (_lastImage.valid) {
        return _lastImage.length;
    }
    return 0;
}

bool Camera::setFrameSize(framesize_t size) {
    if (!_initialized) {
        return false;
    }
    
    sensor_t* sensor = esp_camera_sensor_get();
    if (sensor) {
        sensor->set_framesize(sensor, size);
        DEBUG_PRINTF("[CAM] Frame size set to %d\n", size);
        return true;
    }
    return false;
}

bool Camera::setJpegQuality(int quality) {
    if (!_initialized) {
        return false;
    }
    
    sensor_t* sensor = esp_camera_sensor_get();
    if (sensor) {
        sensor->set_quality(sensor, quality);
        DEBUG_PRINTF("[CAM] JPEG quality set to %d\n", quality);
        return true;
    }
    return false;
}


