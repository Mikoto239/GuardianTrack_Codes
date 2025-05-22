#include <TinyGPS++.h>

int vibratesensor = 3;
String phoneNumber;
TinyGPSPlus gps;

bool sendSMSAllowed = true;
bool sendserverallowed;
bool sendlocation;
unsigned long lastSMSTime = 0;
unsigned long smsInterval = 1000;
bool locationSMSSent = false;
unsigned long lastLevel1SMSTime = 0;




const int VIBRATION_THRESHOLD1 = 10;
const int VIBRATION_THRESHOLD2 = 20000;
const int VIBRATION_THRESHOLD3 = 30000;
unsigned long vibrationStart = 0;
const unsigned long VIBRATION_TIMEOUT = 5000;
unsigned long lastVibrationTime = 0;
const float GEO_FENCE_RADIUS_METERS = 10;
float clatitude = 0.0;
float clongitude = 0.0;
float pinlatitude = 0.0;
float pinlongitude = 0.0;
bool theft = false;
int count = 0;


void setup() {
    Serial.begin(115200);  // USB serial
    Serial2.begin(9600);   // GPS serial
    Serial3.begin(115200); // NodeMCU serial
    pinMode(vibratesensor, INPUT);
    Serial.println("System Initializing...");
}


void loop() {
  while (Serial2.available() > 0) {
        if (gps.encode(Serial2.read())) {
            if (gps.location.isValid()) {
              
                clatitude = gps.location.lat();
                clongitude = gps.location.lng();
               
                vibration();
            }
        }
    }
  //  vibration();
              transactions();
  
}


double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
  const double R = 6371000; // Earth radius in meters
  double dLat = degreesToRadians(lat2 - lat1);
  double dLon = degreesToRadians(lon2 - lon1);
  double a = sin(dLat / 2) * sin(dLat / 2) +
             cos(degreesToRadians(lat1)) * cos(degreesToRadians(lat2)) *
             sin(dLon / 2) * sin(dLon / 2);
  double c = 2 * atan2(sqrt(a), sqrt(1 - a));
  double distance = R * c;
  return distance;
}

// Convert degrees to radians
double degreesToRadians(double degrees) {
  return degrees * (PI / 180);
}

void transactions() {
    while (Serial3.available()) {
        String input = Serial3.readStringUntil('\n');
        input.trim();

        // Check for phone number format
        if (looksLikePhoneNumber(input)) {
            phoneNumber = input; // Assign the phone number
        } else if (input.equals("1")) {
            sendserverallowed = true;
            Serial.print("sendserverallowed: ");
            Serial.println(sendserverallowed);
        } else if (input.equals("0")) {
            sendserverallowed = false;
            pinlatitude = 0;
            pinlongitude = 0;
        } else if (input.equalsIgnoreCase("true")) {
            sendlocation = true;
            printGPSData("Current Location", "");
        } else if (input.equalsIgnoreCase("false")) {
            sendlocation = false;
        } else if (input.startsWith("Latitude:")) {
            // Extract and convert latitude
            int latIndex = input.indexOf(':') + 1;
            String latStr = input.substring(latIndex);
            pinlatitude = latStr.toFloat();
            Serial.print("Parsed Latitude: ");
            Serial.println(pinlatitude, 6); // Print with 6 decimal places
        } else if (input.startsWith("Longitude:")) {
            // Extract and convert longitude
            int longIndex = input.indexOf(':') + 1;
            String longStr = input.substring(longIndex);
            pinlongitude = longStr.toFloat();
            Serial.print("Parsed Longitude: ");
            Serial.println(pinlongitude, 6); // Print with 6 decimal places
        } else {
            Serial.println("Unhandled input: " + input);
        }
    }
}

