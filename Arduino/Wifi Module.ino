#include <ESP8266HTTPClient.h>
#include <WiFiClientSecureBearSSL.h>
#include <ArduinoJson.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>

const char* ssid = "HUAWEI-E5373-F145";
const char* password = "td3egdb0";
// const char* ssid = "ROOM8";
// const char* password = "room82023";
// const char* ssid = "HOME WIFI";
// const char* password = "LAPIZAR3459";
const char* mqtt_server = "broker.emqx.io";
char macStr[18];
String ssl = "8B:8E:8B:6C:B9:94:FC:33:E9:3E:F9:71:7C:9C:CF:AF:10:F1:DA:7A";
char token[256];
const size_t JSON_BUFFER_SIZE = JSON_OBJECT_SIZE(6) + 100; // Adjust size as needed
DynamicJsonDocument jsonDoc(JSON_BUFFER_SIZE);
String input = "";

WiFiClient espClient;
PubSubClient client(espClient);

unsigned long lastMsg = 0;
#define MSG_BUFFER_SIZE (100)
char msg[MSG_BUFFER_SIZE];
int value = 0;

unsigned long lastTheftDetailTime = 0; // Variable to track the last time theft details were sent
const unsigned long theftDetailInterval = 15000; // 15 seconds
unsigned long lastTheftFalseTime = 0; // Variable to track the last time theft was false
bool alertActive = false; // Initial alertActive status
unsigned long lastAlertTime = 0;
unsigned long lastSendTime = 0;  
unsigned long lastLevel1to3Time = 0;
const unsigned long level1to3Interval = 12000; // Interval set to 12 seconds



unsigned long lastTheftDetailsTime = 0; // Variable to store the last time sendtheftdetails was called
const unsigned long theftDetailsInterval = 20000; 

unsigned long lastInactiveExecutionTime = 0; // Track the last execution time of inactive block
const unsigned long inactiveInterval = 13000; // 13 seconds interval
bool on = false;

unsigned long lastExecutionTime = 0; 
void setup_wifi() {
    Serial.begin(115200);

    // Connect to WiFi
    WiFi.begin(ssid, password);
    while (WiFi.status() != WL_CONNECTED) {
        delay(1000);
        Serial.println("Connecting to WiFi...");
    }
    Serial.println("Connected to WiFi");

    uint8_t mac[6];
    WiFi.macAddress(mac);
    sprintf(macStr, "%02X:%02X:%02X:%02X:%02X:%02X", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);

    bool registrationSuccess = false;
    while (!registrationSuccess) {
        // Attempt to register hardware with the server
        registrationSuccess = registerHardware(macStr);

        if (registrationSuccess) {
            Serial.println("Hardware registered or already registered.");
        } else {
            Serial.println("Registration failed. Check if hardware is already registered or try again.");
            delay(5000); // Delay before retrying (adjust as needed)
        }
    }
}


void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Create a random client ID
    String clientId = "ESP8266Client-";
    clientId += String(random(0xffff), HEX);
    // Attempt to connect
    if (client.connect(clientId.c_str())) {
      Serial.println("connected");
      // Once connected, publish an announcement...
      client.publish("outTopic", "hello world");
      // ... and resubscribe
      client.subscribe("inTopic");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}


