// cristinel.ababei; 2016;
//
// this is the Arduino Uno sketch for the following project:
// an 8x8 LED matrix is connected to Arduino, which also has connected
// an ESP8266 WiFi module; the LED matrix is controlled to display a 
// heart shape with a lighting effect that can be one of: 
// (1) heart beating or (2) heart fading;
// either of these effects can be selected/controlled via an Android 
// smartphone app with two buttons;
//
// connections of the 8x8 LED matrix:
// MAX7219 VCC pin   --> Arduino 5V pin
// MAX7219 GND pin   --> Arduino GND pin
// MAX7219 DIN pin   --> Arduino pin 2
// MAX7219 CS pin    --> Arduino pin 3
// MAX7219 CLOCK pin --> Arduino pin 4
//
// connections to the ESP8266 are done via 3-resistor level converter;
// ESP8266 TX --> Arduino pin 10 (used as RX of Software serial)
// ESP8266 RX --> 3-resistor --> Arduino pin 11 (used as TX of Software serial)

#include <SoftwareSerial.h>
 
#define DEBUG true

int ANIMDELAY = 100;  // animation delay, deafault value is 100
int INTENSITYMIN = 0; // minimum brightness, valid range [0,15]
int INTENSITYMAX = 8; // maximum brightness, valid range [0,15]

int DIN_PIN = 2;      // data in pin
int CS_PIN = 3;       // load (CS) pin
int CLK_PIN = 4;      // clock pin

// MAX7219 registers
byte MAXREG_DECODEMODE = 0x09;
byte MAXREG_INTENSITY  = 0x0a;
byte MAXREG_SCANLIMIT  = 0x0b;
byte MAXREG_SHUTDOWN   = 0x0c;
byte MAXREG_DISPTEST   = 0x0f;

const unsigned char heart[] =
{
  B01100110,
  B11111111,
  B11111111,
  B11111111,
  B01111110,
  B00111100,
  B00011000,
  B00000000
};

boolean turn_ON_beating = false;
boolean turn_ON_fading = false;
int connection_Id = 0;
int effect_Number = 0;

// RX Arduino line is pin 10, TX Arduino line is pin 11;
// this means that you need to connect TX line from ESP8266 to the Arduino's pin 10
// and the RX line from ESP8266 to the Arduino's pin 11;
SoftwareSerial esp8266(10, 11); // RX, TX

///////////////////////////////////////////////////////////////////////////////
//
// setup;
//
///////////////////////////////////////////////////////////////////////////////

void setup()
{
  // (1) hardware serial connects to host PC; software serial connects to ESP8266;
  Serial.begin(9600);
  esp8266.begin(9600); // make sure baudrate of your ESP8266 module is the same 9600;

  // this approach does not need to use a name and password to connect to a WiFI router; 
  // to create connection between ESP8266 and your Android device: 
  // -->check wifi networks available on your Android & connect to the one that is ESP8266 module;
  // -->on the Android phone, when using the provided app, you must type in the IP
  // address of the ESP8266 module, as printed by the debug messages on the Arduino's
  // Serial terminal window;  
  send_CMD_or_Data("AT+RST\r\n",1000,DEBUG); // reset module
  delay(100);
  send_CMD_or_Data("AT+CWMODE=2\r\n",1000,DEBUG); // configure as access point
  delay(100);
  send_CMD_or_Data("AT+CIFSR\r\n",1000,DEBUG); // get ip address
  delay(100);
  send_CMD_or_Data("AT+CIPMUX=1\r\n",1000,DEBUG); // configure for multiple connections
  delay(100);
  send_CMD_or_Data("AT+CIPSERVER=1,80\r\n",1000,DEBUG); // turn on server on port 80
  delay(100);

  Serial.println("\r\n\r\nWiFi ESP8266 module ready!");  
  
  // (2) things related to the 8x8 LED matrix display;
  pinMode(DIN_PIN, OUTPUT);
  pinMode(CLK_PIN, OUTPUT);
  pinMode(CS_PIN, OUTPUT);
  // initialization of the MAX7219;
  setRegistry(MAXREG_SCANLIMIT, 0x07);
  setRegistry(MAXREG_DECODEMODE, 0x00);  // using an led matrix (not digits)
  setRegistry(MAXREG_SHUTDOWN, 0x01);    // not in shutdown mode
  setRegistry(MAXREG_DISPTEST, 0x00);    // no display test
  setRegistry(MAXREG_INTENSITY, 0x0f & INTENSITYMIN);
  // draw heart;
  heart_static_and_dim(); 
}

