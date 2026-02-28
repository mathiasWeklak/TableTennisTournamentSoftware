---
description: Repository Information Overview
alwaysApply: true
---

# TableTennisTournamentSoftware Information

## Summary
A Java-based desktop application for managing table tennis tournaments. Supports the **Swiss System** and **Round Robin** modes with features for player management, draw/pairing, result entry, and referee sheets. Saves tournament state automatically as `.ser` files in the user's Documents folder.

## Structure
```
src/
  main/java/
    controller/   – Tournament logic, UI actions, save/load
    model/        – Data classes: Player, Match, TournamentState
    simulation/   – Simulation utilities
    view/         – Swing-based UI components
  main/resources/
  test/java/      – JUnit test files (MatchTest, PlayerTest, PairingEngineTest, ScoreCalculatorTest, TournamentStateTest)
  test/resources/
gradle/wrapper/   – Gradle wrapper configuration
build/            – Build output
```

## Language & Runtime
**Language**: Java  
**Version**: Java 25 (source & target compatibility), Java 25+ required at runtime  
**Build System**: Gradle 8.14  
**Package Manager**: Gradle (Maven Central for dependencies)

## Dependencies
**Main Dependencies**:
- Java Swing (JDK built-in) – UI framework

**Development / Test Dependencies**:
- `org.junit.jupiter:junit-jupiter:5.12.0` – JUnit 5 for testing

## Build & Installation
```bash
# Build the project and create executable JAR
./gradlew build

# Run the application directly
./gradlew run

# Create standalone executable JAR (output: build/libs/TableTennisTournamentSoftware.jar)
./gradlew createExecutableJar
```

**Main Entry Point**: `controller.TournamentController` (Gradle `run` task)  
**JAR Main Class**: `controller.TournamentController`

## Testing
**Framework**: JUnit Jupiter 5 (JUnit 5)  
**Test Location**: `src/test/java/`  
**Naming Convention**: `*Test.java` (e.g., `MatchTest.java`, `PlayerTest.java`)

**Run Command**:
```bash
./gradlew test
```
