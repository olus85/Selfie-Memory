# Selfie-Memory: Kritischer Bug-Report & Architektur-Audit

Dieses Dokument enthält eine detaillierte Analyse der 10 kritischsten Fehler in der Selfie-Memory App. Diese Fehler führen zu massivem Akkuverbrauch, Unzuverlässigkeit bei der Bildaufnahme und potenziellen Datenlecks.

## 1. Prioritäten-Übersicht

| ID | Problembereich | Priorität | Auswirkung |
|---|---|---|---|
| #1 | SettingsViewModel Service-Lifecycle | CRITICAL | Extremer Akku-Drain |
| #2 | BootReceiver Konfigurationsprüfung | CRITICAL | System-Instabilität nach Boot |
| #3 | Service Permission Check | HIGH | Dienst-Abstürze |
| #4 | NetworkMonitor Logikfehler | HIGH | Trigger lösen nicht aus |
| #5 | Cooldown Zeitberechnung | HIGH | Fehlende Aufnahmen |
| #6 | Daily Limit Race Condition | HIGH | Speicher-Überlauf |
| #7 | Main-Thread Blockade (UI Freeze) | HIGH | App reagiert nicht mehr |
| #8 | Enum Parsing Crash | MEDIUM | App-Absturz beim Start |
| #9 | ImageProxy Memory Leak | MEDIUM | Zunehmende Trägheit |
| #10 | Storage-Leak / File Orphaning | MEDIUM | Datenmüll im Dateisystem |

---

## #1 BUG: Redundante Service-Starts (Akku-Drain)

**Zusammenfassung:** Das SettingsViewModel startet den SelfieCaptureService bei jeder Initialisierung und bei jeder Änderung der Einstellungen neu, ohne effektiv zu prüfen, ob der Dienst bereits läuft. Dies führt zu einer Flut von Intents.

**Lösungsansatz:** Entferne den Service-Start aus dem init-Block des ViewModels. Implementiere ein explizites Start/Stopp-Kommando im Service.

---

## #2 BUG: BootReceiver ohne Validierung

**Zusammenfassung:** Der BootReceiver startet den Service blind nach jedem Neustart, auch wenn der Nutzer noch keine WLAN-Bedingungen konfiguriert hat oder die Überwachung deaktiviert wünscht.

**Lösungsansatz:** Nutze `goAsync()` im BroadcastReceiver, um die DataStore-Einstellungen asynchron zu prüfen. Starte den Service nur, wenn eine gültige Konfiguration vorliegt.

---

## #3 BUG: Receiver ohne Permission-Check

**Zusammenfassung:** Der `USER_PRESENT` Receiver wird im Service registriert, bevor geprüft wird, ob die Kamera-Berechtigung noch erteilt wurde.

**Lösungsansatz:** Verschiebe die Receiver-Registrierung in den Block nach dem Permission-Check.

---

## #4 BUG: NetworkMonitor Trigger feuert nicht

**Zusammenfassung:** Der NetworkMonitor löst trotz korrekter WLAN-Verbindung keine Aufnahme aus.

**Lösungsansatz:** Überprüfe die Netzwerk-Metriken und stelle sicher dass der State-Flow korrekt durchläuft.

---

## #5 BUG: Cooldown-Zeitberechnung

**Zusammenfassung:** Das Cooldown-Intervall wird falsch berechnet, sodass Aufnahmen blockiert werden obwohl der Cooldown abgelaufen ist.

**Lösungsansatz:** Prüfe die Zeitberechnungslogik und stelle sicher dass UTC/Local-Time konsistent gehandhabt wird.

---

## #6 BUG: Daily Limit Race Condition

**Zusammenfassung:** Bei gleichzeitigem Zugriff auf das Daily-Limit wird die Grenze überschritten.

**Lösungsansatz:** Nutze AtomicInteger oder eine Datenbank-Transaction für das Daily-Limit.

---

## #7 BUG: Main-Thread Blockade (UI Freeze)

**Zusammenfassung:** Schwere Workloads blockieren den Main-Thread, die App wird unresponsive.

**Lösungsansatz:** Alle I/O-Operationen und Bildverarbeitung auf den IO-Dispatcher auslagern.

---

## #8 BUG: Enum Parsing Crash beim App-Start

**Zusammenfassung:** Die App stürzt ab wenn ein Enum-Wert nicht geparst werden kann (z.B. wegen veraltetem SharedPreferences-Key).

**Lösungsansatz:** Default-Wert im Enum-Parser: `values().firstOrNull() ?: defaultValue`.

---

## #9 BUG: ImageProxy Memory Leak

**Zusammenfassung:** ImageProxy-Objekte werden nicht korrekt geschlossen, was zu Memory Leak führt.

**Lösungsansatz:** Nutze `use {}` oder try-finally um ImageProxy guaranteed zu schliessen.

---

## #10 BUG: Storage-Leak / File Orphaning

**Zusammenfassung:** Dateien werden nicht gelöscht wenn sie nicht mehr referenziert werden, was zu Datenmüll führt.

**Lösungsansatz:** Implementiere einen Cleanup-Task beim App-Start und periodisch.