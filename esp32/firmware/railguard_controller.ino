/* RailGuard AI - ESP32 Wi-Fi controller for four continuous-rotation MG995 servos. */
#include <Arduino.h>
#include <ESP32Servo.h>
#include <WiFi.h>
#include <ArduinoOTA.h>

const char* WIFI_SSID = "Nambakaserikalam";
const char* WIFI_PASSWORD = "Sreyas2524";
const char* OTA_HOSTNAME = "RAILguardAI_ESP32";
const char* OTA_PASSWORD = "RAILguardAI";
WiFiServer server(80);

const int LF_PWM = 17;
const int LR_PWM = 21;
const int RF_PWM = 19;
const int RR_PWM = 16;
const int SERVO_MIN_US = 1000;
const int SERVO_MAX_US = 2000;
const int MAX_OFFSET_US = 250;
const unsigned long COMMAND_TIMEOUT_MS = 1000;
const float VELOCITY_DEADZONE = 0.03f;

Servo servoLF, servoLR, servoRF, servoRR;
int DIR_LF = 1, DIR_LR = 1, DIR_RF = -1, DIR_RR = -1;
bool emergencyStop = false;
unsigned long lastCommandTime = 0;

int velocityToPulse(float velocity, int direction) {
  velocity = constrain(velocity, -1.0f, 1.0f) * direction;
  if (fabs(velocity) < VELOCITY_DEADZONE) return 1500;
  return constrain(1500 + (int)(velocity * MAX_OFFSET_US), SERVO_MIN_US, SERVO_MAX_US);
}

void writeWheels(float lf, float lr, float rf, float rr) {
  servoLF.writeMicroseconds(velocityToPulse(lf, DIR_LF));
  servoLR.writeMicroseconds(velocityToPulse(lr, DIR_LR));
  servoRF.writeMicroseconds(velocityToPulse(rf, DIR_RF));
  servoRR.writeMicroseconds(velocityToPulse(rr, DIR_RR));
}

void stopRobot() {
  writeWheels(0, 0, 0, 0);
  lastCommandTime = millis();
}

void setVelocity(float forward, float rotation) {
  if (emergencyStop) { stopRobot(); return; }
  forward = constrain(forward, -1.0f, 1.0f);
  rotation = constrain(rotation, -1.0f, 1.0f);
  float lf = forward - rotation, rf = forward + rotation;
  float lr = forward + rotation, rr = forward - rotation;
  float scale = max(1.0f, max(max(fabs(lf), fabs(rf)), max(fabs(lr), fabs(rr))));
  writeWheels(lf / scale, lr / scale, rf / scale, rr / scale);
  lastCommandTime = millis();
}

void sendResponse(WiFiClient& client, const char* body) {
  client.print("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n");
  client.print(body);
}

float parameter(const String& request, const char* name) {
  int start = request.indexOf(String(name) + "=");
  if (start < 0) return 0.0f;
  start += strlen(name) + 1;
  int end = request.indexOf('&', start);
  if (end < 0) end = request.indexOf(' ', start);
  return request.substring(start, end < 0 ? request.length() : end).toFloat();
}

void handleClient() {
  WiFiClient client = server.available();
  if (!client) return;
  String request = client.readStringUntil('\n');
  if (request.indexOf("GET /stop ") >= 0) {
    stopRobot(); sendResponse(client, "CMD_OK,STOP");
  } else if (request.indexOf("GET /emergency ") >= 0) {
    emergencyStop = true; stopRobot(); sendResponse(client, "CMD_OK,EMERGENCY_STOP");
  } else if (request.indexOf("GET /clear_emergency ") >= 0) {
    emergencyStop = false; sendResponse(client, "CMD_OK,CLEAR_EMERGENCY");
  } else if (request.indexOf("GET /move?") >= 0) {
    float forward = parameter(request, "vx");
    float rotation = parameter(request, "wz");
    setVelocity(forward, rotation);
    sendResponse(client, "CMD_OK,MOVE");
  } else {
    client.print("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\nCMD_ERROR,NOT_FOUND");
  }
  client.stop();
}

void setup() {
  Serial.begin(115200);
  ESP32PWM::allocateTimer(0); ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2); ESP32PWM::allocateTimer(3);
  servoLF.setPeriodHertz(50); servoLR.setPeriodHertz(50);
  servoRF.setPeriodHertz(50); servoRR.setPeriodHertz(50);
  servoLF.attach(LF_PWM, SERVO_MIN_US, SERVO_MAX_US);
  servoLR.attach(LR_PWM, SERVO_MIN_US, SERVO_MAX_US);
  servoRF.attach(RF_PWM, SERVO_MIN_US, SERVO_MAX_US);
  servoRR.attach(RR_PWM, SERVO_MIN_US, SERVO_MAX_US);
  stopRobot();
  WiFi.mode(WIFI_STA); WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000) delay(500);
  if (WiFi.status() == WL_CONNECTED) { server.begin(); ArduinoOTA.setHostname(OTA_HOSTNAME); ArduinoOTA.setPassword(OTA_PASSWORD); ArduinoOTA.begin(); }
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) { handleClient(); ArduinoOTA.handle(); }
  if (!emergencyStop && lastCommandTime > 0 && millis() - lastCommandTime > COMMAND_TIMEOUT_MS) stopRobot();
  delay(5);
}
