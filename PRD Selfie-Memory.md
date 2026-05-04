# Product Requirements Document (PRD): "Selfie-Memory"

## 1. Project Overview
**Selfie-Memory** ist eine private, 100% lokale Android-App. Das Ziel ist es, den mentalen Aufwand bei der Outfit-Wahl zu reduzieren, indem die App völlig automatisiert (im Hintergrund) Selfies des Nutzers aufnimmt, sobald das Gerät entsperrt wird. 
Um Batterieverbrauch und störende System-Benachrichtigungen zu minimieren, wird der Kamera-Dienst nur unter spezifischen, vom Nutzer definierten Netzwerkbedingungen (z. B. Büro-WLAN) aktiv. Die App erfordert kein Backend und speichert alle Daten in einer lokalen SQLite-Datenbank und im internen App-Speicher.

**Erfolgskriterien:**
* Die App läuft im Hintergrund stabil und nimmt zuverlässig Fotos beim Entsperren auf.
* Der Akkuverbrauch ist durch die Nutzung von passiven Netzwerk-Triggern und einer Cooldown-Mechanik minimal.
* Die Bilder tauchen nicht in der globalen Foto-Galerie (z.B. Google Photos) auf.

---

## 2. User Stories & Features
* **Als User möchte ich** festlegen können, unter welchen Netzwerkbedingungen (z.B. bestimmter WLAN-Name / SSID) die App aktiv wird, **damit** der Hintergrunddienst (und die Android-Benachrichtigung) nur an relevanten Orten wie dem Büro läuft.
* **Als User möchte ich**, dass die App beim Entsperren des Geräts automatisch ein Foto mit der (Weitwinkel-)Frontkamera macht, **damit** mein Outfit ohne manuelles Zutun festgehalten wird.
* **Als User möchte ich** eine zeitliche Verzögerung (in Sekunden) nach dem Entsperren einstellen können, **damit** die Kamera erst auslöst, wenn ich das Handy richtig vor mir halte.
* **Als User möchte ich** einen Cooldown-Timer (in Minuten) einstellen können, **damit** bei mehrfachem, schnellem Entsperren nicht sofort das Tageslimit aufgebraucht wird.
* **Als User möchte ich** eine maximale Anzahl an Bildern pro Tag festlegen können (wobei das älteste bei Überschreitung gelöscht wird), **damit** mein Speicherplatz nicht vollläuft.
* **Als User möchte ich** in der App eine chronologische Galerie (neueste oben) sehen, **damit** ich meine vergangenen Outfits sofort prüfen kann.
* **Als User möchte ich**, dass GPS-Koordinaten in den Metadaten der Bilder oder der Datenbank gespeichert werden, **damit** ich nachvollziehen kann, wo das Foto entstand.

---

## 3. User Flow & UX
1. **Main Screen (Gallery):**
   * Raster-Ansicht (Grid) mit Thumbnails der gemachten Selfies.
   * Sortierung: Chronologisch absteigend (Neueste oben).
   * Klick auf ein Thumbnail öffnet das Bild im Vollbildmodus (mit Datum, Uhrzeit und grobem Standort als Text-Overlay oder Info-Icon).
2. **Settings Screen:**
   * **Netzwerk-Trigger:** Dropdown (Mobilfunk, Jedes WLAN, Spezifisches WLAN) -> Bei "Spezifisch" ein Textfeld für die SSID.
   * **Kamera-Auswahl:** Dropdown (Front Ultra-Wide [Default], Front Normal, Back).
   * **Capture Delay:** Slider oder Number-Input (0 bis 10 Sekunden).
   * **Cooldown Timer:** Slider oder Number-Input (z.B. 1 bis 120 Minuten).
   * **Daily Limit:** Slider oder Number-Input (z.B. 1 bis 50 Bilder pro Tag).
3. **Background Flow (Unsichtbar für den User):**
   * System erkennt Netzwerkwechsel (ConnectivityManager) -> Entspricht SSID der Settings -> Startet `Foreground Service`.
   * Service registriert BroadcastReceiver für `Intent.ACTION_USER_PRESENT` (Device Unlock).
   * User entsperrt Gerät -> Cooldown Check -> Delay Timer startet -> CameraX nimmt Bild auf -> Location abgerufen -> Speichern in Room DB.

---

## 4. Technical Specifications
* **Tech Stack:** * Plattform: Nativ Android.
  * Sprache: Kotlin.
  * UI: Jetpack Compose.
  * Kamera: CameraX (ImageCapture UseCase).
  * Lokale DB: Room Database.
  * Standort: FusedLocationProviderClient (Google Play Services).
* **Datenmodell (`SelfieEntity`):**
  * `id`: Int (Primary Key, AutoGenerate)
  * `timestamp`: Long (Unix Time)
  * `filePath`: String (Interner App-Speicherpfad)
  * `latitude`: Double?
  * `longitude`: Double?
* **API Definitionen:** * Keine externen Netzwerkanfragen. 100% Offline-Betrieb.

---

## 5. Non-Functional Requirements
* **Privacy & Storage:** Bilder MÜSSEN in `Context.getFilesDir()` oder `Context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)` gespeichert werden, damit sie privat bleiben. Bilder dürfen nicht im MediaStore (System-Galerie) indiziert werden (Ggf. `.nomedia` Datei nutzen, falls externer App-Speicher verwendet wird).
* **Battery Performance:** Die App darf das System nicht permanent wachhalten. Die Überwachung der Netzwerkverbindung muss ressourcenschonend implementiert werden (z.B. über `ConnectivityManager.NetworkCallback`).
* **Permissions:** Notwendige Berechtigungen sauber handhaben (`CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` falls nötig für den WLAN-Scan im Hintergrund, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`).

---

## 6. AI Context Instructions (Prompt für den AI-Agenten)
> **An den AI-Agenten:** Du entwickelst eine native Android-App in Kotlin. Die größte technische Herausforderung dieses Projekts ist die unsichtbare Ausführung der Kamera im Hintergrund auf modernen Android-Versionen (Android 13/14+). 
> 
> **Wichtige Architekturvorgaben:**
> 1. Verwende einen passiven `ConnectivityManager.NetworkCallback`, um das Netzwerk zu überwachen. Nur wenn die definierte Netzwerkbedingung (z.B. spezifizierte SSID) erfüllt ist, darf der `Foreground Service` (Typ `camera`) gestartet werden.
> 2. Um CameraX in einem Foreground Service ohne eine sichtbare UI (`Activity`) zu betreiben, implementiere den Service als `LifecycleService`. Dies gibt CameraX den benötigten Lifecycle-Owner. Du musst vermutlich einen Dummy-`SurfaceTexture` oder nur den `ImageCapture`-UseCase binden, da keine Vorschau (Preview) gezeichnet wird.
> 3. Überwache `ACTION_USER_PRESENT` innerhalb des Foreground Services über einen dynamischen BroadcastReceiver. Setze dort die Logik für Delay, Cooldown und das Limit (Löschen des ältesten Datensatzes aus Room + Dateisystem) um.
> 4. Generiere die UI ausschließlich mit Jetpack Compose.
> 5. Implementiere robustes Error-Handling, falls die Kamera im Hintergrund durch das OS blockiert wird.
