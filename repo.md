# Repository Overview: TableTennisTournamentSoftware

## Project Summary

A Java-based desktop application for managing table tennis tournaments. Supports the **Swiss System**, **Round-Robin** ("Jeder gegen Jeden") and **group stage + knockout stage**, each for singles or **doubles**, with a Swing-based GUI. Native packages for Windows, Linux and macOS are built with jpackage.

- **Author**: Mathias Weklak
- **Language**: Java 25
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
│   │   ├── TournamentRound.java            # Running tournament: rounds, undo, withdrawal, late entry, saving
│   │   ├── PairingEngine.java              # Swiss System (optimal matching), Round Robin (circle method), groups/KO
│   │   ├── GroupKnockout.java              # Groups (snake system), group schedule, qualification, bracket, placing
│   │   ├── BracketController.java          # Knockout bracket display model
│   │   ├── WeightedMatching.java           # Maximum weight matching (Edmonds' blossom algorithm)
│   │   ├── ScoreCalculator.java            # Points, sets, balls, Buchholz, Fein-Buchholz
│   │   ├── Standings.java                  # Ranking incl. ITTF direct comparison and shared ranks
│   │   ├── GroupTables.java                # Group tables (result matrix, place, qualifiers)
│   │   ├── TournamentStorage.java          # Atomic save/load with deserialization filter
│   │   ├── ResultEntryController.java      # Result entry incl. earlier rounds, unsaved-change check
│   │   ├── MatchManagerController.java     # Manual pairing changes (Swiss)
│   │   ├── CrossTableController.java       # Cross table, refresh and compact print
│   │   ├── RefereeSheetsController.java    # Referee sheets (6 per page, fixed margins, names fitted, stage per match)
│   │   ├── PlayerDialog.java               # Add/edit player dialog, duplicate name check
│   │   ├── PrintUtil.java                  # Table printing, MessageFormat escaping
│   │   └── ErrorHandler.java               # Error dialog and log file for uncaught exceptions
│   ├── model/
│   │   ├── Player.java                     # Participant: single player or doubles pair, with stats
│   │   ├── Match.java                      # Match with set results, round, float and group/knockout information
│   │   ├── GroupKoState.java               # Group/knockout settings, groups and bracket
│   │   ├── ResultRules.java                # Table tennis set/match rules
│   │   └── TournamentState.java            # Serializable snapshot of tournament state
│   ├── simulation/
│   │   └── TournamentSimulation.java       # GUI-free simulation; drives PairingEngine directly
│   └── view/                               # Swing views (incl. BracketView, GroupTablesPanel), UITheme, WrapLayout
├── main/resources/icons/                   # Window icon
packaging/                                  # IconGenerator and icons for the native packages
.github/workflows/release.yml               # Builds packages for all systems on version tags
└── test/java/                              # JUnit 5 tests (see below)
```

---

## Key Rules

### Result rule (`Match.isDecided()`)
A match counts if its set results form a complete best-of-five match (valid sets: at least 11 points and a lead of 2; no gaps; no set after the decision). Without set entries a decided overall result ("3:1") counts. The standings, the cross table, the pairing status and the round check all use this rule.

### Swiss System pairing
Each round is paired as a whole by a minimum-cost perfect matching without rematches. Cost levels: score differences, float history of the previous round, float history two rounds ago, who floats, upper half against lower half within a score group. The bye goes to the lowest-ranked player without a previous bye. The seeding order (TTR, ties by lot) is the last tie-break everywhere.

### Group stage + knockout stage
- Fixed maximum group size; snake distribution by seeding; round robin in every group
- Qualifiers: first n of every group plus the x best on place n+1 (per-match comparison)
- Bracket: group winners before runners-up before further qualifiers (TTR within each level), byes for the best seeds, same-group participants as far apart as possible
- Group tables instead of a ranking list: fixed group order, result matrix, games, sets, balls, points, place; qualifiers highlighted (provisional during the group stage); printed one group per block
- Withdrawal: group stage strikes all group matches (`PairingEngine.annulledPlayers()`); knockout stage withdrawals are recorded in `GroupKoState.knockoutWithdrawn` (empty in older files, which fall back to the bracket) and keep the group results, also after the knockout draw is taken back, the opponent advances by walkover (also final and match for third place); withdrawal dialog in the knockout stage lists only participants of the running round
- A round consisting of byes only is kept and does not end the tournament
- Bracket separation: swaps within a seeding level first, across levels only if the groups are separated strictly better; byes never move (always the best seeds)
- Final placing: a participant who withdraws in the knockout stage loses his next match by walkover and gets that round's place, whether the match was drawn before (result "kampflos") or after (bye for the opponent; `GroupKnockout.loserOf` derives the loser from the previous round); a single remaining qualifier wins the final by walkover
- Knockout best of five; match for third place optional (`GroupKoState.noThirdPlaceMatch`, stored negated so older files keep the match)

### Doubles
- A pair is one `Player` with partner fields; seeding by the sum of both TTR values; a person may only play in one pair

### Bye (Freilos)
- Swiss: 1 point, no win, no sets or balls; counts as a virtual opponent for Buchholz with the player's own points, capped at half the number of rounds counted so far ("Mr. 50%", direction of the FIDE C.07 revision 2026), and the player's own Buchholz for Fein-Buchholz (also rounds without pairing after late entry or withdrawal)
- Round Robin: "spielfrei", 0 points
- Each player receives at most one bye in the Swiss System

### Walkover (kampflos)
- `Match.isWalkover()`: exactly three sets 11:0 for the same player (what the "Kampflos" button enters)
- Scored like a normal win (points, sets, balls); for Buchholz/Fein-Buchholz unplayed: both players count a virtual opponent with the lower score (Buchholz) of the two
- Buchholz values are `double` (`Player.buchholzScore`, renamed from the former int field so that old files still load); displayed via `Standings.formatScore` ("12,5")

### Ranking
- Swiss: points, Buchholz, Fein-Buchholz, set difference, ball difference
- Round Robin: points, ITTF direct comparison among tied players (wins, sets, balls; recomputed for remaining tied subgroups), then set and ball difference
- Players equal in all criteria share a rank

---

## Persistence

- Auto-save right after the first draw, after every result entry, pairing change, round change and on close
- Starting a new tournament whose `<name>.ser` exists asks first; loading another file (round backup, other folder) while `<name>.ser` exists asks first, because saving continues in `<name>.ser`
- `<name>.ser` holds the current state; `<name>_Runde_N.ser` is a backup after completed round N
- Files are stored in the user's documents folder (as reported by the OS), otherwise in the home directory
- Writes are atomic; loading accepts only JDK base/collection classes and the model
- Saved states can be restored via **"Turnier laden"**

---

## Dependencies

| Dependency | Version | Scope |
|-----------|---------|-------|
| `org.junit.jupiter:junit-jupiter` | 5.12.0 | Test |

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

**Requirements**: Java 25

---

## Tests

| Test Class | Covers |
|-----------|--------|
| `PlayerTest`, `MatchTest` | Model classes, defensive copies |
| `ResultRulesTest` | Set and match validation |
| `ScoreCalculatorTest`, `ScoreCalculatorRulesTest` | Points, sets, balls, Buchholz, Fein-Buchholz, only decided matches count |
| `UnplayedTieBreakTest` | Walkover detection and scoring, bye cap at half the rounds, half points |
| `PairingEngineTest`, `PairingEngineRegressionTest` | Swiss and Round Robin pairing, whole simulated tournaments (no rematches, no double byes), manual changes |
| `SwissClassicPairingTest`, `ClassicByeAndFloatTest` | Upper vs. lower half, floaters, float history, bye scoring |
| `controller.WeightedMatchingTest` | Matching compared with brute force on random graphs |
| `controller.StandingsAndRoundFeaturesTest`, `controller.ReviewFixesTest` | Ranking, direct comparison, withdrawal, undo, seeding, helpers |
| `controller.GroupKnockoutTest`, `controller.DoublesAndGroupUiTest` | Groups, bracket, whole group/KO tournaments, third place option, doubles, saving |
| `controller.GroupTablesTest` | Group tables: result matrix, place, qualifiers, withdrawal, panel and printing |
| `controller.ReviewRound2Test` | Bye-only rounds, withdrawal in the knockout stage, ranks without withdrawn players, bracket byes, manual pairing, late entry, locked knockout results, referee sheets |
| `view.ResultEntryHeaderTest` | Result entry headers for single players and pairs |
| `controller.TournamentStorageTest`, `controller.ControllerHelpersTest` | Saving/loading, file names, escaping, duplicate names |
| `TournamentStateTest`, `TournamentStateRoundtripTest` | State record and serialization |
| `view.ResultEntryViewTest` | Walkover entry |
