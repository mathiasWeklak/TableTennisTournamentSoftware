# 🏓 TableTennisTournamentSoftware

## Übersicht

**TableTennisTournamentSoftware** ist eine Java-basierte Desktop-Anwendung zur Verwaltung von Tischtennisturnieren.  
Sie unterstützt sowohl das **Schweizer System** als auch den Modus **„Jeder gegen Jeden“** und bietet eine benutzerfreundliche Oberfläche für Spielerverwaltung, Auslosung, Ergebniseingabe und Schiedsrichterzettel.

---

## ✨ Funktionen

- ✅ **Automatisches Speichern** zu Beginn jeder neuen Runde (Dateiname basiert auf Turniername)
- 🔁 **Fortsetzen unterbrochener Turniere** durch Laden gespeicherter `.ser`-Dateien
- 👥 **Spieler- und Turnierverwaltung**: Hinzufügen/Entfernen von Spielern, Turniername, Tischanzahl, Moduswahl
- 📋 **Turnierablauf**: automatische oder manuelle Auslosung, Anzeige der laufenden Runde
- 🪑 **Automatische Tischzuweisung** für jede Begegnung
- 🕳️ **Freilos-Unterstützung**: bei ungerader Teilnehmerzahl erhält eine Person ein 3:0-Freilos mit 33:0 Punkten
- 🧮 **Ergebniseingabe**: satzweise Eingabe mit automatischer Gesamtberechnung
- 🖨️ **Schiedsrichterzettel**: Anzeigen und Drucken pro Tisch
- 🛠️ **Manuelle Anpassung der Setzung** (nur im Schweizer System) möglich
- 🔒 **Datenkonsistenz**: keine mehrfachen Freilose, keine doppelte Auswertung bereits gewerteter Spiele

---

## 📊 Ranglistenberechnung

- Die Rangliste wird nach jedem Durchlauf auf Basis folgender Kriterien aktualisiert:
    - Punkte (1 Punkt pro Sieg oder Freilos)
    - Siege und Niederlagen
    - Satzverhältnis, Ballverhältnis
    - Buchholz & Fein-Buchholz (nur im Schweizer System)
- Jedes Match wird **nur einmal** gewertet – auch nach dem Laden (über internes Flag `evaluated`)
- Freilos-Spiele zählen als 3:0 mit 33:0 Bällen

---

## 📂 Dateiverwaltung

- Alle gespeicherten Turniere werden als `.ser`-Dateien im **„Dokumente“-Ordner des aktuellen Benutzers** abgelegt
- Der Dateiname wird automatisch aus dem Turniernamen generiert (Sonderzeichen werden ersetzt)
- Über den Menüpunkt **„Turnier laden“** kann eine gespeicherte Datei geöffnet und fortgesetzt werden

---

## ⚠ Einschränkungen

- ❌ Kein manuelles Speichern durch Benutzer – nur automatisches Speichern vor neuer Runde
- 🧪 Die Setzlogik im Schweizer System bevorzugt gleiche Standings, ist aber **nicht offiziell TTR-konform**

---

## ⚙️ Systemanforderungen & Ausführung

- **Java-Version**: Java 21 oder höher erforderlich
- **Empfohlene Ausführung**:
    - Direkt in einer IDE wie IntelliJ IDEA oder Eclipse
    - Oder über Gradle:

```bash
# Anwendung starten
./gradlew run

# Tests ausführen
./gradlew test

# Ausführbare JAR erstellen
./gradlew build
```

---

## 🛡️ Fehlerbehandlung & Stabilität

- Spieler können **maximal ein Freilos** erhalten – doppelte Einträge werden automatisch entfernt
- Bereits bewertete Spiele werden **nicht doppelt ausgewertet**
- Fehler beim Speichern/Laden werden benutzerfreundlich über Dialogfenster gemeldet

---

## 🧩 Projektstruktur (für Entwickler)

- `controller` – Turnierlogik, UI-Aktionen, Speichern/Laden; enthält `PairingEngine` (Auslosung) und `ScoreCalculator` (Wertung)
- `model` – Datenklassen: `Player`, `Match`, `TournamentState`
- `simulation` – `TournamentSimulation`: GUI-freie Simulation zum Testen der Auslosungslogik
- `view` – Swing-basierte Benutzeroberfläche (Erfassung, Tabellen, Menü, Zettel etc.)
- Einstiegspunkt: `TournamentController.main()`  
  Lädt das Hauptfenster und koordiniert den Ablauf

---

## 📜 Lizenz

[**Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)**](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.de)

> **Die Software ist ausschließlich für nicht-kommerzielle, nicht-TTR-relevante Turniere gedacht.**  
> Eine Nutzung in offiziellen oder leistungsrelevanten Wettkämpfen ist untersagt.

---

## 👨‍💻 Autor

**Mathias Weklak**