///////////////////////////////////////////////////////////////////////////////
//
// loop;
//
///////////////////////////////////////////////////////////////////////////////

// below is an example of what ESP8266 receives from Android phone, when
// button 1 is pressed on the app; the logic inside the loop() is to record this into a 
// String and then, look for "+IPD" and "effect="; then, read the "0"
// "1" (or "2") as the connection_Id and effect_Number from the recorded String;

//"0,CONNECT
//
//+IPD,0,242:POST / HTTP/1.1
//Content-Type: application/x-www-form-urlencoded
//User-Agent: Dalvik/2.1.0 (Linux; U; Android 10; SM-G960U Build/QP1A.190711.020)
//Host: 192.168.4.1
//Connection: Keep-Alive
//Accept-Encoding: gzip
//Content-Length: 8
//
//effect=1"

// and after a while (timeout used inside the Android phone) also:
// "0,CLOSED"

void loop()
{
  connection_Id = 0;

  // check if ESP8266 module sent anything to Arduino board;
  // NOTE: ESP8266 does send some garbage characters to Arduino even when it 
  // does not receive anything via WiFi?! 
  if (esp8266.available()) { 
    delay(100);

    // (1) read for up to 3 seconds everything into a String;
    String received_from_esp = "";
    long int time = millis();    
    while ( (time + 2000) > millis()) { // 3 seconds timeout;
      while (esp8266.available()) {
        char c = esp8266.read(); // read next character;
        received_from_esp += c;
      }  
    }

    // (2) check if what was received from ESP8266 contains the keyowrd "+IPD"
    int pos_of_IPD = received_from_esp.indexOf("+IPD"); // position of "+IPD"
    if (pos_of_IPD >= 0) {
      if (DEBUG) {
        Serial.println("\r\n----------------------- Received from ESP8266:");
        Serial.print(received_from_esp);
      }
      String substr_Id = received_from_esp.substring((pos_of_IPD + 5), (pos_of_IPD + 6)); // chars containing the connection Id
      connection_Id = substr_Id.toInt();
      if ( DEBUG) {
        Serial.print("\r\nconnection_Id = ");
        Serial.print(connection_Id);
      }
    }

    // (3) check if what was received from ESP8266 contains the keyowrd "effect="
    int pos_of_effect = received_from_esp.indexOf("effect="); // position of "effect="
    if (pos_of_effect >= 0) {
      String substr_Number = received_from_esp.substring((pos_of_effect + 7), (pos_of_effect + 8)); // chars containing the effect Number
      effect_Number = substr_Number.toInt();
      if ( DEBUG) {
        Serial.print("\r\nReceived effect = ");
        Serial.print(effect_Number);
      }

      // (4) take appropriate action according to received effect number;
      // "effect" can be 1 or 2; else do nothing;
      if ( effect_Number == 1) {
        turn_ON_beating = true;
        turn_ON_fading = false;
      } else if ( effect_Number == 2) {
        turn_ON_beating = false;
        turn_ON_fading = true;
      } else {
        turn_ON_beating = false;
        turn_ON_fading = false;
      }

      // (5) sending to Android phone confirmation;  
      // build String that is send back to ESP8266;
      // NOTE: commented this one out; no need for confirmation inside Android app;
      /*---
      String reply_to_ESP8266; // response to send back to ESP8266 to send to Android phone, as confirmation of what was received;
      if ( effect_Number == 1 || effect_Number == 2) {
        reply_to_ESP8266 = "Effect ";
        reply_to_ESP8266 += effect_Number;
        reply_to_ESP8266 += " is ";
        reply_to_ESP8266 += "ON ";
      } else {
        reply_to_ESP8266 = "Communication Error!";
      }
      sendHTTPResponse(connection_Id, reply_to_ESP8266);
      ---*/

      // (6) build also the close command; 
      // NOTE: commented this one out as well;
      /*---
      String closeCommand = "AT+CIPCLOSE="; 
      closeCommand+=connection_Id; // append connection id
      closeCommand+="\r\n";
      send_CMD_or_Data(closeCommand,2000,DEBUG); // close connection
      ---*/
    }

  } // if (esp8266.available()) 
  
  // (7) implement the requested effect;
  if ( turn_ON_beating == true) {
    heart_beating_effect();
  } else if ( turn_ON_fading == true) {
    heart_fading_effect();
  } else {
    heart_static_and_dim();
  }
}

