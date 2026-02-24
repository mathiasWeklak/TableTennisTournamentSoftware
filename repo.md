# Repository Overview: TableTennisTournamentSoftware

## Project Summary

A Java-based desktop application for managing table tennis tournaments. Supports both the **Swiss System** and **Round-Robin** ("Jeder gegen Jeden") modes with a Swing-based GUI.

- **Author**: Mathias Weklak
- **Language**: Java 21
- **Build Tool**: Gradle (Kotlin DSL)
- **GUI Framework**: Java Swing
- **License**: CC BY-NC-SA 4.0 (non-commercial use only)
- **Entry Point**: `controller.TournamentController.main()`
- **Output JAR**: `build/libs/TableTennisTournamentSoftware.jar`

---

## Project Structure

```
src/
├── main/java/
│   ├── controller/
│   │   ├── TournamentController.java       # App entry point, player setup, file loading
│   │   ├── TournamentRound.java            # Round UI; delegates to PairingEngine and ScoreCalculator
│   │   ├── PairingEngine.java              # Swiss System and Round Robin pairing algorithms
│   │   ├── ScoreCalculator.java            # Points, Buchholz, and FeinBuchholz calculation
│   │   ├── MatchManagerController.java     # Match assignment and management
│   │   ├── RefereeSheetsController.java    # Referee sheet generation
│   │   └── ResultEntryController.java      # Match result input handling
│   ├── model/
│   │   ├── Player.java                     # Player data model with stats
│   │   ├── Match.java                      # Match between two players with set results
│   │   └── TournamentState.java            # Serializable snapshot of tournament state
│   ├── simulation/
│   │   └── TournamentSimulation.java       # GUI-free simulation; drives PairingEngine directly
│   └── view/
│       ├── TournamentView.java             # Main setup window
│       ├── UITheme.java                    # Shared colors, fonts, and component factory
│       ├── MatchManagerView.java           # Match manager UI
│       ├── RefereeSheetsView.java          # Referee sheet UI
│       └── ResultEntryView.java            # Result entry UI
└── test/java/
    ├── MatchTest.java                      # Unit tests for Match
    ├── PlayerTest.java                     # Unit tests for Player
    ├── PairingEngineTest.java              # Unit tests for PairingEngine (Swiss + Round Robin)
    ├── ScoreCalculatorTest.java            # Unit tests for ScoreCalculator
    └── TournamentStateTest.java            # Unit tests for TournamentState record
```

---

## Key Classes

### `model.Player`
Represents a tournament participant. Tracks:
- Identity: `firstName`, `lastName`, `club`, `ttr` (TTR rating)
- Stats: `points`, `wins`, `losses`, `setsWon`, `setsLost`, `ballsWon`, `ballsLost`
- Swiss-specific: `buchholz`, `feinBuchholz`

### `model.Match`
Represents a match between two players. Contains:
- `firstPlayer`, `secondPlayer`, `tableNumber`
- Up to 5 set results via inner `Result` class (5×2 string array)
- `overallResult` string
- `evaluated` flag — prevents double-counting on reload

### `model.TournamentState`
A `record` implementing `Serializable`. Snapshot of the full tournament:
- `playerList`, `allMatches`, `matches` (current round)
- `currentRound`, `finished`, `tournamentName`, `tableCount`
- `modus` (`true` = round-robin, `false` = Swiss system)

### `controller.TournamentController`
- Application entry point (`main()`)
- Manages player list (add/remove, sorted by TTR descending)
- Validates tournament name and table count
- Loads saved `.ser` tournament files via `JFileChooser`
- Launches `TournamentRound` on start

---

## Tournament Modes

| Mode | Description |
|------|-------------|
| Swiss System (`modus=false`) | Pairing based on standings; supports Buchholz & Fein-Buchholz; manual seeding adjustment possible |
| Round-Robin (`modus=true`) | Every player plays against every other player |

---

## Ranking Criteria

Rankings are updated after each round based on:
1. Points (1 per win or bye)
2. Wins / Losses
3. Set ratio
4. Ball ratio
5. Buchholz score (Swiss only)
6. Fein-Buchholz score (Swiss only)

---

## Persistence

- Tournament state is auto-saved as a `.ser` file at the start of each new round
- Files are stored in the current user's **Documents** folder
- File name is derived from the tournament name (special characters replaced)
- Saved states can be restored via **"Turnier laden"** menu item

---

## Bye (Freilos) Handling

- Triggered when there is an odd number of players
- The receiving player gets a **3:0 win with 33:0 balls**
- Each player can receive **at most one bye** throughout the tournament

---

## Dependencies

| Dependency | Version | Scope |
|-----------|---------|-------|
| `org.junit.jupiter:junit-jupiter` | 5.11.0-M2 | Test |

---

## Build & Run

```bash
# Run with Gradle
./gradlew run

# Run tests
./gradlew test

# Build executable JAR
./gradlew build
# Output: build/libs/TableTennisTournamentSoftware.jar
```

**Requirements**: Java 21+

---

## Tests

| Test Class | Covers |
|-----------|--------|
| `PlayerTest` | Constructor initialization, getters/setters for all stats fields |
| `MatchTest` | Constructor, table/result getters/setters, invalid index/data handling for `Result` inner class |
| `PairingEngineTest` | Swiss System pairing (even/odd players, bye logic, backtracking, forcePairing), Round Robin termination, state restore, `calculatePairingDifference`, `selectUniquePlayerMatches` |
| `ScoreCalculatorTest` | Points, wins/losses, set/ball tallying, Buchholz, FeinBuchholz, duplicate bye removal |
| `TournamentStateTest` | Record construction and field access |

Test framework: **JUnit Jupiter 5** — 113 tests total
