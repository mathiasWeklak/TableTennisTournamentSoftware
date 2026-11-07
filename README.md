# 🏓 TableTennisTournamentSoftware

## Übersicht

**TableTennisTournamentSoftware** ist eine Java-basierte Desktop-Anwendung zur Verwaltung von Tischtennisturnieren.  
Sie unterstützt das **Schweizer System**, den Modus **„Jeder gegen Jeden“** sowie **Vorrunde in Gruppen mit anschließender KO-Runde**, jeweils als Einzel oder **Doppel**, und bietet eine benutzerfreundliche Oberfläche für Spielerverwaltung, Auslosung, Ergebniseingabe und Schiedsrichterzettel.

---

## ✨ Funktionen

- 💾 **Automatisches Speichern** nach jeder Ergebniseingabe, Setzungsänderung und Runde sowie beim Schließen
- 🔁 **Fortsetzen unterbrochener Turniere** durch Laden gespeicherter `.ser`-Dateien
- 🏆 **Vorrunde in Gruppen + KO-Runde** mit Turnierbaum (Anzeige und Druck) und Spiel um Platz 3
- 👫 **Doppelkonkurrenzen**: Teilnehmer sind Paare; Setzung nach der Summe beider TTR-Werte, jede Person nur in einem Doppel
- 👥 **Spielerverwaltung**: Hinzufügen, Bearbeiten (auch per Doppelklick), Entfernen mit Rückfrage; gleiche Vor- und Nachnamen werden abgelehnt
- 🎲 **Setzliste**: nach TTR; bei gleichem oder fehlendem TTR entscheidet das Los
- 📋 **Turnierablauf**: Auslosung mit Rückfrage, „Auslosung zurücknehmen“ solange noch kein Ergebnis erfasst ist, optionale Rundenzahl im Schweizer System
- 🚪 **Spieler zurückziehen** (beide Modi) und **Spieler nachmelden** (Schweizer System)
- 🪑 **Automatische Tischzuweisung** für jede Begegnung
- 🧮 **Ergebniseingabe**: satzweise mit Prüfung der Tischtennisregeln, Statusanzeige pro Spiel, Tastaturbedienung (Enter, Strg+S, Esc), „Kampflos“-Schaltflächen, Korrektur früherer Runden
- 🖨️ **Drucken**: Rangliste, Kreuztabelle (kompakt) und Schiedsrichterzettel (6 pro Seite, mit Turniername und Runde)
- 🛠️ **Manuelle Anpassung der Setzung** (nur im Schweizer System)
- ⚠️ **Fehlerdialog** bei unerwarteten Fehlern, Details in `Turniersoftware-Fehler.log`

---

## 🔀 Auslosung im Schweizer System

Die Runde wird als Ganzes optimiert (gewichtetes Matching nach Edmonds), ohne Wiederholungsbegegnungen. Kriterien in dieser Reihenfolge:

1. Möglichst wenige und kleine Punktsprünge zwischen den Punktgruppen
2. Niemand soll wie in der Vorrunde erneut herunterrutschen bzw. hochgeholt werden (ein Freilos zählt als Herunterrutschen)
3. Dasselbe für die Runde davor (schwächer gewichtet)
4. Herunter rutscht der Letzte der höheren Gruppe, er trifft auf den Bestplatzierten der niedrigeren
5. Innerhalb einer Punktgruppe: obere gegen untere Hälfte (1–5, 2–6, …)

Bei ungerader Spielerzahl erhält der niedrigstplatzierte Spieler ohne bisheriges Freilos das Freilos. Ist keine Runde ohne Wiederholung mehr möglich oder die geplante Rundenzahl erreicht, ist das Turnier beendet.

---

## 🏆 Vorrunde in Gruppen + KO-Runde

