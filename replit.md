# Silent Classroom

A 3D stealth/escape game in Java (LibGDX). You play as Ayan navigating a 7-room school building — 4 classrooms and 3 labs — while an AI sentinel robot chases you. Find 3 hidden mini-games across the rooms, complete them to collect Glitch Tokens, then escape.

## Run & Build

**Build the JAR:**
```bash
cd java-game && ./gradlew :lwjgl3:jar
```
Output: `java-game/lwjgl3/build/libs/silent-classroom.jar`

**Run locally (requires Java 11+):**
```bash
java -jar java-game/lwjgl3/build/libs/silent-classroom.jar
```

## Game Stack

- **Engine**: LibGDX 1.14.2 + LWJGL3 (desktop)
- **Language**: Java 11+ (compiled with GraalVM 22.3)
- **3D Rendering**: LibGDX ModelBatch + ModelBuilder (procedural geometry)
- **Build**: Gradle 9.6.1 (Kotlin/Groovy DSL)

## Game Architecture

### Screens
- `MainMenuScreen` — animated grid title screen
- `HallwayScreen` — **top-down 3D** corridor: 7 doors, WASD movement, robot AI
- `RoomScreen` — **first-person 3D**: furniture search, hide mechanic, mouse look
- `KernelPanicScreen` — CRT terminal mini-game (Kernel Panic, 4 lanes, Q/W/E/R/SPACE)
- `CircuitBreakerScreen` — holographic circuit puzzle (arrow keys + SPACE)
- `SilentCodeScreen` — holographic code-block sorting puzzle (arrow keys + ENTER)
- `GameOverScreen` — score, grade, win/lose result

### Game Logic (java-game/core/src/main/java/com/silentclassroom/game/)
- `GameSession.java` — 9-minute countdown, score, HP (3 lives), token tracking, mini-game assignment
- `RobotAI.java` — FSM sentinel: PATROL → ALERT → CHASE → SEARCH
- `Room.java` — room metadata, furniture slots, mini-game assignment

### Mini-Games (redesigned for 3D immersion)
1. **Kernel Panic** — falling glitch tokens in 4 lanes on a CRT terminal. Select lane Q/W/E/R, fix with SPACE. Win: 20 fixes.
2. **Circuit Breaker** — neon circuit-board path-tracing puzzle. Arrow keys move cursor, SPACE draw wire. Win: trace correct path.
3. **Silent Code** — holographic floating code blocks scrambled. Arrow keys + ENTER swap blocks into correct logical order.

### Controls
**Hallway**:  WASD move, SHIFT sprint, E enter room, F toggle hide

**Room**: WASD+mouse look, E search furniture, F hide under desk, ESC exit room

**Mini-games**: Game-specific (shown on HUD), ESC to quit

## Where things live

```
java-game/
├── core/src/main/java/com/silentclassroom/
│   ├── SilentClassroomGame.java         # main Game class, screen router
│   ├── game/GameSession.java            # timer, score, state (from SilentClassroomSession)
│   ├── game/RobotAI.java                # FSM AI (from SentinelController)
│   ├── game/Room.java                   # room metadata
│   ├── screen/HallwayScreen.java        # top-down 3D hallway (main screen)
│   ├── screen/RoomScreen.java           # first-person 3D room
│   ├── screen/MainMenuScreen.java
│   ├── screen/GameOverScreen.java
│   ├── minigame/KernelPanicScreen.java
│   ├── minigame/CircuitBreakerScreen.java
│   └── minigame/SilentCodeScreen.java
└── lwjgl3/                              # desktop launcher
```

## Architecture Decisions

- **Procedural geometry**: No external 3D assets. All models built from `ModelBuilder.createBox()` with materials/colours for reliable cross-platform builds.
- **Screen-per-state**: Each major state is a LibGDX Screen. Transition is a simple `game.setScreen(new XxxScreen(game))`.
- **Shared session**: `GameSession` lives on `SilentClassroomGame` and is passed by reference — all screens share the same mutable state.
- **2D HUD over 3D**: `SpriteBatch`/`ShapeRenderer` drawn after the `ModelBatch` pass for all UI elements.
- **Java 11 target**: Compiled to Java 11 bytecode for broadest compatibility, even though GraalVM 22.3 (Java 19) is the runtime.

## User preferences

- Game backend must be Java
- Mini-games redesigned for 3D immersion (not reused from 2D FXGL version)
- Hallway: top-down 3D view; Rooms: first-person 3D view
- 7 rooms: 4 classrooms + 3 labs
- 3 randomly assigned mini-games hidden per run
- Robot AI chases player; player can hide under furniture
- 9-minute timer (from SilentClassroomSession)

## Pointers

- Original 2D game (FXGL/JavaFX): `extracted/vp_game/vp_game/override-game/`
- 3D conversion design docs: `extracted/convert_3d/` (HTML + Three.js prototype)
- Existing game assets/sprites: `extracted/vp_game/vp_game/override-game/src/main/resources/`
