#include <DHT.h>
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP8266.h>
#include <WiFiManager.h>
#include <Wire.h>
#include <Adafruit_INA219.h>

#define DHTPIN D2     // Chân kết nối DHT11
#define DHTTYPE DHT11 // Định dạng cảm biến DHT11
#define SOIL_PIN A0   // Chân đọc cảm biến độ ẩm đất
#define RELAY2_PIN D6 // Chân điều khiển relay 2 (MÁY BƠM)
#define LED_BUG D8    // Chân điều khiển Led khi không kết nối Wifi
#define TRIG_PIN D4   // GPIO2
#define ECHO_PIN D7   // GPIO0

// Khởi tạo INA219
Adafruit_INA219 ina219;

DHT dht(DHTPIN, DHTTYPE); // Khởi tạo DHT11

WiFiUDP ntpUDP; // Khởi tạo UDP

NTPClient timeClient(ntpUDP, "pool.ntp.org", 0, 60000); // Khởi tạo NTP Client (Không cộng múi giờ)

// Cấu hình Firebase
#define FIREBASE_HOST "projectii-fabc6-default-rtdb.asia-southeast1.firebasedatabase.app" // URL dự án Firebase
#define FIREBASE_AUTH "2alu2RzqECbig8ebDKXUMkgCNKdBR8CDKIYA7JXB"                          // Database Secret

FirebaseData firebaseData;
FirebaseData cmdData;
FirebaseJson json;
FirebaseConfig firebaseConfig;
FirebaseAuth firebaseAuth;

unsigned long lastCommandCheck = 0;     // Thời điểm kiểm tra lệnh cuối cùng
const long commandCheckInterval = 1000; // Kiểm tra lệnh mỗi 1 giây

unsigned long lastLedCheck = 0;
unsigned long lastUpdateData = 0;

// Biến trạng thái cho máy bơm và cờ ghi đè thủ công
bool pumpOn = false;
bool resetOn = false;
bool manualOverride = false;
unsigned long pumpStartTime = 0;    // Lưu thời điểm bắt đầu bật bơm
unsigned long pumpDuration = 10000; // Thời gian chạy máy bơm (mặc định 10 giây)
bool autoPump = false;
int typeError = 0;

float temperature = 0;
float humidity = 0;
float percentSoilMoisture = 0;
float batteryLevel = 0;
float tankWaterLevel = 0;

float heightTankWater = 100;

bool sendNotiLowPower = true;
bool sendNotiDhtError = true;
bool sendNotiSoilMoisError = true;

void configModeCallback(WiFiManager *myWiFiManager)
{
  unsigned long currentMillis = millis();
  Serial.print(currentMillis);
  Serial.print("    >>    Entered config mode    ");
  Serial.println(WiFi.softAPIP());
  Serial.print(currentMillis);
  Serial.print("    >>    ");
  Serial.println(myWiFiManager->getConfigPortalSSID());
}