- **Einstellungen:** Gruppengröße (feste Höchstgröße; geht es nicht auf, haben einige Gruppen einen Teilnehmer weniger), Anzahl Weiterkommende je Gruppe, zusätzlich die x besten Nächstplatzierten (z. B. die besten Gruppendritten)
- **Gruppeneinteilung:** nach Setzliste im Schlangensystem (A1, B1, C1, C2, B2, A2, …)
- **Vorrunde:** Jeder gegen Jeden in jeder Gruppe, alle Gruppen spielen parallel; Gruppentabelle mit direktem Vergleich nach ITTF
- **Beste Nächstplatzierte:** Vergleich über die Gruppen nach Punkten pro Spiel, dann Satz- und Balldifferenz pro Spiel, dann TTR
- **Turnierbaum:** Gruppensieger vor Gruppenzweiten vor den übrigen Weiterkommenden, jeweils nach TTR; Freilose für die Bestgesetzten; Teilnehmer derselben Gruppe treffen möglichst spät aufeinander
- **KO-Runde:** Best of 5, Spiel um Platz 3, Endplatzierung (1–4, danach geteilte Plätze 5, 9, …); zieht sich ein Teilnehmer zurück, kommt sein Gegner kampflos weiter

---

## 📊 Ranglistenberechnung

Gewertet werden nur entschiedene Spiele: Ein Spieler hat 3 Sätze gewonnen, alle Sätze sind gültig (mindestens 11 Punkte, 2 Punkte Vorsprung) und lückenlos erfasst.

**Schweizer System:** Punkte, Buchholz, Fein-Buchholz, Satzdifferenz, Balldifferenz.

- Freilos: 1 Punkt, aber kein Sieg und keine Sätze oder Bälle
- Für Buchholz und Fein-Buchholz zählen Freilos und Runden ohne Paarung (Nachmeldung, Rückzug) als Spiel gegen einen virtuellen Gegner mit den eigenen Punkten

**Jeder gegen Jeden:** Punkte; bei Punktgleichheit der direkte Vergleich nach ITTF (Siege, dann Sätze, dann Bälle nur aus den Spielen der Punktgleichen untereinander; bleibt eine Teilgruppe gleich, wird für sie neu verglichen), danach Satz- und Balldifferenz aller Spiele. Spielfrei gibt 0 Punkte. Zieht sich ein Spieler zurück, werden alle seine Spiele gestrichen (auch bereits gespielte); er steht ohne Rang am Ende der Tabelle. Im Schweizer System bleiben die Ergebnisse eines zurückgezogenen Spielers bestehen.

Spieler, die in allen Kriterien gleich sind, teilen sich den Rang (z. B. 1, 2, 2, 4).

---

## 📂 Dateiverwaltung

- Gespeichert wird im **Dokumente-Ordner** des Benutzers (auch bei Umleitung, z. B. OneDrive), sonst im Benutzerverzeichnis
- `<Turniername>.ser` enthält immer den aktuellen Stand
- `<Turniername>_Runde_N.ser` ist eine Sicherung des Stands nach abgeschlossener Runde N
- Über den Menüpunkt **„Turnier laden“** kann eine gespeicherte Datei geöffnet und fortgesetzt werden

---

## ⚠ Einschränkungen

- 🧪 Die Auslosung orientiert sich am holländischen System der FIDE, ist aber **nicht offiziell TTR-konform**
- Satz- und Balldifferenz werden als Differenz gerechnet, nicht als Verhältnis

---

## ⚙️ Systemanforderungen & Ausführung

- **Java-Version**: Java 25
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

## 🧩 Projektstruktur (für Entwickler)

- `controller` – Turnierlogik, UI-Aktionen, Speichern/Laden; u. a. `PairingEngine` (Auslosung), `WeightedMatching` (Matching-Algorithmus), `ScoreCalculator` (Wertung), `Standings` (Rangliste), `TournamentStorage` (Dateien)
- `model` – Datenklassen: `Player`, `Match`, `TournamentState`, `ResultRules` (Tischtennisregeln)
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
