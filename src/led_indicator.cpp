#include "led_indicator.h"

// Global instance
LedIndicator ledIndicator;

LedIndicator::LedIndicator()
    : _currentPattern(LedPattern::OFF)
    , _ledState(false)
    , _lastUpdate(0)
    , _brightness(255)
    , _flashing(false)
    , _flashCount(0)
    , _flashRemaining(0)
    , _flashOnTime(50)
    , _flashOffTime(50)
    , _flashStart(0) {
}

void LedIndicator::begin() {
    // Configure LED pin
    pinMode(LED_PIN, OUTPUT);
    
    // Set up PWM for brightness control
    ledcSetup(0, 5000, 8);  // Channel 0, 5kHz, 8-bit resolution
    ledcAttachPin(LED_PIN, 0);
    
    // Start with LED off
    off();
    
    DEBUG_PRINTLN("[LED] Indicator initialized on GPIO " + String(LED_PIN));
}

void LedIndicator::setPattern(LedPattern pattern) {
    if (_currentPattern != pattern) {
        _currentPattern = pattern;
        _lastUpdate = millis();
        _ledState = false;
        
        // Cancel any ongoing flash sequence when pattern changes
        _flashing = false;
        
        DEBUG_PRINTF("[LED] Pattern set to %d\n", (int)pattern);
    }
}

LedPattern LedIndicator::getPattern() {
    return _currentPattern;
}

void LedIndicator::update() {
    unsigned long currentTime = millis();
    
    // Handle flash sequence first
    if (_flashing) {
        unsigned long elapsed = currentTime - _flashStart;
        int cycleDuration = _flashOnTime + _flashOffTime;
        int cyclePosition = elapsed % cycleDuration;
        
        // Determine if LED should be on or off in current cycle
        bool shouldBeOn = (cyclePosition < _flashOnTime);
        setLedState(shouldBeOn);
        
        // Check if flash sequence is complete
        int completedCycles = elapsed / cycleDuration;
        if (completedCycles >= _flashCount) {
            _flashing = false;
            setPattern(LedPattern::OFF);
        }
        return;
    }
    
    // Handle patterns
    switch (_currentPattern) {
        case LedPattern::OFF:
            setLedState(false);
            break;
            
        case LedPattern::ON:
            setLedState(true);
            break;
            
        case LedPattern::BLINK_SLOW:
            if (currentTime - _lastUpdate >= SLOW_BLINK_PERIOD / 2) {
                _ledState = !_ledState;
                setLedState(_ledState);
                _lastUpdate = currentTime;
            }
            break;
            
        case LedPattern::BLINK_FAST:
            if (currentTime - _lastUpdate >= FAST_BLINK_PERIOD / 2) {
                _ledState = !_ledState;
                setLedState(_ledState);
                _lastUpdate = currentTime;
            }
            break;
            
        case LedPattern::BLINK_MOTION:
            if (currentTime - _lastUpdate >= MOTION_BLINK_PERIOD / 2) {
                _ledState = !_ledState;
                setLedState(_ledState);
                _lastUpdate = currentTime;
            }
            break;
            
        case LedPattern::BLINK_TRANSMIT:
            if (currentTime - _lastUpdate >= TRANSMIT_BLINK_PERIOD / 2) {
                _ledState = !_ledState;
                setLedState(_ledState);
                _lastUpdate = currentTime;
            }
            break;
            
        case LedPattern::BLINK_ERROR: {
            // SOS pattern: ... --- ...
            static const int SOS_PATTERN[] = {1,0,1,0,1,0,0,0, 1,1,1,0,1,1,1,0,1,1,1,0,0,0, 1,0,1,0,1,0,0,0,0,0};
            static const int SOS_LENGTH = sizeof(SOS_PATTERN) / sizeof(SOS_PATTERN[0]);
            static int sosIndex = 0;
            
            if (currentTime - _lastUpdate >= 150) {
                setLedState(SOS_PATTERN[sosIndex] == 1);
                sosIndex = (sosIndex + 1) % SOS_LENGTH;
                _lastUpdate = currentTime;
            }
            break;
        }
            
        case LedPattern::PULSE: {
            // Smooth breathing effect using sine wave
            float phase = (float)(currentTime % PULSE_PERIOD) / PULSE_PERIOD;
            float brightness = (sin(phase * 2 * PI - PI/2) + 1) / 2;  // 0 to 1
            uint8_t pwmValue = (uint8_t)(brightness * _brightness);
            
            if (LED_ACTIVE_LOW) {
                pwmValue = 255 - pwmValue;
            }
            ledcWrite(0, pwmValue);
            break;
        }
    }
}

void LedIndicator::flash(int count, int onTime, int offTime) {
    _flashing = true;
    _flashCount = count;
    _flashRemaining = count;
    _flashOnTime = onTime;
    _flashOffTime = offTime;
    _flashStart = millis();
    
    DEBUG_PRINTF("[LED] Flash sequence: %d times\n", count);
}

void LedIndicator::on() {
    setLedState(true);
}

void LedIndicator::off() {
    setLedState(false);
}

void LedIndicator::toggle() {
    _ledState = !_ledState;
    setLedState(_ledState);
}

void LedIndicator::setBrightness(uint8_t brightness) {
    _brightness = brightness;
}

void LedIndicator::setLedState(bool state) {
    _ledState = state;
    
    uint8_t pwmValue = state ? _brightness : 0;
    
    if (LED_ACTIVE_LOW) {
        pwmValue = 255 - pwmValue;
    }
    
    ledcWrite(0, pwmValue);
}