// ---------------------------------- SETUP --------------------------------------
void setup()
{
  unsigned long currentMillis = millis();
  Serial.begin(115200);
  Serial.println();
  Serial.print(currentMillis);
  Serial.println("    >>    Booted");

  // Khởi tạo I2C trên D5 (SDA), D1 (SCL)
  Wire.begin(D5, D1);

  // Khởi động INA219
  if (!ina219.begin())
  {
    Serial.print(currentMillis);
    Serial.println("    >>    Cannot find INA219.");
    typeError = 3;
  }

  // Thiết lập chân Trig và Echo của HC SR04
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  pinMode(RELAY2_PIN, OUTPUT);   // Thiết lập chân relay là OUTPUT
  digitalWrite(RELAY2_PIN, LOW); // Tắt relay 2 (máy bơm) khi khởi động

  pinMode(SOIL_PIN, INPUT); // Thiết lập chân đọc độ ẩm đất là INPUT

  pinMode(LED_BUG, OUTPUT); // Thiết lập chân led bug dht11 là OUTPUT

  // Khai báo WiFi Manager
  WiFiManager wifiManager;
  // Setup callback để khởi động AP với SSID "ESP+chipID"
  wifiManager.setAPCallback(configModeCallback);
  if (!wifiManager.autoConnect())
  {
    digitalWrite(LED_BUG, HIGH);
    Serial.print(currentMillis);
    Serial.println("    >>    Failed to connect wifi and hit timeout");
    // Nếu kết nối thất bại thì reset
    delay(1000);
    ESP.reset();
  }
  // Thành công thì thông báo ra màn hình
  Serial.print(currentMillis);
  Serial.println("    >>    Connected...");
  digitalWrite(LED_BUG, LOW);

  // Cấu hình FirebaseConfig và FirebaseAuth
  firebaseConfig.host = FIREBASE_HOST;
  firebaseConfig.database_url = FIREBASE_HOST;
  firebaseConfig.signer.tokens.legacy_token = FIREBASE_AUTH;
  firebaseConfig.api_key = FIREBASE_AUTH;
  firebaseConfig.token_status_callback = tokenStatusCallback;
  firebaseConfig.database_url = FIREBASE_HOST;

  Firebase.begin(&firebaseConfig, &firebaseAuth); // Bắt đầu kết nối Firebase
  Firebase.reconnectWiFi(true);                   // Tự động kết nối lại WiFi nếu bị ngắt

  timeClient.begin(); // Bắt đầu lấy thời gian từ NTP Server
  dht.begin();        // Bắt đầu đo nhiệt độ - độ ẩm
}
// ------------------------------------- END SETUP ----------------------------------

void tokenStatusCallback(TokenInfo info)
{
  Serial.print(millis());
  Serial.print("    >>    Token Info: ");
  Serial.println(info.status);
}

// -------------------------------------- LOOP --------------------------------------
void loop()
{
  unsigned long currentMillis = millis();

  ledBug(typeError);

  // Kiểm tra kết nối WiFi
  if (WiFi.status() != WL_CONNECTED)
  {
    digitalWrite(LED_BUG, HIGH);
    Serial.print(currentMillis);
    Serial.println("    >>    WiFi connection lost. Reconnecting...");
    WiFi.reconnect(); // Thử kết nối lại WiFi
    typeError = 5;    // Đặt lỗi kết nối WiFi
    delay(2000);
  }
  else
  {
    if (typeError == 5)
    {
      typeError = 0; // Reset lỗi kết nối WiFi nếu đã kết nối lại
    }
  }

  if (currentMillis - lastCommandCheck >= commandCheckInterval)
  {
    lastCommandCheck = currentMillis;
    checkCommands();
    checkConfig();
  }

  checkPump(); // Kiểm tra trạng thái máy bơm và tắt nếu quá thời gian

  timeClient.update(); // Cập nhật lại thời gian

  if (currentMillis - lastUpdateData >= 2000)
  {
    // Đọc dữ liệu từ DHT11
    temperature = dht.readTemperature(); // Nhiệt độ (C)
    humidity = dht.readHumidity();       // Độ ẩm (%)

    // Đọc độ ẩm đất từ cảm biến V1.2
    int soilMoisture = analogRead(SOIL_PIN); // (0-1036) - tỉ lệ nghịch
    percentSoilMoisture = 100 - map(soilMoisture, 350, 1024, 0, 100);

    // Lấy dữ liệu mức pin
    batteryLevel = checkLevelBattery();

    // Lấy dữ liệu mức nước
    tankWaterLevel = checkLevelWater();

    // Kiểm tra nếu cảm biến lỗi
    if (isnan(temperature) || isnan(humidity))
    {
      typeError = 1;

      if(!sendNotiDhtError) {
        // Gửi thông báo lên Firebase, sau đó Cloud Function sẽ gửi thông báo về App thông qua FCM
        json.clear();
        unsigned long timestamp = timeClient.getEpochTime();
        json.set("timestamp", timestamp);
        json.set("desc", "Sensor DHT11 Error");
        if (!Firebase.push(firebaseData, "/notification", json))
        {
          Serial.print(pumpStartTime);
          Serial.println("    >>    Failed to send data to Firebase /notification");
          Serial.print(pumpStartTime);
          Serial.println("    >>    REASON: " + firebaseData.errorReason());
          typeError = 6; // Lỗi kết nối Firebase
        } else if(typeError == 6) {
          typeError = 0; // Reset lỗi kết nối Firebase nếu gửi thành công
        }
        sendNotiDhtError = true;
      }
    }
    else
    {
      if (typeError == 1)
      {
        typeError = 0; // Reset lỗi cảm biến nếu không có lỗi
      }
      sendNotiDhtError = false;
    }

    if (soilMoisture <= 20 || soilMoisture >= 1023)
    {
      typeError = 2;

      if(!sendNotiSoilMoisError) {
        // Gửi thông báo lên Firebase, sau đó Cloud Function sẽ gửi thông báo về App thông qua FCM
        json.clear();
        unsigned long timestamp = timeClient.getEpochTime();
        json.set("timestamp", timestamp);
        json.set("desc", "Sensor Soil Moisture Error");
        if (!Firebase.push(firebaseData, "/notification", json))
        {
          Serial.print(pumpStartTime);
          Serial.println("    >>    Failed to send data to Firebase /notification");
          Serial.print(pumpStartTime);
          Serial.println("    >>    REASON: " + firebaseData.errorReason());
          typeError = 6; // Lỗi kết nối Firebase
        } else if(typeError == 6) {
          typeError = 0; // Reset lỗi kết nối Firebase nếu gửi thành công
        }
        sendNotiSoilMoisError = true;
      }
    }
    else
    {
      if (typeError == 2)
      {
        typeError = 0; // Reset lỗi cảm biến độ ẩm đất nếu không có lỗi
      }
      sendNotiSoilMoisError = false;
    }

    if (typeError == 0)
    {
      // Gửi dữ liệu lên Firebase
      sendDataToFirebase(temperature, humidity, percentSoilMoisture, batteryLevel, tankWaterLevel); // Gửi dữ liệu lên Firebase
    }
    Serial.print(currentMillis);
    Serial.println("    >>    Send data to Firebase");
    Serial.println(".............................................................");
  }

  // Logic điều khiển tưới nước tự động
  int hour = timeClient.getHours();
  if (!manualOverride && (hour == 6 || hour == 18) && percentSoilMoisture <= 50 && temperature <= 40 && autoPump)
  {
    turnOnPump();
    Serial.print(currentMillis);
    Serial.println("    >>    Auto Pump");
  }

}
// ----------------------------------- END LOOP ---------------------------------------