void setup() {

    setup_wifi(); // Connect to WiFi

    client.setServer(mqtt_server, 1883);
  
}


  void loop() {
    if (!client.connected()) {
        reconnect();
    }
    client.loop();

    unsigned long currentMillis = millis(); // Capture the current time
    static unsigned long lastLevel4ExecutionTime = 0; // Timer for Level 4
    static unsigned long lastExecutionTime = 0;       // Timer for other actions

    if (Serial.available()) {
        input = Serial.readStringUntil('\n'); // Read the data until newline character

        // Debugging: Print the received input
        Serial.print("Received input: ");
        Serial.println(input);

        // Parse the input data
        int comma1 = input.indexOf(',');
        int comma2 = input.indexOf(',', comma1 + 1);
        int comma3 = input.indexOf(',', comma2 + 1);
        int comma4 = input.indexOf(',', comma3 + 1);
        int comma5 = input.indexOf(',', comma4 + 1);
        int comma6 = input.indexOf(',', comma5 + 1);

        if (comma1 != -1 && comma2 != -1 && comma3 != -1) {
            float latitude = input.substring(0, comma1).toFloat();
            float longitude = input.substring(comma1 + 1, comma2).toFloat();
            String level = input.substring(comma2 + 1, comma3);
            String description = input.substring(comma3 + 1, comma4);
            bool identifierpin = input.substring(comma4 + 1, comma5).equalsIgnoreCase("true");
            bool theft = input.substring(comma5 + 1, comma6).equalsIgnoreCase("true");
            bool alert = input.substring(comma6 + 1).equalsIgnoreCase("true");

            if (level == "Level 4") {
                    lastAlertTime = millis();
                    jsonDoc["latitude"] = latitude;
                    jsonDoc["longitude"] = longitude;
                    jsonDoc["Macaddress"] = macStr;
                    publishMessage("gt/t", jsonDoc);
                    alertActive = true; 
                  
                    if (currentMillis - lastTheftDetailsTime >= 25000) {
                        sendtheftdetails(latitude, longitude, level, description);
                            getDataFromServer(macStr);
                        lastTheftDetailsTime = currentMillis; // Update the timestamp
                    }

                   
            }

            if (level == "Level 1" || level == "Level 2" || level == "Level 3") {
                if (currentMillis - lastLevel1to3Time >= level1to3Interval) {
                    minoralert(latitude, longitude, description, level);
                    lastLevel1to3Time = currentMillis; // Update the last send time
                }
            }

            if (level == "pin" || identifierpin) {
                Serial.println("ASDSAd");
                updatepinstatus(macStr, false, latitude, longitude);
            }

            if (level != "Level 4" && currentMillis - lastTheftFalseTime > 15000) {
                alertActive = false;
                lastTheftFalseTime = currentMillis;
            }
        }
    }

    // Execute these functions only if alertActive is false or 15 seconds have passed
    else if (!alertActive ) {
      
        getpinlocation(macStr);
        getDataFromServer(macStr);
        // getNumberFromServer(macStr);
        getpinstatusFromServer(macStr);

        lastExecutionTime = currentMillis; // Update the last execution time
    }
}




void publishMessage(const char* topic, const String& message) {
    // Convert String to char array
    char msgBuffer[message.length() + 1];
    message.toCharArray(msgBuffer, sizeof(msgBuffer));
    
    // Publish message
    if (client.publish(topic, msgBuffer)) {
        Serial.println("Message published successfully.");
    } else {
        Serial.println("Message publish failed.");
    }
}
void publishMessage(const char* topic, const DynamicJsonDocument& doc) {
    // Define a buffer size that is large enough to hold the serialized JSON.
    // You may need to adjust this size based on your JSON complexity.
    const size_t bufferSize = 1024; // Adjust as necessary
    char jsonBuffer[bufferSize];
    
    // Serialize the JSON document to the buffer
    size_t n = serializeJson(doc, jsonBuffer, bufferSize);

    // Ensure that the JSON was serialized successfully
    if (n == 0) {
        Serial.println("Failed to serialize JSON");
        return;
    }
Serial.println("send na ");
    // Publish the serialized JSON
    client.publish(topic, jsonBuffer);
}








