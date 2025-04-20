#include <DHT.h>
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP8266.h>
#include <WiFiManager.h>

#define DHTPIN D2                    // Chân kết nối DHT11
#define DHTTYPE DHT11                // Định dạng cảm biến DHT11
#define SOIL_PIN A0                  // Chân đọc cảm biến độ ẩm đất
#define RELAY1_PIN D5                // Chân điều khiển relay 1 (LED1)
#define RELAY2_PIN D6                // Chân điều khiển relay 2 (MÁY BƠM)
#define LED_BUG_DHT11_PIN D7         // Chân điều khiển led bug DHT11
#define LED_BUG_V12_PIN D3           // Chân điều khiển led bug V1.2
#define LED_NOT_CONNECT_WIFI_PIN D8  // Chân điều khiển Led khi không kết nối Wifi

DHT dht(DHTPIN, DHTTYPE);  // Khởi tạo DHT11

WiFiUDP ntpUDP;  // Khởi tạo UDP

NTPClient timeClient(ntpUDP, "pool.ntp.org", 0, 60000);  // Khởi tạo NTP Client (Không cộng múi giờ)

// Cấu hình Firebase
#define FIREBASE_HOST "projectii-fabc6-default-rtdb.asia-southeast1.firebasedatabase.app"  // URL dự án Firebase
#define FIREBASE_AUTH "2alu2RzqECbig8ebDKXUMkgCNKdBR8CDKIYA7JXB"                           // Database Secret

FirebaseData firebaseData;
FirebaseData cmdData;
FirebaseJson json;
FirebaseConfig firebaseConfig;
FirebaseAuth firebaseAuth;

unsigned long lastCommandCheck = 0;      // Thời điểm kiểm tra lệnh cuối cùng
const long commandCheckInterval = 1000;  // Kiểm tra lệnh mỗi 1 giây

// Biến trạng thái cho máy bơm và cờ ghi đè thủ công
bool pumpOn = false;
bool resetOn = false;
bool manualOverride = false;
unsigned long pumpStartTime = 0;     // Lưu thời điểm bắt đầu bật bơm
unsigned long pumpDuration = 10000;  // Thời gian chạy máy bơm (mặc định 10 giây)

void configModeCallback(WiFiManager* myWiFiManager) {
  Serial.println("Entered config mode");
  Serial.println(WiFi.softAPIP());
  Serial.println(myWiFiManager->getConfigPortalSSID());
}

// ---------------------------------- SETUP --------------------------------------
void setup() {
  Serial.begin(115200);
  Serial.println();
  Serial.println("Booted");

  pinMode(RELAY1_PIN, OUTPUT);    // Thiết lập chân relay là OUTPUT
  pinMode(RELAY2_PIN, OUTPUT);    // Thiết lập chân relay là OUTPUT
  digitalWrite(RELAY1_PIN, LOW);  // Tắt relay 1 khi khởi động
  digitalWrite(RELAY2_PIN, LOW);  // Tắt relay 2 (máy bơm) khi khởi động

  pinMode(SOIL_PIN, INPUT);             // Thiết lập chân đọc độ ẩm đất là INPUT
  pinMode(LED_BUG_DHT11_PIN, OUTPUT);   // Thiết lập chân led bug dht11 là OUTPUT
  pinMode(LED_BUG_V12_PIN, OUTPUT);     // Thiết lập chân led bug v1.2 là OUTPUT

  //Khai báo WiFi Manager
  WiFiManager wifiManager;
  //Setup callback để khởi động AP với SSID "ESP+chipID"
  wifiManager.setAPCallback(configModeCallback);
  if (!wifiManager.autoConnect()) {
    digitalWrite(LED_NOT_CONNECT_WIFI_PIN, HIGH);
    Serial.println("Failed to connect and hit timeout");
    //Nếu kết nối thất bại thì reset
    ESP.reset();
    delay(2000);
  }
  // Thành công thì thông báo ra màn hình
  Serial.println("Connected...");
  digitalWrite(LED_NOT_CONNECT_WIFI_PIN, LOW);

  // Cấu hình FirebaseConfig và FirebaseAuth
  firebaseConfig.host = FIREBASE_HOST;
  firebaseConfig.database_url = FIREBASE_HOST;
  firebaseConfig.signer.tokens.legacy_token = FIREBASE_AUTH;
  firebaseConfig.api_key = FIREBASE_AUTH;
  firebaseConfig.token_status_callback = tokenStatusCallback;
  firebaseConfig.database_url = FIREBASE_HOST;

  Firebase.begin(&firebaseConfig, &firebaseAuth);  // Bắt đầu kết nối Firebase
  Firebase.reconnectWiFi(true);                    // Tự động kết nối lại WiFi nếu bị ngắt

  timeClient.begin();  // Bắt đầu lấy thời gian từ NTP Server
  dht.begin();         // Bắt đầu đo nhiệt độ - độ ẩm
}
// ------------------------------------- END SETUP ----------------------------------