// ---------------------------------- CHECK COMMAND -----------------------------------
void checkCommands()
{
  unsigned long currentMillis = millis();
  if (!Firebase.getJSON(cmdData, "/commands"))
  {
    Serial.print(currentMillis);
    Serial.print("    >>    Failed to get command: ");
    Serial.println(cmdData.errorReason());
    return;
  }

  if (!cmdData.dataAvailable())
  {
    Serial.print(currentMillis);
    Serial.println("    >>    No command available.");
    return;
  }

  Serial.print(currentMillis);
  Serial.print("    >>    Received JSON: ");
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

  if (!jsonData.success)
  {
    Serial.print(currentMillis);
    Serial.println("    >>    Failed to get command type.");
    return;
  }

  String commandType = jsonData.stringValue;
  Serial.print(currentMillis);
  Serial.print("    >>    Processing command: ");
  Serial.println(commandType);

  if (commandType == "TURN_ON_PUMP")
  {
    turnOnPump();
  }
  else if (commandType == "TURN_OFF_PUMP")
  {
    pumpOn = false;
    manualOverride = false;
    digitalWrite(RELAY2_PIN, LOW);
    Serial.print(currentMillis);
    Serial.println("    >>    Pump turned OFF by app command");
  }
  else if (commandType == "RESET")
  {
    Serial.print(currentMillis);
    Serial.println("    >>    System reset by app command");
    resetOn = true;
  }

  // Xoá command
  if (!Firebase.deleteNode(cmdData, commandPath))
  {
    Serial.print(currentMillis);
    Serial.print("    >>    Failed to delete command: ");
    Serial.println(cmdData.errorReason());
  }

  if (resetOn)
  {
    resetOn = false;
    ESP.restart();
  }
}
// ---------------- END CHECK COMMAND --------------------

