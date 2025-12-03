#ifndef PIR_SENSOR_H
#define PIR_SENSOR_H

#include <Arduino.h>
#include "config.h"

// Callback function type for motion detection
typedef void (*MotionCallback)(void);

class PIRSensor {
public:
    PIRSensor();
    
    // Initialize the PIR sensor
    void begin();
    
    // Set callback function for motion detection
    void setMotionCallback(MotionCallback callback);
    
    // Check for motion (call in loop for debounce handling)
    void update();
    
    // Get current motion state
    bool isMotionDetected();
    
    // Get time since last motion
    unsigned long getTimeSinceLastMotion();
    
    // Reset the cooldown timer (useful after handling motion)
    void resetCooldown();
    
    // Enable/disable the sensor
    void setEnabled(bool enabled);
    bool isEnabled();

private:
    static void IRAM_ATTR handleInterrupt();
    
    static volatile bool _motionFlag;
    static volatile unsigned long _lastInterruptTime;
    
    MotionCallback _callback;
    unsigned long _lastMotionTime;
    unsigned long _lastTriggerTime;
    bool _enabled;
    bool _motionState;
};

// Global instance
extern PIRSensor pirSensor;

#endif // PIR_SENSOR_H


