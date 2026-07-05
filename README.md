# VESC Bridge Android App

A native Android companion application designed to monitor and configure VESC controllers through an ESP32-based BLE/WiFi Bridge. Built with Kotlin and Jetpack Compose.

## Core Features

### 1. Live Telemetry & Status
*   **Real-time Monitoring**: View essential VESC data including voltage, ERPM, FET temperatures, and motor temperatures.
*   **Fault Detection**: Displays VESC fault codes and descriptive error strings.
*   **Connection Diagnostics**: Monitors bridge uptime, free heap memory, WiFi signal strength (RSSI), and ESP32 system diagnostics (watchdog resets, loop times, and packet statistics).

### 2. LED Control System (WS28XX)
*   **Multi-Channel Management**: Supports up to 4 independent LED channels with configurable GPIO pins and pixel counts.
*   **Animation Engine**: Built-in controls for various effects:
    *   Solid Color, Knight Rider (KITT), Rainbow Wave, Breathing, Sparkle, Meteor Rain, and Hellfire.
    *   Emergency lighting: EU Police and US Police (Red/Blue/White) with adjustable frequencies.
*   **Synchronization**: Option to sync settings across all active channels for unified lighting effects.
*   **Real-time Tuning**: Adjust color via an integrated color wheel, brightness, animation speed, and effect width/density on the fly.

### 3. Smart Connectivity Logic
*   **Intelligent Host Resolution**: Automatically searches for the bridge across multiple saved IP addresses and the default Access Point IP (192.168.9.1).
*   **Auto-Connect**: Allows marking a "favorite" device to initiate connection automatically on app startup.
*   **Access Point Fallback**: Automatically triggers a connection request to the bridge's internal Access Point if the home WiFi is unreachable or if the device is set to "AP Only" mode.
*   **Network Pinning**: Uses Android's `ConnectivityManager` to bind the app's traffic to the bridge network even when the OS detects a lack of internet access.

### 4. Remote Configuration
*   **UART Settings**: Configure RX/TX pins and baud rate settings.
*   **BLE Customization**: Change the BLE name (visible in VESC Tool) and set advertising modes (Always On, Off, or Auto-ON based on ERPM/Movement).
*   **WiFi Management**: Manage a priority list of known WiFi networks (SSID/Password/Static IP configurations).
*   **System Maintenance**: Remote reboot triggers, roaming threshold adjustments (RSSI-based switching), and auto-reboot timers.

### 5. Integrated Update System
*   **Bridge Firmware**: Check for and install ESP32 firmware updates directly from the app.
*   **App Updates**: Built-in version checking against GitHub releases.
*   **Smart Download**: The app automatically identifies a secondary internet-capable network (mobile data or home WiFi) to download updates while remaining connected to the bridge AP.

## Technical Stack
*   **Language**: Kotlin
*   **UI**: Jetpack Compose with Material 3
*   **Architecture**: MVVM (ViewModel, StateFlow)
*   **Networking**: HttpURLConnection with specific Network Binding for IoT stability
*   **Persistence**: Jetpack DataStore (Preferences) for device and setting storage

## Requirements
*   **Android**: Min SDK 32 (Android 12L) or higher.
*   **Hardware**: Compatible ESP32 VESC Bridge firmware.

## Build Instructions
Standard Gradle build process:
```bash
./gradlew assembleDebug
```
Ensure you have the latest Android SDK and Build Tools installed via Android Studio.
