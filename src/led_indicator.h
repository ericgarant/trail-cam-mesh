#ifndef LED_INDICATOR_H
#define LED_INDICATOR_H

#include <Arduino.h>
#include "config.h"

// LED patterns enumeration
enum class LedPattern {
    OFF,            // LED off
    ON,             // LED on solid
    BLINK_SLOW,     // Slow blink (1 Hz)
    BLINK_FAST,     // Fast blink (4 Hz)
    BLINK_MOTION,   // Quick burst for motion detection
    BLINK_TRANSMIT, // Rapid blink during transmission
    BLINK_ERROR,    // SOS-style error pattern
    PULSE           // Smooth pulsing (breathing effect)
};

class LedIndicator {
public:
    LedIndicator();
    
    // Initialize the LED
    void begin();
    
    // Set LED pattern
    void setPattern(LedPattern pattern);
    
    // Get current pattern
    LedPattern getPattern();
    
    // Update LED state (call in loop)
    void update();
    
    // Quick flash for events (non-blocking)
    void flash(int count = 3, int onTime = 50, int offTime = 50);
    
    // Direct LED control
    void on();
    void off();
    void toggle();
    
    // Set LED brightness (0-255, requires PWM)
    void setBrightness(uint8_t brightness);

private:
    void setLedState(bool state);
    
    LedPattern _currentPattern;
    bool _ledState;
    unsigned long _lastUpdate;
    uint8_t _brightness;
    
    // For flash sequence
    bool _flashing;
    int _flashCount;
    int _flashRemaining;
    int _flashOnTime;
    int _flashOffTime;
    unsigned long _flashStart;
    
    // Pattern timing
    static const int SLOW_BLINK_PERIOD = 1000;
    static const int FAST_BLINK_PERIOD = 250;
    static const int MOTION_BLINK_PERIOD = 100;
    static const int TRANSMIT_BLINK_PERIOD = 50;
    static const int PULSE_PERIOD = 2000;
};

// Global instance
extern LedIndicator ledIndicator;

#endif // LED_INDICATOR_H