///////////////////////////////////////////////////////////////////////////////
//
// functions related to ESP8266 wifi module;
//
///////////////////////////////////////////////////////////////////////////////

void sendHTTPResponse(int connectionId, String content)
{
  // function that sends HTTP 200, HTML UTF-8 response to ESP8266 module;
  // build HTTP response
  String httpResponse;
  String httpHeader;
  // HTTP Header
  httpHeader = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n"; 
  httpHeader += "Content-Length: ";
  httpHeader += content.length();
  httpHeader += "\r\n";
  httpHeader +="Connection: close\r\n\r\n";
  // HTTP response
  httpResponse = httpHeader + content;
  sendCIPData(connectionId, httpResponse);
}

void sendCIPData(int connectionId, String data)
{
  // sends a CIPSEND=<connectionId>,<data> command to ESP8266;
  String cipSend = "AT+CIPSEND=";
  cipSend += connectionId;
  cipSend += ",";
  cipSend +=data.length();
  cipSend +="\r\n";
  send_CMD_or_Data(cipSend, 1000, DEBUG); 
  send_CMD_or_Data(data, 1000, DEBUG);
}

String send_CMD_or_Data(String command_or_data, const int timeout, boolean debug)
{
  // function used to send AT command or data to ESP8266 WiFi module;
  String response = "";

  int dataSize = command_or_data.length() + 1; 
  char data[dataSize];
  command_or_data.toCharArray(data, dataSize); 
  esp8266.write(data, dataSize);
  // NOTE: an alternative to the above four lines of code is:
  //esp8266.print(command_or_data);

  if (debug) {
    Serial.println("\r\n----------------------- Sending CMD/Data to ESP8266:");
    Serial.write(data,dataSize);
  }

  long int time = millis();    
  while ( (time + timeout) > millis()) {
    while (esp8266.available()) {
      char c = esp8266.read(); // read the next character.
      response += c;
    }  
  }
  if (debug) {
    Serial.println("\r\n----------------------- ESP8266's response:");
    Serial.print(response);
  }   
  return response;
}

///////////////////////////////////////////////////////////////////////////////
//
// functions related to handling the 8x8 LED matrix;
//
///////////////////////////////////////////////////////////////////////////////

void heart_beating_effect()
{
  // first beat
  setRegistry(MAXREG_INTENSITY, 0x0f & INTENSITYMAX);
  delay(ANIMDELAY); 
  // switch off
  setRegistry(MAXREG_INTENSITY, 0x0f & INTENSITYMIN);
  delay(ANIMDELAY);  
  // second beat
  setRegistry(MAXREG_INTENSITY, 0x0f & INTENSITYMAX);
  delay(ANIMDELAY); 
  // switch off
  setRegistry(MAXREG_INTENSITY, 0x0f & INTENSITYMIN);
  delay(ANIMDELAY*6);
}

void heart_fading_effect()
{
  for (int i = 15; i > 0; i--) {
    setRegistry(MAXREG_INTENSITY, 0x0f & i);
    delay(200); 
  }
}

void heart_static_and_dim()
{
  setRegistry(MAXREG_INTENSITY, 0x0f & INTENSITYMIN);
  setRegistry(1, heart[0]);
  setRegistry(2, heart[1]);
  setRegistry(3, heart[2]);
  setRegistry(4, heart[3]);
  setRegistry(5, heart[4]);
  setRegistry(6, heart[5]);
  setRegistry(7, heart[6]);
  setRegistry(8, heart[7]);
}

void setRegistry(byte reg, byte value)
{
  digitalWrite(CS_PIN, LOW);

  putByte(reg);   // specify register
  putByte(value); // send data

  digitalWrite(CS_PIN, LOW);
  digitalWrite(CS_PIN, HIGH);
}

void putByte(byte data)
{
  byte i = 8;
  byte mask;
  while (i > 0) {
    mask = 0x01 << (i - 1);        // get bitmask
    digitalWrite( CLK_PIN, LOW);   // tick
    if (data & mask) {             // choose bit
      digitalWrite(DIN_PIN, HIGH); // send 1
    } else {
      digitalWrite(DIN_PIN, LOW);  // send 0
    }
    digitalWrite(CLK_PIN, HIGH);   // tock
    --i;                           // move to lesser bit
  }
}