// ---------------- CHECK CONFIG ---------------------
void checkConfig()
{
  unsigned long currentMillis = millis();

  if (!Firebase.getInt(cmdData, "/config/pumpDuration"))
  {
    Serial.print(currentMillis);
    Serial.print("    >>    Failed to get pumpDuration: ");
    Serial.println(cmdData.errorReason());
    return;
  }

  pumpDuration = cmdData.intData();
  Serial.print(currentMillis);
  Serial.print("    >>    Updated pumpDuration: ");
  Serial.println(pumpDuration);

  if (!Firebase.getInt(cmdData, "/config/autoPump"))
  {
    Serial.print(currentMillis);
    Serial.print("    >>    Failed to get Auto Pump: ");
    Serial.println(cmdData.errorReason());
    return;
  }

  autoPump = cmdData.intData() == 1 ? true : false;
  Serial.print(currentMillis);
  Serial.print("    >>    Auto Pump: ");
  if (autoPump == 0)
  {
    Serial.println("False");
  }
  else
  {
    Serial.println("True");
  }

  if (!Firebase.getInt(cmdData, "/config/heightWaterTank"))
  {
    Serial.print(currentMillis);
    Serial.print("    >>    Failed to get height water tank: ");
    Serial.println(cmdData.errorReason());
    return;
  }

  heightTankWater = cmdData.intData();
  Serial.print(currentMillis);
  Serial.print("    >>    Height Water Tank: ");
  Serial.println(heightTankWater);
}
// --------------------------- END CHECK CONFIG -----------------------------

// ---------------------------- TURN ON PUMP ---------------------------------
void turnOnPump()
{
  if (!pumpOn)
  {
    pumpOn = true;
    manualOverride = true;
    digitalWrite(RELAY2_PIN, HIGH);
    pumpStartTime = millis(); // Ghi lại thời gian bật bơm
    Serial.print(pumpStartTime);
    Serial.println("    >>    Pump turned ON");

    // Gửi thông báo lên Firebase, sau đó Cloud Function sẽ gửi thông báo về App thông qua FCM
    json.clear();
    unsigned long timestamp = timeClient.getEpochTime();
    json.set("timestamp", timestamp);
    json.set("desc", "Auto Pump");
    if (!Firebase.push(firebaseData, "/notification", json))
    {
      Serial.print(pumpStartTime);
      Serial.println("    >>    Failed to send data to Firebase /notification");
      Serial.print(pumpStartTime);
      Serial.println("    >>    REASON: " + firebaseData.errorReason());
      typeError = 6; // Lỗi kết nối Firebase
    } else if(typeError == 6) {
      typeError = 0; // Reset lỗi kết nối Firebase nếu gửi thành công
    }
  }
}

// ------------------------------ END TURN ON PUMP ----------------------------

// Hàm kiểm tra và tự tắt bơm sau thời gian chạy
void checkPump()
{
  if (pumpOn && millis() - pumpStartTime >= pumpDuration)
  {
    pumpOn = false;
    digitalWrite(RELAY2_PIN, LOW);
    Serial.print(millis());
    Serial.printf("    >>    Pump turned OFF automatically after %lu s\n", pumpDuration);
  }
}
// ------------------------------ END CHECK PUMP -----------------------------

// ------------------- Hàm gửi dữ liệu tới Firebase --------------------------
void sendDataToFirebase(float temperature, float humidity, float soilMoisture, float batteryLevel, float tankWaterLevel)
{
  // Tạo JSON chứa dữ liệu cảm biến
  json.clear();
  json.set("temperature", temperature);
  json.set("humidity", humidity);
  json.set("soilMoisture", soilMoisture);
  json.set("batteryLevel", batteryLevel);
  json.set("tankWaterLevel", tankWaterLevel);
  unsigned long timestamp = timeClient.getEpochTime();

  unsigned long currentMillis = millis();

  // Gửi lên Firebase
  int mod = timestamp % 1800;
  // 30 phút gửi 1 lần, để thống kê dài ngày
  if (mod < 5)
  {
    json.set("timestamp", timestamp - mod);
    if (!Firebase.push(firebaseData, "/sensor_data_30min", json))
    {
      Serial.print(currentMillis);
      Serial.println("    >>    Failed to send data to Firebase /sensor_data_30min");
      Serial.print(currentMillis);
      Serial.println("    >>    REASON: " + firebaseData.errorReason());
      typeError = 6; // Lỗi kết nối Firebase
    } else if(typeError == 6) {
      typeError = 0; // Reset lỗi kết nối Firebase nếu gửi thành công
    }
    lastUpdateData = currentMillis + 4000; // Cập nhật thời gian gửi dữ liệu, tránh gửi quá nhanh
  }
  // 3-4s gửi 1 lần, xem trực tiếp
  json.set("timestamp", timestamp);
  if (!Firebase.push(firebaseData, "/sensor_data", json))
  {
    Serial.print(currentMillis);
    Serial.println("    >>    Failed to send data to Firebase /sensor_data");
    Serial.print(currentMillis);
    Serial.println("    >>    REASON: " + firebaseData.errorReason());
    typeError = 6; // Lỗi kết nối Firebase
  } else if(typeError == 6) {
    typeError = 0; // Reset lỗi kết nối Firebase nếu gửi thành công
  }
}

