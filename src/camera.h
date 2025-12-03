#ifndef CAMERA_H
#define CAMERA_H

#include <Arduino.h>
#include "esp_camera.h"
#include "config.h"

// Image data structure
struct CapturedImage {
    uint8_t* data;      // JPEG data pointer
    size_t length;      // JPEG data length
    uint32_t timestamp; // Capture timestamp
    bool valid;         // Whether the image is valid
};

class Camera {
public:
    Camera();
    
    // Initialize the camera
    bool begin();
    
    // Capture a JPEG image
    bool capture();
    
    // Get the last captured image
    CapturedImage getLastImage();
    
    // Release the frame buffer (must be called after processing)
    void releaseFrame();
    
    // Check if camera is initialized
    bool isInitialized();
    
    // Get image data for chunked transmission
    uint8_t* getImageData();
    size_t getImageLength();
    
    // Set frame size (quality vs speed tradeoff)
    bool setFrameSize(framesize_t size);
    
    // Set JPEG quality (0-63, lower = better quality)
    bool setJpegQuality(int quality);

private:
    bool _initialized;
    camera_fb_t* _fb;
    CapturedImage _lastImage;
};

// Global instance
extern Camera camera;

#endif // CAMERA_H


