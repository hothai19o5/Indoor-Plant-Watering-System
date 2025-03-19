# ESP8266
```cpp
#include <DHT.h>
#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <NTPClient.h>
#include <ArduinoJson.h>
#include <FirebaseESP8266.h>  // Thư viện Firebase

#define DHTPIN D2         // Chân kết nối DHT22
#define DHTTYPE DHT22     // Định dạng cảm biến DHT22
#define SOIL_PIN A0       // Chân đọc cảm biến độ ẩm đất
#define RELAY1_PIN D5     // Chân điều khiển relay 1 (LED1)
#define RELAY2_PIN D6     // Chân điều khiển relay 2 (MÁY BƠM)
#define LED_DEBUG_PIN D7  // Chân điều khiển led debug

DHT dht(DHTPIN, DHTTYPE);   // Khởi tạo DHT22

WiFiUDP ntpUDP;   // Khởi tạo UDP

NTPClient timeClient(ntpUDP, "pool.ntp.org", 7 * 3600, 60000);  // Khởi tạo NTP Client (GMT+7 cho Việt Nam)

const char *ssid      = "B10509_2.4G";            // Tên WiFi SSID
const char *password  = "509509509";              // Mật khẩu wifi

// Cấu hình Firebase
#define FIREBASE_HOST "projectii-fabc6-default-rtdb.asia-southeast1.firebasedatabase.app"  // URL dự án Firebase
#define FIREBASE_AUTH "2alu2RzqECbig8ebDKXUMkgCNKdBR8CDKIYA7JXB"          // Database Secret

FirebaseData firebaseData;
FirebaseData cmdData;  // Đối tượng FirebaseData riêng cho commands
FirebaseJson json;
FirebaseConfig firebaseConfig;
FirebaseAuth firebaseAuth;

unsigned long lastCommandCheck = 0;  // Thời điểm kiểm tra lệnh cuối cùng
const long commandCheckInterval = 1000;  // Kiểm tra lệnh mỗi 1 giây

// Hàm kết nối WiFi
void setup_wifi() {
  delay(1000);
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);
  WiFi.begin(ssid, password);
  int countConnect = 0;   // Biến đếm số lần kết nối wifi thất bại
  while (WiFi.status() != WL_CONNECTED) {
    countConnect ++;
    delay(1000);
    Serial.print(".");
    if(countConnect == 60) {
      ESP.restart();  // Khởi động lại ESP nếu kết nối thất bại quá nhiều lần
    }
  }
  randomSeed(micros());
  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
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
  
  setup_wifi();         // Kết nối tới wifi

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
    setup_wifi();
  }

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
  if (isnan(temperature) || isnan(humidity)) {
    Serial.println("DHT22 ERROR!!!");
    digitalWrite(LED_DEBUG_PIN, HIGH);      // Bật led debug
    delay(1000);
    return;
  }
  if (soilMoisture <= 20 || soilMoisture >= 1023) {
    Serial.println("V1.2 ERROR!!!");
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
  if((hour == 6 || hour == 18) && soilMoisture >= 800 && temperature <= 40) {
    digitalWrite(RELAY2_PIN, HIGH);     // Bật máy bơm
    Serial.println("Watering...");
    delay(10000);                       // Tưới trong 10 giây
    Serial.println("Finished Watering...");
    digitalWrite(RELAY2_PIN, LOW);     // Tắt máy bơm
  }

  // Gửi dữ liệu lên Firebase
  sendDataToFirebase(temperature, humidity, percentSoilMoisture); //Gửi dữ liệu lên Firebase

  Serial.println("-----------------------------");
  delay(2000); // Đọc dữ liệu mỗi 2 giây
}

// Hàm kiểm tra và xử lý lệnh từ Firebase
void checkCommands() {
  if (Firebase.getString(cmdData, "/commands")) {
    if (cmdData.dataAvailable()) {
      // Lấy tất cả commands dưới dạng JSON
      FirebaseJson *json = cmdData.jsonObjectPtr();
      
      if (json != NULL) {
        size_t len = json->iteratorBegin();
        String lastKey = "";
        String lastCmd = "";
        
        // Tìm lệnh mới nhất (lệnh cuối cùng)
        for (size_t i = 0; i < len; i++) {
          FirebaseJson::IteratorValue value = json->valueAt(i);
          lastKey = value.key.c_str();
          lastCmd = value.value.c_str();
        }
        json->iteratorEnd();
        
        // Nếu có lệnh mới
        if (lastCmd.length() > 0) {
          Serial.print("Command received: ");
          Serial.println(lastCmd);
          
          // Xử lý lệnh
          if (lastCmd == "\"TURN_ON_PUMP\"") {
            digitalWrite(RELAY2_PIN, HIGH);     // Bật máy bơm
            Serial.println("Pump turned ON by app command");
            delay(10000);
            digitalWrite(RELAY2_PIN, LOW);
          } 
          else if (lastCmd == "\"TURN_OFF_PUMP\"") {
            digitalWrite(RELAY2_PIN, LOW);      // Tắt máy bơm
            Serial.println("Pump turned OFF by app command");
          }
          else if (lastCmd == "\"RESET\"") {
            ESP.restart();
            Serial.println("System reset by app command");
          }
          
          // Đánh dấu lệnh đã xử lý bằng cách xóa node
          if (lastKey.length() > 0) {
            String path = "/commands/" + lastKey;
            
            // Tạo một object lệnh mới với trạng thái đã xử lý
            FirebaseJson respJson;
            respJson.add("command", lastCmd);
            respJson.add("processed", true);
            respJson.add("timestamp", timeClient.getEpochTime());
            
            // Cập nhật lệnh với trạng thái đã xử lý
            Firebase.updateNode(cmdData, path, respJson);
          }
        }
      }
    }
  } else {
    Serial.println("Failed to get command: " + cmdData.errorReason());
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