void minoralert(float latitude, float longitude, String description, String level) {
    

    // Set insecure connection (no SSL verification)
     WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection

    HTTPClient http;
    String serverUrl = "https://hardware-99dt.onrender.com/api/send_alert"; // Initial URL
    String payload;

    StaticJsonDocument<256> jsonDoc; // Adjust size based on your needs
    jsonDoc["latitude"] = latitude;
    jsonDoc["longitude"] = longitude;
    jsonDoc["description"] = description;
    jsonDoc["level"] = level;
    jsonDoc["token"] = token;

    String jsonStr;
    serializeJson(jsonDoc, jsonStr);

    unsigned long startTime = millis(); // Start timer

    if (http.begin(client, serverUrl)) { // Use client for the secure client
        http.addHeader("Content-Type", "application/json");
        http.addHeader("Authorization", "Bearer " + String(token)); // Add Authorization header

        int httpResponseCode = http.POST(jsonStr);

        if (httpResponseCode > 0) {
            Serial.printf("HTTP Response code: %d\n", httpResponseCode);
            if (httpResponseCode == HTTP_CODE_FOUND || httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY) {
                serverUrl = http.header("Location"); // Handle redirect
                Serial.println("Redirected to: " + serverUrl);
            } else if (httpResponseCode >= 200 && httpResponseCode < 300) {
                payload = http.getString(); // Get the response payload
                Serial.println("Received payload: " + payload);
            } else if (httpResponseCode == 404) {
                Serial.println("Error 404: Resource not found. Check the endpoint URL.");
            }
        } else {
            Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
        }

        http.end(); // End HTTP connection
    } else {
        Serial.println("Failed to begin HTTP connection");
    }

    unsigned long a = millis() - startTime;
    Serial.printf("Time taken to send minoralert: %lu ms\n", a);
}

void getDataFromServer(const char* uniqueId) {
    
     WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection

    HTTPClient http;
    String serverUrl = "https://hardware-99dt.onrender.com/api/hardwarestatus";

    if (!http.begin(client, serverUrl)) {
        Serial.println("Failed to begin HTTP connection");
        return;
    }

    http.addHeader("Authorization", "Bearer " + String(token));
    http.addHeader("Content-Type", "application/json");

    StaticJsonDocument<200> jsonDoc;
    jsonDoc["token"] = token;
    String jsonStr;
    serializeJson(jsonDoc, jsonStr);

    int httpResponseCode = http.POST(jsonStr);

    if (httpResponseCode > 0) {
        if (httpResponseCode == HTTP_CODE_OK) {
            String payload = http.getString();

            StaticJsonDocument<200> doc;
            DeserializationError error = deserializeJson(doc, payload);

            if (error) {
                Serial.print(F("deserializeJson() failed: "));
                Serial.println(error.f_str());
                http.end();
                return;
            }

            bool status = doc["status"].as<bool>();
              on = status;
             Serial.println(status);
        } else {
            Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
        }
    } else {
        Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
    }

    http.end();
}


//check the pinlocation whether its true or false
void getpinstatusFromServer(const char* uniqueId) {
    
     WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection

    HTTPClient http;
    String serverUrl = "https://hardware-99dt.onrender.com/api/pinstatus";

    if (!http.begin(client, serverUrl)) {
        Serial.println("Failed to begin HTTP connection");
        return;
    }

    http.addHeader("Authorization", "Bearer " + String(token));
    http.addHeader("Content-Type", "application/json");

    StaticJsonDocument<200> jsonDoc;
    jsonDoc["token"] = token;
    String jsonStr;
    serializeJson(jsonDoc, jsonStr);

    int httpResponseCode = http.POST(jsonStr);

    if (httpResponseCode > 0) {
        if (httpResponseCode == HTTP_CODE_OK) {
            String payload = http.getString();

            StaticJsonDocument<200> doc;
            DeserializationError error = deserializeJson(doc, payload);

            if (error) {
                Serial.print(F("deserializeJson() failed: "));
                Serial.println(error.f_str());
                http.end();
                return;
            }

            bool pinlocation = doc["status"].as<bool>();
            Serial.println(pinlocation ? "true" : "false");

        } else {
            Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
        }
    } else {
        Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
    }

    http.end();
}