void vibration() {
    long measurement1 = vibrationMeasure(vibratesensor);
      //  long measurement1 = 100;
    if (sendlocation) {
        printGPSData("pin", "");
    }

    if (measurement1 > VIBRATION_THRESHOLD1) {
       
        if (vibrationStart == 0) {
            vibrationStart = millis();
        }

     
        unsigned long vibrationDuration = millis() - vibrationStart;
    
        double distance = calculateDistance(clatitude, clongitude, pinlatitude, pinlongitude);
        Serial.println(clatitude);
        Serial.println(clongitude);
        Serial.println(pinlatitude);
        Serial.println(pinlongitude);
         Serial.println(distance);

      
        if (sendserverallowed) {
            if (vibrationDuration >= 10 && clatitude != 0 && clongitude != 0 && pinlatitude != clatitude && pinlongitude != clongitude &&
                pinlatitude != 0 && pinlongitude != 0 && distance > GEO_FENCE_RADIUS_METERS) {
                Serial.println("Level 4");
                String message = "Critical! Potential theft detected! Your vehicle shows significant vibrations and is moving from its original location.";
                printGPSData("Level 4", message);
            } else {
                if (vibrationDuration >= 5 && vibrationDuration <= VIBRATION_THRESHOLD2) {
                    Serial.println("Level 1");
                    // String message = "Notice! Minor vibration detected on your vehicle without any location change. \n Vibrate duration: " + String(vibrationDuration / 1000) + " secs.";
                     String message = "Warning! Minor vibration detected on your vehicle without any location change.";
                    printGPSData("Level 1", message);
                } else if (vibrationDuration > VIBRATION_THRESHOLD2 && vibrationDuration <= VIBRATION_THRESHOLD3) {
                    Serial.println("Level 2");
                    // String message = "Alert! Persistent activity detected on your vehicle without movement. \n Vibrate duration: " + String(vibrationDuration / 1000) + " secs.";
                    String message = "Minor! Persistent activity detected on your vehicle without movement.";
                    printGPSData("Level 2", message);
                } else if (vibrationDuration > VIBRATION_THRESHOLD3) {
                    Serial.println("Level 3");
                    // String message = "Warning! Continuous vibration activity detected on your vehicle without relocation.\n Vibrate duration: " + String(vibrationDuration / 1000) + " secs.";
                     String message = "Major! Continuous vibration activity detected on your vehicle without relocation.";
                    printGPSData("Level 3", message);
                }
            }
        }

        lastVibrationTime = millis(); 
    } else {
       
        if (millis() - lastVibrationTime >= VIBRATION_TIMEOUT) {
            vibrationStart = 0;
            theft = false;
        }
    }
}




bool isNumeric(String str) {
    for (char c : str) {
        if (!isDigit(c) && c != '+' && c != '-') return false;
    }
    return true;
}

bool looksLikePhoneNumber(String str) {
    str.trim();
    if (str.length() < 7 || str.length() > 15) return false;
    return isNumeric(str);
}

long vibrationMeasure(int pin) {
    return pulseIn(pin, HIGH); 
}

void printGPSData(String level, String label) {
    if (clatitude != 0 && clongitude != 0) {
        if (sendserverallowed) {
            if (sendSMSAllowed && sendserverallowed) {
//                sendSMS(clatitude, clongitude, label, level);
                sendtoserver(clatitude, clongitude, label, level);
            } else if (sendserverallowed) {
                sendtoserver(clatitude, clongitude, label, level);
            } else if (theft) {
                sendtoserver(clatitude, clongitude, label, level);
            }
        } 
        else if (sendlocation) {
            sendtoserver(clatitude, clongitude, "pin", "");
            sendlocation = false;
        } else if (sendSMSAllowed) {
//            sendSMS(clatitude, clongitude, label, level);
        }
    }
}



void sendtoserver(float latitude, float longitude, String duration, String level) {
    char dataToSend[200]; // Increase the buffer size if needed
    bool locationSent = false;

    String latStr = String(latitude, 6);
    String lonStr = String(longitude, 6);

    if (sendlocation) {
        snprintf(dataToSend, sizeof(dataToSend), "%s,%s,%s,%s,true,false,false", latStr.c_str(), lonStr.c_str(), level.c_str(), duration.c_str());
        sendlocation = false;
        locationSent = true;
    } else if (theft) {
        snprintf(dataToSend, sizeof(dataToSend), "%s,%s,%s,%s,false,false,true", latStr.c_str(), lonStr.c_str(), level.c_str(), duration.c_str());
        locationSent = true;
    } else if (sendserverallowed) {
        snprintf(dataToSend, sizeof(dataToSend), "%s,%s,%s,%s,false,true,false", latStr.c_str(), lonStr.c_str(), level.c_str(), duration.c_str());
        locationSent = true;
    }

    // if (!sendserverallowed) {
       
    //       snprintf(dataToSend, sizeof(dataToSend), "%s,%s,%s,%s,false,true,false", latStr.c_str(), lonStr.c_str(), level.c_str(), duration.c_str());
      
    // }

    // Send to Serial3
    Serial3.println(dataToSend);

    // Clear the dataToSend array
    dataToSend[0] = '\0';  // Or use memset(dataToSend, 0, sizeof(dataToSend));

    // Debugging: Confirm the data was sent
    Serial.println("Data sent to server: " + String(dataToSend)); // This will print an empty string after clearing.
}

