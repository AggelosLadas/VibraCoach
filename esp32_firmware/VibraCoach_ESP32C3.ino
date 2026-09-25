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

U8G2_SSD1306_72X40_ER_F_HW_I2C u8g2(U8G2_R0, OLED_RST, OLED_SCL, OLED_SDA);

volatile bool connected = false;
volatile bool triggerConnectScreen = false;
volatile bool triggerDisconnectScreen = false;

volatile int pulsesRemaining = 0;
volatile bool ledState = false;
volatile unsigned long ledTimer = 0;
const int BLINK_SPEED = 90;

void setLed(bool on) {
    digitalWrite(LED_PIN, on ? LOW : HIGH);
}

void showScreen(const char *title, const char *msg) {
    u8g2.clearBuffer();
    u8g2.setFont(u8g2_font_profont11_tf);
    u8g2.drawStr(0, 14, title);
    u8g2.drawLine(0, 18, 72, 18);
    u8g2.drawStr(0, 32, msg);
    u8g2.sendBuffer();
}

void processLedPattern() {
    if (pulsesRemaining > 0) {
        if (millis() - ledTimer >= BLINK_SPEED) {
            ledTimer = millis();
            ledState = !ledState;
            setLed(ledState);
            pulsesRemaining--;
        }
    } else {
        setLed(false);
    }
}

void triggerBlinks(int count) {
    pulsesRemaining = (count * 2) - 1;
    ledState = true;
    setLed(true);
    ledTimer = millis();
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

        int count = 1;
        if (patternKey.indexOf("pattern4") != -1 || patternKey.indexOf("alert") != -1) {
            count = 4;
        } else if (patternKey.indexOf("pattern3") != -1 || patternKey.indexOf("timeout") != -1) {
            count = 3;
        } else if (patternKey.indexOf("pattern2") != -1 || patternKey.indexOf("foul") != -1) {
            count = 2;
        } else if (patternKey.indexOf("pattern1") != -1 || patternKey.indexOf("whistle") != -1) {
            count = 1;
        }

        triggerBlinks(count);
    }
};

void setup() {
    Serial.begin(115200);
    pinMode(LED_PIN, OUTPUT);
    setLed(false);

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
    processLedPattern();

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