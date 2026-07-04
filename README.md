# VESC Bridge – Android App

Native Android-App (Kotlin + Jetpack Compose) zur Steuerung der VESC BLE/WiFi Bridge
über deren lokale HTTP-API. Mehrere Geräte speicherbar, schnelles Umschalten,
Status-Anzeige und volle Mehrkanal-LED-Steuerung.

## Bauen / Installieren

1. **Android Studio** (aktuelle Version, z.B. Koala/Ladybug) öffnen.
2. `File > Open` → diesen Ordner `VescBridgeApp` wählen.
3. Android Studio lädt automatisch Gradle + Abhängigkeiten (Internet nötig).
4. Handy per USB anschließen (USB-Debugging aktiv) oder Emulator starten.
5. Auf den grünen ▶ "Run"-Button klicken → App wird gebaut und installiert.

Alternativ per Kommandozeile (im Projektordner):
```
./gradlew assembleDebug
```
Die fertige APK liegt dann unter:
`app/build/outputs/apk/debug/app-debug.apk`
(per `adb install` aufs Handy, oder die Datei aufs Handy kopieren und antippen).

## Erste Schritte in der App

1. Unten auf **"Geräte"** tippen → **+** → Name (z.B. "G30") eingeben.
   - **Mehrere IPs** eintragen (Button "IP hinzufügen"): z.B. die Heimnetz-IP
     `10.0.0.142` UND die AP-IP `192.168.9.1`. Die App pingt alle und nutzt
     automatisch die erreichbare als Datenquelle.
   - **AP-SSID + Passwort** (optional): der WLAN-Name und das Passwort des
     Access Points der Bridge. Damit kann die App automatisch mit dem AP
     verbinden, wenn das Handy in **keinem** WLAN ist (mobile Daten zählen nicht).
2. Mehrere Geräte einfach mehrfach hinzufügen; oben über das Wechsel-Symbol umschalten.
3. **Status**: Live-Telemetrie (BLE, VESC, WiFi, Uptime) + welche IP gerade genutzt wird.
   **LED**: Mehrkanal-Steuerung.

## Auto-Connect zum Access Point

Wenn keine der hinterlegten IPs erreichbar ist UND das Handy in keinem WLAN
hängt (nur mobile Daten oder gar nichts), verbindet die App automatisch mit dem
AP der Bridge (SSID + Passwort müssen beim Gerät hinterlegt sein). Android zeigt
dabei **einmal einen Systemdialog** ("Mit [SSID] verbinden?") — das ist von
Android ab Version 10 so vorgeschrieben und lässt sich nicht umgehen. Nach dem
Bestätigen läuft der App-Verkehr über den AP. Über "Vom AP trennen" (im Status)
geht es zurück ins normale Netz.

## Hinweise

- Die App kommuniziert über **HTTP** (kein TLS) – das Handy muss im selben Netz
  wie die Bridge sein (Heim-WLAN oder direkt mit dem AP der Bridge verbunden).
- Cleartext-HTTP ist in der App bewusst erlaubt (siehe `network_security_config.xml`).
- Mindest-Android: 8.0 (API 26).
