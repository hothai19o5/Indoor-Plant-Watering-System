# INDOOR PLANT WATERING SYSTEM
## Images
### Android App
<p align="center">
  <img src="https://github.com/user-attachments/assets/89eb3115-d9ee-4dc7-a790-9b9ca165fac4" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/ae8b9729-70cf-4d86-932f-085d87b112b5" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/5c6b3616-40bc-4ab3-85d3-bdee10c54c84" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/05151efd-37f9-42f6-8011-a2e8f2e75e5a" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/cb7277fe-eab4-42da-ab2e-918da6e999ed" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/c54dfff5-7f9c-478d-bbc8-f3cc6496ec4b" alt="" width="30%">
</p>

### Hardware module wiring diagram
<p align="center">
  <img src="https://github.com/user-attachments/assets/eea64c6b-8024-4a24-aea1-fa3363c84fd9" alt="" width="90%">
</p>

## Code Esp8266
```cpp
#include <DHT.h>
#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP8266.h>                            // Thư viện Firebase
#include <WiFiManager.h>

#define DHTPIN D2                                       // Chân kết nối DHT22
#define DHTTYPE DHT22                                   // Định dạng cảm biến DHT22
#define SOIL_PIN A0                                     // Chân đọc cảm biến độ ẩm đất
#define RELAY1_PIN D5                                   // Chân điều khiển relay 1 (LED1)
#define RELAY2_PIN D6                                   // Chân điều khiển relay 2 (MÁY BƠM)
#define LED_DEBUG_PIN D7                                // Chân điều khiển led debug

DHT dht(DHTPIN, DHTTYPE);                               // Khởi tạo DHT22

WiFiUDP ntpUDP;                                         // Khởi tạo UDP

NTPClient timeClient(ntpUDP, "pool.ntp.org", 0, 60000); // Khởi tạo NTP Client (Không cộng múi giờ)

// Cấu hình Firebase
#define FIREBASE_HOST "YOUR URL PROJECT FIREBASE"       // URL dự án Firebase
#define FIREBASE_AUTH "YOUR DATABASE SECRET"            // Database Secret

FirebaseData firebaseData;
FirebaseData cmdData;                                   // Đối tượng FirebaseData riêng cho commands
FirebaseJson json;
FirebaseConfig firebaseConfig;
FirebaseAuth firebaseAuth;

unsigned long lastCommandCheck = 0;                     // Thời điểm kiểm tra lệnh cuối cùng
const long commandCheckInterval = 1000;                 // Kiểm tra lệnh mỗi 1 giây

// Biến trạng thái cho máy bơm và cờ ghi đè thủ công
bool pumpOn = false;
bool resetOn = false;
bool manualOverride = false;
unsigned long pumpStartTime = 0;                        // Lưu thời điểm bắt đầu bật bơm
const unsigned long pumpDuration = 10000;               // Thời gian chạy máy bơm (10 giây)

void configModeCallback (WiFiManager *myWiFiManager)
{
  Serial.println("Entered config mode");
  Serial.println(WiFi.softAPIP());
  Serial.println(myWiFiManager->getConfigPortalSSID());
}

void setup() {
  Serial.begin(115200);
  Serial.println();
  Serial.println("Booted");

  pinMode(RELAY1_PIN, OUTPUT);                          // Thiết lập chân relay là OUTPUT
  pinMode(RELAY2_PIN, OUTPUT);                          // Thiết lập chân relay là OUTPUT
  digitalWrite(RELAY1_PIN, LOW);                        // Tắt relay 1 khi khởi động
  digitalWrite(RELAY2_PIN, LOW);                        // Tắt relay 2 (máy bơm) khi khởi động
  
  pinMode(SOIL_PIN, INPUT);                             // Thiết lập chân đọc độ ẩm đất là INPUT
  pinMode(LED_DEBUG_PIN, OUTPUT);                       // Thiết lập chân led debug là OUTPUT
                      
  WiFiManager wifiManager;                              //Khai báo WiFi Manager
  
  wifiManager.setAPCallback(configModeCallback);        //Setup callback để khởi động AP với SSID "ESP+chipID"
  if (!wifiManager.autoConnect())
  {
    Serial.println("Failed to connect and hit timeout");
    ESP.reset();                                        //Nếu kết nối thất bại thì reset
    delay(1000);
  }
  Serial.println("Connected...");                       // Thành công thì thông báo ra màn hình

  // Cấu hình FirebaseConfig và FirebaseAuth
  firebaseConfig.host = FIREBASE_HOST;
  firebaseConfig.database_url = FIREBASE_HOST;
  firebaseConfig.signer.tokens.legacy_token = FIREBASE_AUTH;
  firebaseConfig.api_key = FIREBASE_AUTH;
  firebaseConfig.token_status_callback = tokenStatusCallback;
  firebaseConfig.database_url = FIREBASE_HOST;
  
  Firebase.begin(&firebaseConfig, &firebaseAuth);       // Bắt đầu kết nối Firebase
  Firebase.reconnectWiFi(true);                         // Tự động kết nối lại WiFi nếu bị ngắt

  timeClient.begin();                                   // Bắt đầu lấy thời gian từ NTP Server
  dht.begin();                                          // Bắt đầu đo nhiệt độ - độ ẩm
  
  Serial.println("System ready to receive commands");
}

void tokenStatusCallback(TokenInfo info) {
    Serial.println("Token Info: ");
    Serial.println(info.status);
}

void loop() {
  unsigned long currentMillis = millis();
  
  if (WiFi.status() != WL_CONNECTED) {                  // Kiểm tra kết nối WiFi
    Serial.println("WiFi connection lost. Reconnecting...");
    ESP.reset();
    delay(1000);
  }

  if (currentMillis - lastCommandCheck >= commandCheckInterval) {
    lastCommandCheck = currentMillis;
    checkCommands();
  }

  checkPump();                                          // Kiểm tra trạng thái máy bơm và tắt nếu quá thời gian

  // Kiểm tra commands từ Firebase mỗi 1 giây
  if (currentMillis - lastCommandCheck >= commandCheckInterval) {
    lastCommandCheck = currentMillis;
    checkCommands();
  }

  timeClient.update();                                  // Cập nhật lại thời gian

  // Đọc dữ liệu từ DHT22
  float temperature = dht.readTemperature();            // Nhiệt độ (C)
  float humidity = dht.readHumidity();                  // Độ ẩm (%)

  // Đọc độ ẩm đất từ cảm biến V1.2
  int soilMoisture = analogRead(SOIL_PIN);              // (0-1036) - tỉ lệ nghịch
  float percentSoilMoisture = 100 - map(soilMoisture, 350, 1024, 0, 100);

  // Kiểm tra nếu cảm biến lỗi
  if (isnan(temperature) || isnan(humidity) || soilMoisture <= 20 || soilMoisture >= 1023) {
    Serial.println("Sensor ERROR!!!");
    digitalWrite(LED_DEBUG_PIN, HIGH);                  // Bật led debug
    delay(1000);
    return;
  }

  digitalWrite(LED_DEBUG_PIN, LOW);                     // Tắt LED debug
  
  // In dữ liệu ra Serial Monitor
  Serial.print("Temperature: "); Serial.print(temperature); Serial.println(" °C");
  Serial.print("Humidity: "); Serial.print(humidity); Serial.println(" %");
  Serial.print("Soil Moisture: "); Serial.println(percentSoilMoisture);
  Serial.print("Time: "); Serial.println(timeClient.getFormattedTime());

  // Logic điều khiển tưới nước tự động
  int hour = timeClient.getHours();
  if(!manualOverride && (hour == 6 || hour == 18) && soilMoisture >= 800 && temperature <= 40) {
    if(!pumpOn){ //Chỉ bật máy bơm nếu máy bơm đang tắt
        pumpOn = true;
        digitalWrite(RELAY2_PIN, HIGH);
        Serial.println("Automatic watering started...");
        delay(10000);
        digitalWrite(RELAY2_PIN, LOW);
        Serial.println("Automatic watering finished...");
        pumpOn = false;
      }
  }

  // Gửi dữ liệu lên Firebase
  sendDataToFirebase(temperature, humidity, percentSoilMoisture);

  Serial.println("-----------------------------");
  delay(2000);                                          // Đọc dữ liệu mỗi 2 giây
}

void checkCommands() {
    if (Firebase.getJSON(cmdData, "/commands")) {
      if (cmdData.dataAvailable()) {
        Serial.print("Received JSON: ");
        Serial.println(cmdData.jsonString());

        FirebaseJson *json = cmdData.jsonObjectPtr();
        if (json != NULL && json->iteratorBegin() > 0) { 
          FirebaseJsonData jsonData;
          String firstKey;

          // Lấy key đầu tiên trong JSON (chính là ID động của Firebase)
          json->iteratorBegin();
          int type;
          String key, value;
          json->iteratorGet(0, type, key, value);       // Lấy key đầu tiên
          firstKey = key;
          json->iteratorEnd();

          Serial.print("First Key: ");
          Serial.println(firstKey);
   
          json->get(jsonData, firstKey + "/type");      // Lấy giá trị "type" từ key động

          if (jsonData.success) {
            String commandType = jsonData.stringValue;
            Serial.print("Processing command: ");
            Serial.println(commandType);

            // Xử lý lệnh
            if (commandType == "TURN_ON_PUMP") {
              turnOnPump();
            } else if (commandType == "TURN_OFF_PUMP") {
              pumpOn = false;
              manualOverride = true;
              digitalWrite(RELAY2_PIN, LOW);
              Serial.println("Pump turned OFF by app command");
            } else if (commandType == "RESET") {
              Serial.println("System reset by app command");
              resetOn = true;
            }

            // Xóa lệnh sau khi xử lý
            if (Firebase.deleteNode(cmdData, "/commands/" + firstKey)) {
              Serial.println("Command deleted successfully.");
            } else {
              Serial.print("Failed to delete command: ");
              Serial.println(cmdData.errorReason());
            }
            
            if(resetOn) {                               // Sau khi xóa lệnh thì mới reset
              resetOn = false;
              ESP.restart();
            }

          } else {
            Serial.println("Failed to get 'type' from JSON.");
          }
        } else {
          Serial.println("json == NULL || json->iteratorBegin() <= 0");
        }
      } else {
        Serial.println("cmdData.dataAvailable() = false");
      }
    } else {
      Serial.print("Failed to get command: ");
      Serial.println(cmdData.errorReason());
    }
}

void turnOnPump() {                                     // Bật bơm không dùng delay
    if (!pumpOn) {
      pumpOn = true;
      manualOverride = true;
      digitalWrite(RELAY2_PIN, HIGH);
      pumpStartTime = millis();                         // Ghi lại thời gian bật bơm
      Serial.println("Pump turned ON");
    }
}

void checkPump() {                                      // Hàm kiểm tra và tự tắt bơm sau thời gian chạy
    if (pumpOn && millis() - pumpStartTime >= pumpDuration) {
      pumpOn = false;
      digitalWrite(RELAY2_PIN, LOW);
      Serial.println("Pump turned OFF automatically after 10s");
    }
}

void sendDataToFirebase(float temperature, float humidity, float soilMoisture) {
  // Tạo JSON chứa dữ liệu cảm biến
  json.clear();
  json.set("temperature", temperature);
  json.set("humidity", humidity);
  json.set("soilMoisture", soilMoisture);
  json.set("timestamp", timeClient.getEpochTime());  // Thêm timestamp

  // Gửi lên Firebase
  if (Firebase.push(firebaseData, "/sensor_data", json)) {
    Serial.println("Data sent to Firebase successfully");
  } else {
    Serial.println("Failed to send data to Firebase");
    Serial.println("REASON: " + firebaseData.errorReason());
  }
}
