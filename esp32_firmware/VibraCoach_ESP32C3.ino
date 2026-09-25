/*
 * VibraCoach ESP32-C3 BLE Receiver & Display
 * ABSOLUTE MINIMUM LATENCY VERSION
 * Board: ESP32-C3 SuperMini / 0.42" OLED Board
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <U8g2lib.h>
#include <Wire.h>

#define SERVICE_UUID        "19B10000-E0C5-4515-A513-00592D0B64F0"
#define CHARACTERISTIC_UUID "19B10001-E0C5-4515-A513-00592D0B64F0"

#define OLED_SDA            5
#define OLED_SCL            6
#define OLED_RST            4
#define LED_PIN             8     // Built-in Blue LED

U8G2_SSD1306_72X40_ER_F_HW_I2C u8g2(U8G2_R0, /* reset=*/ OLED_RST, /* clock=*/ OLED_SCL, /* data=*/ OLED_SDA);

// --- GLOBAL STATES ---
volatile bool connected = false;
volatile bool triggerConnectScreen = false;
volatile bool triggerDisconnectScreen = false;

// --- HIGH-SPEED LED STATE MACHINE ---
volatile int blinksRemaining = 0;
volatile bool blinkIsOffPhase = false;
unsigned long ledTimer = 0;
const int BLINK_SPEED = 80; // 80ms snappy LED pulse

// Helper to handle Active-Low LED
void setLed(bool on) {
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, on ? LOW : HIGH);
}

// Draw screen ONLY for Connection Status changes
void showScreen(const char* title, const char* msg) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_profont11_tf);
    u8g2.drawStr(0, 14, title);
    u8g2.drawLine(0, 18, 72, 18);
    u8g2.drawStr(0, 32, msg);
    u8g2.sendBuffer();
}

// Rapid non-blocking LED state machine
void processLedPattern() {
    if (blinksRemaining > 0) {
        if (millis() - ledTimer >= BLINK_SPEED) {
            ledTimer = millis();

            if (blinkIsOffPhase) {
                setLed(true);            // Turn ON pulse
                blinkIsOffPhase = false;
                blinksRemaining--;       // Decrement pulse count
            } else {
                setLed(false);           // Turn OFF gap
                blinkIsOffPhase = true;
            }
        }
    } else {
        // Solid ON if connected, OFF if disconnected
        setLed(connected);
    }
}

class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        connected = true;
        triggerConnectScreen = true; // Queue screen update in main loop
    }

    void onDisconnect(BLEServer* pServer) override {
        connected = false;
        triggerDisconnectScreen = true;
        BLEDevice::startAdvertising();
    }
};

class MyCharCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) override {
        // 1. INSTANT ZERO-LATENCY FEEDBACK
        setLed(false);
        blinkIsOffPhase = true;
        ledTimer = millis();

        // 2. READ RAW DATA (Format: "CommandName|patternKeyword" e.g., "Whistle|pattern4")
        String rawMsg = pChar->getValue();
        if (rawMsg.length() == 0) return;

        // 3. PARSE PATTERN KEYWORD SPECIFICALLY (Prevents command names like "Whistle" from matching keyword)
        int pipeIdx = rawMsg.indexOf('|');
        String patternKey = (pipeIdx != -1) ? rawMsg.substring(pipeIdx + 1) : rawMsg;
        patternKey.toLowerCase();

        if (patternKey.indexOf("pattern4") != -1) {
            blinksRemaining = 4;
        }
        else if (patternKey.indexOf("pattern3") != -1) {
            blinksRemaining = 3;
        }
        else if (patternKey.indexOf("pattern2") != -1) {
            blinksRemaining = 2;
        }
        else if (patternKey.indexOf("pattern1") != -1) {
            blinksRemaining = 1;
        }
        else if (patternKey.indexOf("alert") != -1) {
            blinksRemaining = 4;
        }
        else if (patternKey.indexOf("timeout") != -1) {
            blinksRemaining = 3;
        }
        else if (patternKey.indexOf("foul") != -1) {
            blinksRemaining = 2;
        }
        else if (patternKey.indexOf("whistle") != -1) {
            blinksRemaining = 1;
        }
        else {
            blinksRemaining = 1;
        }
    }
};

void setup() {
    Serial.begin(115200);
    setLed(false);

    // Init Screen once for connection status
    u8g2.begin();
    u8g2.setBusClock(400000);
    u8g2.setContrast(255);
    showScreen("STATUS", "Waiting...");

    // Init BLE
    BLEDevice::init("VibraCoach");
    BLEServer *pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());

    BLEService *pService = pServer->createService(SERVICE_UUID);
    BLECharacteristic *pChar = pService->createCharacteristic(
        CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE |
        BLECharacteristic::PROPERTY_NOTIFY
    );

    pChar->setCallbacks(new MyCharCallbacks());
    pChar->addDescriptor(new BLE2902());
    pService->start();

    BLEAdvertising *pAdv = BLEDevice::getAdvertising();
    pAdv->addServiceUUID(SERVICE_UUID);
    pAdv->setScanResponse(true);
    BLEDevice::startAdvertising();
}

void loop() {
    // 1. Process LED pulses at max priority with 0ms delay
    processLedPattern();

    // 2. Only update screen on connection state changes
    if (triggerConnectScreen) {
        triggerConnectScreen = false;
        showScreen("STATUS", "Connected!");
        Serial.println("\n[BLE] Connected");
    }

    if (triggerDisconnectScreen) {
        triggerDisconnectScreen = false;
        showScreen("STATUS", "Waiting...");
        Serial.println("\n[BLE] Disconnected");
    }

    delay(1);
}