void tokenStatusCallback(TokenInfo info) {
  Serial.println("Token Info: ");
  Serial.println(info.status);
}

// -------------------------------------- LOOP --------------------------------------
void loop() {
  unsigned long currentMillis = millis();

  // Kiểm tra kết nối WiFi
  if (WiFi.status() != WL_CONNECTED) {
    digitalWrite(LED_NOT_CONNECT_WIFI_PIN, HIGH);
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

  timeClient.update();  // Cập nhật lại thời gian

  // Đọc dữ liệu từ DHT11
  float temperature = dht.readTemperature();  // Nhiệt độ (C)
  float humidity = dht.readHumidity();        // Độ ẩm (%)

  // Đọc độ ẩm đất từ cảm biến V1.2
  int soilMoisture = analogRead(SOIL_PIN);  // (0-1036) - tỉ lệ nghịch
  float percentSoilMoisture = 100 - map(soilMoisture, 350, 1024, 0, 100);

  // Kiểm tra nếu cảm biến lỗi
  if (isnan(temperature) || isnan(humidity)) {
    digitalWrite(LED_BUG_DHT11_PIN, HIGH);  // Bật led debug
    delay(60000);
    return;
  } else if (soilMoisture <= 20 || soilMoisture >= 1023) {
    digitalWrite(LED_BUG_V12_PIN, HIGH);  // Bật led debug
    delay(60000);
    return;
  }

  digitalWrite(LED_BUG_DHT11_PIN, LOW);   // Tắt LED debug
  digitalWrite(LED_BUG_V12_PIN, LOW);     // Tắt LED debug

  // Logic điều khiển tưới nước tự động
  int hour = timeClient.getHours();
  if (!manualOverride && (hour == 6 || hour == 18) && soilMoisture >= 800 && temperature <= 40) {
    turnOnPump();
  }

  // Gửi dữ liệu lên Firebase
  sendDataToFirebase(temperature, humidity, percentSoilMoisture);  //Gửi dữ liệu lên Firebase

  Serial.println("-----------------------------");
  delay(2000);  // Đọc dữ liệu mỗi 2 giây
}
// ----------------------------------- END LOOP ---------------------------------------

// ---------------------------------- CHECK COMMAND -----------------------------------
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

  FirebaseJson* json = cmdData.jsonObjectPtr();
  FirebaseJsonData jsonData;

  String commandPath = "";  // Đường dẫn đến node duy nhất
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
  if (!Firebase.deleteNode(cmdData, commandPath)) {
    Serial.print("Failed to delete command: ");
    Serial.println(cmdData.errorReason());
  }

  if (resetOn) {
    resetOn = false;
    ESP.restart();
  }
}
// ---------------- END CHECK COMMAND --------------------

// ---------------- CHECK CONFIG ---------------------
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
// --------------------------- END CHECK CONFIG -----------------------------

// ---------------------------- TURN ON PUMP ---------------------------------
void turnOnPump() {
  if (!pumpOn) {
    pumpOn = true;
    manualOverride = true;
    digitalWrite(RELAY2_PIN, HIGH);
    pumpStartTime = millis();  // Ghi lại thời gian bật bơm
    Serial.println("Pump turned ON");
  }
}
// ------------------------------ END TURN ON PUMP ----------------------------

// Hàm kiểm tra và tự tắt bơm sau thời gian chạy
void checkPump() {
  if (pumpOn && millis() - pumpStartTime >= pumpDuration) {
    pumpOn = false;
    digitalWrite(RELAY2_PIN, LOW);
    Serial.printf("Pump turned OFF automatically after %lu s\n", pumpDuration);
  }
}
// ------------------------------ END CHECK PUMP -----------------------------

// ------------------- Hàm gửi dữ liệu tới Firebase --------------------------
void sendDataToFirebase(float temperature, float humidity, float soilMoisture) {
  // Tạo JSON chứa dữ liệu cảm biến
  json.clear();
  json.set("temperature", temperature);
  json.set("humidity", humidity);
  json.set("soilMoisture", soilMoisture);
  unsigned long timestamp = timeClient.getEpochTime();

  // Gửi lên Firebase
  int mod = timestamp % 1800;
  // 30 phút gửi 1 lần, để thống kê dài ngày
  if (mod < 5) {
    json.set("timestamp", timestamp - mod);
    if (!Firebase.push(firebaseData, "/sensor_data_30min", json)) {
      Serial.println("Failed to send data to Firebase /sensor_data_30min");
      Serial.println("REASON: " + firebaseData.errorReason());
    }
    delay(3000);  // Tránh gửi 2 lần dữ liệu với 1 timestamp
  }
  // 3-4s gửi 1 lần, xem trực tiếp
  json.set("timestamp", timestamp);
  if (!Firebase.push(firebaseData, "/sensor_data", json)) {
    Serial.println("Failed to send data to Firebase /sensor_data");
    Serial.println("REASON: " + firebaseData.errorReason());
  }
}
