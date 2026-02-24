# Release Notes

## Unreleased — since `4cac4d9` (fix bhz und fbhz)

---

### Bugfixes — PairingEngine (Swiss System)

#### `backtrack()` — falscher Basisfall (kritisch)
Der Basisfall `if (start == matchList.size()) return true` feuerte, sobald alle Match-Kandidaten aufgebraucht waren — unabhängig davon, ob alle Spieler gepairt wurden. Gleichzeitig wurde eine vollständige, gültige Paarung zurückgeworfen, wenn alle Spieler gepairt waren, aber noch Match-Indizes übrig waren (der Loop lief weiter, fand nichts mehr und gab `false` zurück). Beides führte dazu, dass `forcePairing` `finished = true` setzte und das Turnier vorzeitig beendete.

**Fix:** Frühzeitiger Erfolgsabbruch wenn alle Spieler gematcht sind; `false` wenn Matches erschöpft aber noch Spieler offen:
```java
if (usedPlayers.size() == playerList.size()) return true;
if (start == matchList.size()) return false;
```

#### `attempts`-Zähler wurde nicht zwischen Runden zurückgesetzt (moderat)
`attempts` ist ein Instanzfeld. Nach einem `forcePairing`-Aufruf (bei `attempts >= 100`) blieb der Zähler bei 100 und veranlasste in der nächsten Runde einen sofortigen Sprung zu `forcePairing`, ohne die normale Swiss-Auslosung zu versuchen.

**Fix:** `this.attempts = 0` am Anfang von `generatePairings()` (öffentliche Methode).

#### `isByeMatchForAnyPlayer()` prüfte das falsche Spielerfeld (gering, betrifft ungerade Spielerzahlen)
Die Methode prüfte `match.getSecondPlayer()` gegen `byePlayers`. Bei Freilos-Matches ist `secondPlayer == null` — der Check schlug damit nie an für Freilos-Matches. Stattdessen wurden reguläre Matches fälschlich ausgeschlossen, wenn der zweite Spieler bereits ein Freilos hatte.

**Fix:**
```java
return match.getSecondPlayer() == null && byePlayers.contains(match.getFirstPlayer());
```

---

### Verbesserung — Swiss Pairing Algorithmus

Der bisherige Greedy-Loop mit Zufalls-Neustart (`attempts`-Mechanismus, bis zu 100 Versuche) wurde vollständig durch ein **echtes Constraint-Backtracking** ersetzt.

**Entfernt:** `findOpponent()`, `attempts`-Feld, gesamter Neustart-Mechanismus.

**Neu hinzugefügt:**

- **`backtrackSwissPairing()`** — systematisches Backtracking: nimmt den ersten ungepairten Spieler (nach Punkten), probiert Gegner in Swiss-Reihenfolge durch; bei Sackgasse wird die letzte Entscheidung zurückgenommen. Garantiert: findet eine gültige Paarung, wenn eine existiert.

- **`getSwissOrderedOpponents()`** — Swiss-Präferenz bleibt erhalten: Gegner werden nach absolutem Punkteabstand sortiert (gleiche Punkte zuerst, dann ±1, ±2, ...). Sieger spielen bevorzugt gegen Sieger.

- **`canBeFullyMatched()`** — Look-ahead Pruning: bevor eine Paarung bestätigt wird, wird geprüft, ob alle verbleibenden Spieler noch mindestens einen ungespielten Gegner haben. Unmögliche Äste werden frühzeitig abgeschnitten.

Falls das Backtracking keine Swiss-konforme Paarung findet, greift weiterhin `forcePairing()` als Fallback.

---

### Refactoring — Architektur

#### `PairingEngine` als eigenständige Klasse extrahiert
Die gesamte Auslosungslogik (Swiss System, Round Robin, Freilos, Force-Pairing, Backtracking) wurde aus `TournamentRound` in eine dedizierte Klasse `PairingEngine` ausgelagert. `TournamentRound` delegiert nun vollständig an `PairingEngine`.

#### `ScoreCalculator` als eigenständige Klasse extrahiert
Die Wertungsberechnung (Punkte, Siege/Niederlagen, Sätze, Bälle, Buchholz, Fein-Buchholz, Duplikat-Freilos-Entfernung) wurde in `ScoreCalculator` ausgelagert.

#### `UITheme` als zentrale UI-Klasse eingeführt
Farben, Schriften, Button-Factories und Table-Styling sind nun in `UITheme` gebündelt statt über alle View-Klassen verteilt.

---

### TournamentSimulation — überarbeitet

- Nutzt nun `PairingEngine` und `ScoreCalculator` direkt — **keine GUI-Abhängigkeit** mehr (kein `TournamentRound`/`JFrame`)
- Zuvor blockierte die Simulation am Ende, da `TournamentRound.startNextRound()` intern einen `JOptionPane`-Dialog öffnet, der auf Benutzereingabe wartet
- Konfigurierbar über Konstanten: `NUMBER_OF_PLAYERS`, `NUMBER_OF_SIMULATIONS`, `MIN_ROUNDS`
- Gibt am Ende einen strukturierten Bericht aus:

```
========================================
          SIMULATIONSBERICHT
========================================
Simulationen gesamt : 100
Spieleranzahl       : 11
Mindestrunden       : 7
----------------------------------------
Erfolgreich         : 100 (100%)
Fehlgeschlagen      : 0   (0%)
========================================
```

---

### Tests — neu

| Neue Testklasse | Testanzahl | Abdeckung |
|----------------|-----------|-----------|
| `PairingEngineTest` | ~60 | Swiss/Round-Robin-Pairing, Freilos-Logik, Backtracking, `forcePairing`, State-Restore, `calculatePairingDifference`, `selectUniquePlayerMatches` |
| `ScoreCalculatorTest` | ~40 | Punkte, Sieg/Niederlage, Satz-/Ballwertung, Buchholz, Fein-Buchholz, Duplikat-Freilos |
| `TournamentStateTest` | ~10 | Record-Konstruktion und Feldzugriff |

**Gesamtzahl Tests:** 113 (zuvor: ~30)

#### Angepasster Test
`testCalculatePairingDifference_byePlayerAsOpponent_excluded` wurde in `testCalculatePairingDifference_byePlayerAsOpponent_notExcluded` umbenannt und korrigiert — das alte Verhalten war fehlerhaft (reguläre Matches wurden ausgeschlossen, weil der Gegner ein Freilos hatte). Ein neuer Test `testCalculatePairingDifference_byeMatchForAlreadyByePlayer_excluded` validiert das korrekte Verhalten.

---

### Dokumentation

- **`README.md`**: Java-Version korrigiert (17 → 21), Build-Befehle auf Gradle umgestellt, Projektstruktur und Einschränkungen aktualisiert
- **`repo.md`**: Projektstruktur um `UITheme.java` und neue Testdateien ergänzt, Testtabelle vollständig aktualisiert
- **`.zencoder/rules/repo.md`**: Testdateien-Liste aktualisiert
