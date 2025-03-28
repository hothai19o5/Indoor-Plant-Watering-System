# INDOOR PLANT WATERING SYSTEM
## Images
### Android App
<p align="center">
  <img src="https://github.com/user-attachments/assets/89eb3115-d9ee-4dc7-a790-9b9ca165fac4" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/589abbd2-8fb0-4771-9d4d-71b01cf1fc3b" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/780ef7d3-ece8-4cef-9f74-7057cbdca9a1" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/098b7cf9-32fb-4fb7-8989-5de7894e81fa" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/19f0b916-e32a-4e65-a820-40f8e970b52e" alt="" width="30%">
  <img src="https://github.com/user-attachments/assets/944dbc05-c679-4dae-8753-7f5476bad9ad" alt="" width="30%">
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
unsigned long pumpDuration = 10000;                     // Thời gian chạy máy bơm (mặc định 10 giây)

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

  pinMode(RELAY1_PIN, OUTPUT);  // Thiết lập chân relay là OUTPUT
  pinMode(RELAY2_PIN, OUTPUT);  // Thiết lập chân relay là OUTPUT
  digitalWrite(RELAY1_PIN, LOW);  // Tắt relay 1 khi khởi động
  digitalWrite(RELAY2_PIN, LOW);  // Tắt relay 2 (máy bơm) khi khởi động
  
  pinMode(SOIL_PIN, INPUT);      // Thiết lập chân đọc độ ẩm đất là INPUT
  pinMode(LED_DEBUG_PIN, OUTPUT); // Thiết lập chân led debug là OUTPUT
  
  //Khai báo WiFi Manager
  WiFiManager wifiManager;
  //Setup callback để khởi động AP với SSID "ESP+chipID"
  wifiManager.setAPCallback(configModeCallback);
  if (!wifiManager.autoConnect())
  {
    Serial.println("Failed to connect and hit timeout");
    //Nếu kết nối thất bại thì reset
    ESP.reset();
    delay(1000);
  }
  // Thành công thì thông báo ra màn hình
  Serial.println("Connected...");

  // Cấu hình FirebaseConfig và FirebaseAuth
  firebaseConfig.host = FIREBASE_HOST;
  firebaseConfig.database_url = FIREBASE_HOST;
  firebaseConfig.signer.tokens.legacy_token = FIREBASE_AUTH;
  firebaseConfig.api_key = FIREBASE_AUTH;
  firebaseConfig.token_status_callback = tokenStatusCallback;
  firebaseConfig.database_url = FIREBASE_HOST;
  
  Firebase.begin(&firebaseConfig, &firebaseAuth); // Bắt đầu kết nối Firebase
  Firebase.reconnectWiFi(true);             // Tự động kết nối lại WiFi nếu bị ngắt

  timeClient.begin();   // Bắt đầu lấy thời gian từ NTP Server
  dht.begin();          // Bắt đầu đo nhiệt độ - độ ẩm
  
  Serial.println("System ready to receive commands");
}

void tokenStatusCallback(TokenInfo info) {
    Serial.println("Token Info: ");
    Serial.println(info.status);
}

void loop() {
  unsigned long currentMillis = millis();
  
  // Kiểm tra kết nối WiFi
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi connection lost. Reconnecting...");
    ESP.reset();
    delay(1000);
  }

  if (currentMillis - lastCommandCheck >= commandCheckInterval) {
    lastCommandCheck = currentMillis;
    checkCommands();
    checkConfig();
  }

  checkPump();  // Kiểm tra trạng thái máy bơm và tắt nếu quá thời gian

  // Kiểm tra commands từ Firebase mỗi 1 giây
  if (currentMillis - lastCommandCheck >= commandCheckInterval) {
    lastCommandCheck = currentMillis;
    checkCommands();
  }

  timeClient.update();      // Cập nhật lại thời gian

  // Đọc dữ liệu từ DHT22
  float temperature = dht.readTemperature();  // Nhiệt độ (C)
  float humidity = dht.readHumidity();        // Độ ẩm (%)

  // Đọc độ ẩm đất từ cảm biến V1.2
  int soilMoisture = analogRead(SOIL_PIN);    // (0-1036) - tỉ lệ nghịch
  float percentSoilMoisture = 100 - map(soilMoisture, 350, 1024, 0, 100);

  // Kiểm tra nếu cảm biến lỗi
  if (isnan(temperature) || isnan(humidity) || soilMoisture <= 20 || soilMoisture >= 1023) {
    Serial.println("Sensor ERROR!!!");
    digitalWrite(LED_DEBUG_PIN, HIGH);      // Bật led debug
    delay(1000);
    return;
  }

  digitalWrite(LED_DEBUG_PIN, LOW);         // Tắt LED debug
  
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
  sendDataToFirebase(temperature, humidity, percentSoilMoisture); //Gửi dữ liệu lên Firebase

  Serial.println("-----------------------------");
  delay(2000); // Đọc dữ liệu mỗi 2 giây
}

void checkCommands() {
  if (!Firebase.getJSON(cmdData, "/commands")) {
    Serial.print("Failed to get command: ");
    Serial.println(cmdData.errorReason());
    return;
  }

  if (!cmdData.dataAvailable()) {
    Serial.println("No command available.");
    return;
  }

  Serial.print("Received JSON: ");
  Serial.println(cmdData.jsonString());

  FirebaseJson *json = cmdData.jsonObjectPtr();
  FirebaseJsonData jsonData;

  String commandPath = ""; // Đường dẫn đến node duy nhất
  json->iteratorBegin();
  int type;
  String key, value;
  json->iteratorGet(0, type, key, value);
  json->iteratorEnd();

  commandPath = "/commands/" + key;
  json->get(jsonData, key + "/type");

  if (!jsonData.success) {
    Serial.println("Failed to get command type.");
    return;
  }

  String commandType = jsonData.stringValue;
  Serial.print("Processing command: ");
  Serial.println(commandType);

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

  // Xoá command
  if (Firebase.deleteNode(cmdData, commandPath)) {
    Serial.println("Command deleted successfully.");
  } else {
    Serial.print("Failed to delete command: ");
    Serial.println(cmdData.errorReason());
  }

  if (resetOn) {
    resetOn = false;
    ESP.restart();
  }
}

void checkConfig() {
  if (!Firebase.getInt(cmdData, "/config/pumpDuration")) {
    Serial.print("Failed to get pumpDuration: ");
    Serial.println(cmdData.errorReason());
    return;
  }

  pumpDuration = cmdData.intData();
  Serial.print("Updated pumpDuration: ");
  Serial.println(pumpDuration);
}

// Bật bơm không dùng delay
void turnOnPump() {
  if (!pumpOn) {
    pumpOn = true;
    manualOverride = true;
    digitalWrite(RELAY2_PIN, HIGH);
    pumpStartTime = millis();  // Ghi lại thời gian bật bơm
    Serial.println("Pump turned ON");
  }
}

// Hàm kiểm tra và tự tắt bơm sau thời gian chạy
void checkPump() {
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
  json.set("timestamp", timeClient.getEpochTime());

  // Gửi lên Firebase
  if (Firebase.push(firebaseData, "/sensor_data", json)) {
    Serial.println("Data sent to Firebase successfully");
  } else {
    Serial.println("Failed to send data to Firebase");
    Serial.println("REASON: " + firebaseData.errorReason());
  }
}
