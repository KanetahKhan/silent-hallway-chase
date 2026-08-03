---
name: Java game build
description: How the Silent Classroom LibGDX desktop game builds and why it can't be previewed in Replit
---

- Build: `cd java-game && ./gradlew :lwjgl3:jar` → fat JAR at `java-game/lwjgl3/build/libs/silent-classroom.jar` (~14MB). A "Build Silent Classroom" console workflow exists for this.
- **Why no preview:** LWJGL3 needs a real OpenGL display; Replit has none, so the user downloads the JAR and runs `java -jar` locally (Java 11+).
- **Gotcha:** the lwjgl3 fat-jar task must `dependsOn ':core:jar'` or it fails with "Cannot expand ZIP core-1.0.0.jar does not exist".
- **Gotcha:** gradle wrapper must live at `java-game/gradle/wrapper/` (a stray copy exists at `java-game/wrapper/`).
- **Native crash lesson:** a user hit EXCEPTION_ACCESS_VIOLATION in BufferUtils.copyJni via ShapeRenderer.end on the menu (~11s in). No unbounded collections existed; fix applied was explicit large capacity `new ShapeRenderer(20000)` on every screen. If shapes-per-frame ever grow, keep collections capped — default capacity is only 5000 vertices.
- Design rule from review: LibGDX `setScreen` does NOT dispose the old screen — the game router (`SilentClassroomGame.switchScreen`) disposes it; keep using that helper for new screens. Room search state persists in `GameSession.roomSearchCount`/`miniGameFoundInRoom` so failed mini-games can be retried (all 3 are required to win).