float checkLevelWater()
{
  long duration;
  float distance;

  // Gửi xung trigger 10 micro giây
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  // Đo thời gian echo phản hồi
  duration = pulseIn(ECHO_PIN, HIGH);

  // Tính khoảng cách (đơn vị cm)
  distance = duration * 0.034 / 2;
  // Serial.print("Distance: ");
  // Serial.println(distance);

  float level = distance * 100 / heightTankWater;

  unsigned long currentMillis = millis();
  Serial.print(currentMillis);
  Serial.print("    >>    Level Tank Water: ");
  Serial.println(level);

  return level;
}

float checkLevelBattery()
{
  float busVoltage = ina219.getBusVoltage_V();               // Điện áp đo được (V)
  float shuntVoltage = ina219.getShuntVoltage_mV() / 1000.0; // mV -> V
  float loadVoltage = busVoltage + shuntVoltage;             // Tổng điện áp thực tế

  float percent = (loadVoltage - 9) / (12.6 - 9) * 100.0;
  percent = constrain(percent, 0, 100);
  unsigned long currentMillis = millis();
  Serial.print(currentMillis);
  Serial.print("    >>    Current Level Battery: ");
  Serial.print(percent);
  Serial.println(" %");

  if(percent <= 10 && !sendNotiLowPower) {
    // Gửi thông báo lên Firebase, sau đó Cloud Function sẽ gửi thông báo về App thông qua FCM
    json.clear();
    unsigned long timestamp = timeClient.getEpochTime();
    json.set("timestamp", timestamp);
    json.set("desc", "Low Power");
    if (!Firebase.push(firebaseData, "/notification", json))
    {
      Serial.print(pumpStartTime);
      Serial.println("    >>    Failed to send data to Firebase /notification");
      Serial.print(pumpStartTime);
      Serial.println("    >>    REASON: " + firebaseData.errorReason());
      typeError = 6; // Lỗi kết nối Firebase
    } else if(typeError == 6) {
      typeError = 0; // Reset lỗi kết nối Firebase nếu gửi thành công
    }
    sendNotiLowPower = true; // Chỉ thông báo 1 lần
  }

  if(percent >= 15) {
    sendNotiLowPower = false;
  }
  return percent;
}

void ledBug(int type)
{
  unsigned long currentMillis = millis();
  unsigned long interval = 0;
  bool useDelay = false;
  bool alwaysOn = false;

  switch (type)
  {
  case 1: interval = 500; break;            // DHT
  case 2: interval = 1000; break;           // Soil Moisture
  case 3: interval = 2000; useDelay = true; break;  // INA219
  case 4: interval = 4000; useDelay = true; break;  // HC-SR04
  case 5: alwaysOn = true; break;           // Connect WiFi
  case 6: interval = 250; useDelay = true; break;   // Connect Firebase
  default: break;
  }

  if (alwaysOn)
  {
    lastLedCheck = currentMillis;
    digitalWrite(LED_BUG, HIGH);
    return;
  }

  if (interval > 0 && currentMillis - lastLedCheck >= interval)
  {
    lastLedCheck = currentMillis;
    digitalWrite(LED_BUG, HIGH);
    if (useDelay) delay(50);
  }

  digitalWrite(LED_BUG, LOW);
}