bool registerHardware(const char* macAddress) {
    
     WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection

    HTTPClient http;
    String serverUrl = "https://hardware-99dt.onrender.com/api/hardwareregister";
    String payload;

    StaticJsonDocument<200> jsonDoc;
    jsonDoc["uniqueId"] = macAddress;

    String jsonStr;
    serializeJson(jsonDoc, jsonStr);

    int httpResponseCode = 0;
    if (http.begin(client, serverUrl)) {
        http.addHeader("Content-Type", "application/json");
        httpResponseCode = http.POST(jsonStr);

        if (httpResponseCode > 0) {
            if (httpResponseCode >= 200 && httpResponseCode < 300) {
                String response = http.getString();

                DynamicJsonDocument jsonRes(200);
                deserializeJson(jsonRes, response);
                const char* newToken = jsonRes["token"];
                strncpy(token, newToken, sizeof(token));

                return true;
            } else if (httpResponseCode == 400) {
                String response = http.getString();
                DynamicJsonDocument jsonRes(200);
                deserializeJson(jsonRes, response);
                const char* newToken = jsonRes["token"];
                strncpy(token, newToken, sizeof(token));
                return true;
            } else {
                Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
            }
        } else {
            Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
        }
        http.end();
    } else {
        Serial.println("Failed to begin HTTP connection");
    }

    return false;
}
void getNumberFromServer(const char* macAddress) {
  

   WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection

  HTTPClient http;
  String serverUrl = "https://hardware-99dt.onrender.com/api/usernumber";
  String payload;

  StaticJsonDocument<200> jsonDoc;
  jsonDoc["token"] = token;

  String jsonStr;
  serializeJson(jsonDoc, jsonStr);

  int httpResponseCode = 0;
  if (http.begin(client, serverUrl)) {
    http.addHeader("Content-Type", "application/json");
    http.addHeader("Authorization", "Bearer " + String(token));

    httpResponseCode = http.POST(jsonStr);

    if (httpResponseCode > 0) {
      if (httpResponseCode == HTTP_CODE_OK) {
        String responsePayload = http.getString();
        DynamicJsonDocument doc(200);
        DeserializationError error = deserializeJson(doc, responsePayload);

        if (!error) {
          String cellphonenumber = doc["cellphonenumber"].as<String>();
          Serial.println(cellphonenumber);
        } else {
          Serial.print(F("deserializeJson() failed: "));
          Serial.println(error.c_str());
        }
      } else {
        Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
      }
    } else {
      Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
    }
    http.end();
  } else {
    Serial.println("Failed to begin HTTP connection");
  }
}




void updatepinstatus(const char* uniqueId, bool pinstatus, float latitude, float longitude) {
  
   WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection
  HTTPClient http;
  String serverUrl = "https://hardware-99dt.onrender.com/api/pinthislocation";
  String payload;

  StaticJsonDocument<500> jsonDoc;
  jsonDoc["currentlatitude"] = latitude;
  jsonDoc["currentlongitude"] = longitude;
  jsonDoc["pinlocation"] = pinstatus;
  jsonDoc["statusPin"] = true;
  jsonDoc["status"] = true;
  jsonDoc["token"] = token;

  String jsonStr;
  serializeJson(jsonDoc, jsonStr);

  int httpResponseCode = 0;
  do {
    if (http.begin(client, serverUrl)) {
      http.addHeader("Content-Type", "application/json");
      http.addHeader("Authorization", "Bearer " + String(token));

      httpResponseCode = http.POST(jsonStr);

      if (httpResponseCode > 0) {
        Serial.printf("HTTP Response code: %d\n", httpResponseCode);
        if (httpResponseCode == HTTP_CODE_FOUND || httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY) {
          serverUrl = http.header("Location");
          Serial.println("Redirected to: " + serverUrl);
        } else if (httpResponseCode >= 200 && httpResponseCode < 300) {
          payload = http.getString();
          Serial.println("Received payload: " + payload);
          break;
        }
      } else {
        Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
        break;
      }
    } else {
      Serial.println("Failed to begin HTTP connection");
      break;
    }
    http.end();
  } while (httpResponseCode == HTTP_CODE_FOUND || httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY);

  if (httpResponseCode < 200 || httpResponseCode >= 300) {
    Serial.println("Failed to send data after handling redirects.");
  }
}



void getpinlocation(const char* uniqueId) {
  
   WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection
  const char* serverUrl = "https://hardware-99dt.onrender.com/api/checkpinlocation";

  StaticJsonDocument<200> jsonDoc;
  jsonDoc["token"] = token;
  String jsonStr;
  serializeJson(jsonDoc, jsonStr);

  HTTPClient http;
  http.begin(client, serverUrl);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + String(token));

  int httpResponseCode = http.POST(jsonStr);

  if (httpResponseCode > 0) {
    if (httpResponseCode == HTTP_CODE_OK) {
      DynamicJsonDocument jsonDoc(200);
      deserializeJson(jsonDoc, http.getString());
      double latitude = jsonDoc["latitude"].as<double>();
      double longitude = jsonDoc["longitude"].as<double>();
      Serial.print("Latitude:");
      Serial.println(latitude, 6);
      Serial.print("Longitude:");
      Serial.println(longitude, 6);
    }
  } else {
    Serial.print("Error in HTTP POST request: ");
    Serial.println(http.errorToString(httpResponseCode).c_str());
  }

  http.end();
}


