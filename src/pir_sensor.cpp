#include "pir_sensor.h"

// Static member initialization
volatile bool PIRSensor::_motionFlag = false;
volatile unsigned long PIRSensor::_lastInterruptTime = 0;

// Global instance
PIRSensor pirSensor;

PIRSensor::PIRSensor() 
    : _callback(nullptr)
    , _lastMotionTime(0)
    , _lastTriggerTime(0)
    , _enabled(true)
    , _motionState(false) {
}

void PIRSensor::begin() {
    // Configure PIR pin as input with internal pulldown
    pinMode(PIR_PIN, INPUT_PULLDOWN);
    
    // Attach interrupt for rising edge (motion detected)
    attachInterrupt(digitalPinToInterrupt(PIR_PIN), handleInterrupt, RISING);
    
    DEBUG_PRINTLN("[PIR] Sensor initialized on GPIO " + String(PIR_PIN));
}

void IRAM_ATTR PIRSensor::handleInterrupt() {
    unsigned long currentTime = millis();
    
    // Debounce check
    if (currentTime - _lastInterruptTime > PIR_DEBOUNCE_MS) {
        _motionFlag = true;
        _lastInterruptTime = currentTime;
    }
}

void PIRSensor::setMotionCallback(MotionCallback callback) {
    _callback = callback;
}

void PIRSensor::update() {
    if (!_enabled) {
        return;
    }
    
    // Check if motion was detected via interrupt
    if (_motionFlag) {
        _motionFlag = false;
        
        unsigned long currentTime = millis();
        
        // Check cooldown period
        if (currentTime - _lastTriggerTime >= PIR_COOLDOWN_MS) {
            _lastMotionTime = currentTime;
            _lastTriggerTime = currentTime;
            _motionState = true;
            
            DEBUG_PRINTLN("[PIR] Motion detected!");
            
            // Call the callback if set
            if (_callback != nullptr) {
                _callback();
            }
        } else {
            DEBUG_PRINTLN("[PIR] Motion ignored (cooldown active)");
        }
    }
    
    // Reset motion state after a short period
    if (_motionState && (millis() - _lastMotionTime > 1000)) {
        _motionState = false;
    }
}

bool PIRSensor::isMotionDetected() {
    return _motionState;
}

unsigned long PIRSensor::getTimeSinceLastMotion() {
    if (_lastMotionTime == 0) {
        return ULONG_MAX;
    }
    return millis() - _lastMotionTime;
}

void PIRSensor::resetCooldown() {
    _lastTriggerTime = 0;
}

void PIRSensor::setEnabled(bool enabled) {
    _enabled = enabled;
    DEBUG_PRINTLN("[PIR] Sensor " + String(enabled ? "enabled" : "disabled"));
}

bool PIRSensor::isEnabled() {
    return _enabled;
}


