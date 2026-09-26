#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <U8g2lib.h>
#include <Wire.h>

#define SERVICE_UUID "19B10000-E0C5-4515-A513-00592D0B64F0"
#define CHARACTERISTIC_UUID "19B10001-E0C5-4515-A513-00592D0B64F0"

#define OLED_SDA 5
#define OLED_SCL 6
#define OLED_RST 4
#define LED_PIN 8
#define MOTOR_PIN 2

U8G2_SSD1306_72X40_ER_F_HW_I2C u8g2(U8G2_R0, OLED_RST, OLED_SCL, OLED_SDA);

volatile bool connected = false;
volatile bool triggerConnectScreen = false;
volatile bool triggerDisconnectScreen = false;

// --- Pattern Definitions (Even index = ON duration, Odd index = OFF duration, 0 = End) ---
const int pat1[] = {250, 0};                            // Pattern 1: One crisp short pulse
const int pat2[] = {150, 150, 150, 0};                  // Pattern 2: Two quick shorts
const int pat3[] = {700, 200, 200, 0};                  // Pattern 3: One long, one short
const int pat4[] = {100, 100, 100, 100, 100, 100, 800, 0}; // Pattern 4: 3 rapid pulses, 1 long hold

const int* activePattern = nullptr;
volatile int patternStep = 0;
volatile unsigned long nextPatternTime = 0;

void setIndicators(bool on) {
    digitalWrite(LED_PIN, on ? LOW : HIGH); // LED active-low
    digitalWrite(MOTOR_PIN, on ? HIGH : LOW); // Motor active-high
}

void showScreen(const char *title, const char *msg) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_profont11_tf);
    u8g2.drawStr(0, 14, title);
    u8g2.drawLine(0, 18, 72, 18);
    u8g2.drawStr(0, 32, msg);
    u8g2.sendBuffer();
}

void processPatterns() {
    if (activePattern == nullptr) return;

    if (millis() >= nextPatternTime) {
        int duration = activePattern[patternStep];

        if (duration == 0) { // End of pattern reached
            setIndicators(false);
            activePattern = nullptr;
            return;
        }

        setIndicators(patternStep % 2 == 0); // Even steps are ON, Odd steps are OFF
        nextPatternTime = millis() + duration;
        patternStep++;
    }
}

void triggerPattern(int patternId) {
    switch (patternId) {
        case 1: activePattern = pat1; break;
        case 2: activePattern = pat2; break;
        case 3: activePattern = pat3; break;
        case 4: activePattern = pat4; break;
        default: return;
    }
    patternStep = 0;
    nextPatternTime = 0; // Trigger immediately on next loop
}

class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer *pServer) override {
        connected = true;
        triggerConnectScreen = true;
    }

    void onDisconnect(BLEServer *pServer) override {
        connected = false;
        triggerDisconnectScreen = true;
        BLEDevice::startAdvertising();
    }
};

class MyCharCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) override {
        String rawMsg = pChar->getValue().c_str();
        if (rawMsg.length() == 0) return;

        int pipeIdx = rawMsg.indexOf('|');
        String patternKey = (pipeIdx != -1) ? rawMsg.substring(pipeIdx + 1) : rawMsg;
        patternKey.toLowerCase();

        int patternId = 1;
        if (patternKey.indexOf("pattern4") != -1 || patternKey.indexOf("alert") != -1) {
            patternId = 4;
        } else if (patternKey.indexOf("pattern3") != -1 || patternKey.indexOf("timeout") != -1) {
            patternId = 3;
        } else if (patternKey.indexOf("pattern2") != -1 || patternKey.indexOf("foul") != -1) {
            patternId = 2;
        } else if (patternKey.indexOf("pattern1") != -1 || patternKey.indexOf("whistle") != -1) {
            patternId = 1;
        }

        triggerPattern(patternId);
    }
};

void setup() {
    Serial.begin(115200);

    pinMode(LED_PIN, OUTPUT);
    pinMode(MOTOR_PIN, OUTPUT);
    setIndicators(false);

    u8g2.begin();
    u8g2.setBusClock(400000);
    u8g2.setContrast(255);
    showScreen("STATUS", "Waiting...");

    BLEDevice::init("VibraCoach");
    BLEServer *pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());

    BLEService *pService = pServer->createService(SERVICE_UUID);
    BLECharacteristic *pChar = pService->createCharacteristic(
            CHARACTERISTIC_UUID,
            BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);

    pChar->setCallbacks(new MyCharCallbacks());
    pChar->addDescriptor(new BLE2902());
    pService->start();

    BLEAdvertising *pAdv = BLEDevice::getAdvertising();
    pAdv->addServiceUUID(SERVICE_UUID);
    pAdv->setScanResponse(true);
    BLEDevice::startAdvertising();
}

void loop() {
    processPatterns();

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