void sendtheftdetails( float latitude, float longitude, String level, String description) {
   
   WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection
  HTTPClient http;
  String serverUrl = "https://hardware-99dt.onrender.com/api/sendtheftdetails";
  String payload;
  StaticJsonDocument<500> jsonDoc; // Adjust size based on your needs
  jsonDoc["currentlatitude"] = latitude;
  jsonDoc["currentlongitude"] = longitude;
  jsonDoc["description"] = description;
  jsonDoc["level"] = level;
  jsonDoc["token"] = token;
  String jsonStr;
  serializeJson(jsonDoc, jsonStr);

  int httpResponseCode = 0;
  do {
    if (http.begin(client, serverUrl)) { // Begin connection to the specified URL
      http.addHeader("Content-Type", "application/json");
      // Add Authorization header
      http.addHeader("Authorization", "Bearer " + String(token));

      httpResponseCode = http.POST(jsonStr);

      if (httpResponseCode > 0) {
        Serial.printf("HTTP Response code: %d\n", httpResponseCode);
        if (httpResponseCode == HTTP_CODE_FOUND || httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY) {
          serverUrl = http.header("Location"); // Handle redirect
          Serial.println("Redirected to: " + serverUrl);
        } else if (httpResponseCode >= 200 && httpResponseCode < 300) {
          payload = http.getString(); // Get the response payload
          Serial.println("Received payload: " + payload);
          break; // Exit loop on successful data exchange
        }
      } else {
        Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
        break; // Exit loop on error
      }
    } else {
      Serial.println("Failed to begin HTTP connection");
      break; // Exit loop if connection couldn't be begun
    }
    http.end(); // End HTTP connection before potentially looping
  } while (httpResponseCode == HTTP_CODE_FOUND || httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY);

  if (httpResponseCode < 200 || httpResponseCode >= 300) {
    Serial.println("Failed to send data after handling redirects.");
  }
}


void sendtheftalert( float latitude, float longitude){
   
   WiFiClientSecure client;
    client.setInsecure(); // Use insecure connection
  HTTPClient http;
  String serverUrl = "https://hardware-99dt.onrender.com/api/currentvehiclelocation";
  String payload;
  StaticJsonDocument<500> jsonDoc;
  jsonDoc["token"] = token;
  jsonDoc["currentlatitude"] = latitude;
  jsonDoc["currentlongitude"] = longitude;
  String jsonStr;
  serializeJson(jsonDoc, jsonStr);
  
  int httpResponseCode = 0;
  do {
    if (http.begin(client, serverUrl)) {
      http.addHeader("Content-Type", "application/json");
   
      http.addHeader("Authorization", "Bearer " + String(token));

      httpResponseCode = http.POST(jsonStr);

      if (httpResponseCode > 0) {
        Serial.printf("HTTP Response code: %d\n", httpResponseCode);
        if (httpResponseCode == HTTP_CODE_FOUND || httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY) {
          serverUrl = http.header("Location"); // Handle redirect
          Serial.println("Redirected to: " + serverUrl);
        } else if (httpResponseCode >= 200 && httpResponseCode < 300) {
          payload = http.getString(); // Get the response payload
          Serial.println("Received payload: " + payload);
          break;
        }
      } else {
        Serial.printf("HTTP Request failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
        break; // Exit loop on error
      }
    } else {
      Serial.println("Failed to begin HTTP connection");
      break; // Exit loop if connection couldn't be begun
    }
    http.end(); // End HTTP connection before potentially looping
  } while (httpResponseCode == HTTP_CODE_FOUND || httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY);

  if (httpResponseCode < 200 || httpResponseCode >= 300) {
    Serial.println("Failed to send data after handling redirects.");
  }
  
}
