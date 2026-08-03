package com.silentclassroom.minigame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.silentclassroom.Sfx;
import com.silentclassroom.SilentClassroomGame;

/**
 * KERNEL PANIC — redesigned for 3D immersion.
 * Rendered as a CRT terminal monitor in first-person.
 * 4 lanes, glitch tokens fall, select lane with Q/W/E/R, SPACE to fix.
 * Win: fix 20 tokens. Lose: 3 misses.
 */
public class KernelPanicScreen implements Screen {

    private static final int LANES      = 4;
    private static final int WIN_FIXES  = 20;
    private static final int MAX_MISSES = 3;
    private static final float LANE_W   = 160f;
    private static final float TOKEN_H  = 44f;
    private static final float TOKEN_W  = 130f;
    private static final float[] LANE_SPEEDS = {100f, 130f, 120f, 150f};

    private final SilentClassroomGame game;
    private final int roomId;
    private final ShapeRenderer shape;
    private final ScreenViewport vp;

    // Game state
    private int selectedLane = 0;
    private int fixes  = 0;
    private int misses = 0;
    private boolean finished = false;
    private boolean won = false;
    private float finishTimer = 0f;
    private float totalTime = 0f;
    private float scanLine = 0f;

    // Tokens per lane
    private float[] tokenY    = new float[LANES];
    private boolean[] tokenActive = new boolean[LANES];
    private int[] tokenType   = new int[LANES]; // 0=regular,1=boss
    private float[] spawnTimer = new float[LANES];
    private float spawnInterval = 1.8f;

    // Combo
    private int combo = 0;
    private float comboDisplayTimer = 0f;

    // Particles
    private static final int MAX_PARTICLES = 40;
    private float[] pX = new float[MAX_PARTICLES];
    private float[] pY = new float[MAX_PARTICLES];
    private float[] pVX = new float[MAX_PARTICLES];
    private float[] pVY = new float[MAX_PARTICLES];
    private float[] pLife = new float[MAX_PARTICLES];
    private float[] pR = new float[MAX_PARTICLES];
    private float[] pG = new float[MAX_PARTICLES];
    private int pHead = 0;

    // Glitch overlay
    private float glitchTimer = 0f;
    private float[] glitchX = new float[6];
    private float[] glitchY = new float[6];
    private float[] glitchW = new float[6];

    private static final String[] LANE_KEYS   = {"Q", "W", "E", "R"};
    private static final String[] TOKEN_CODES = {
        "SEGFAULT", "NULL_REF", "OVERFLOW", "DEADLOCK",
        "RACE_CON", "MEMLEAK ", "STACKOVF", "BAD_ALLC"
    };

    public KernelPanicScreen(SilentClassroomGame game, int roomId) {
        this.game   = game;
        this.roomId = roomId;
        this.shape  = new ShapeRenderer();
        this.vp     = new ScreenViewport();

        // Stagger initial spawns
        for (int i = 0; i < LANES; i++) {
            spawnTimer[i] = i * 0.5f;
            tokenActive[i] = false;
        }
    }

    @Override
    public void render(float delta) {
        // Global 9-minute run timer keeps ticking during mini-games
        game.session.update(delta);
        if (game.session.isGameOver()) { game.toGameOver(false); return; }
        totalTime += delta;
        scanLine = (scanLine + delta * 80f) % Gdx.graphics.getHeight();

        if (!finished) {
            updateGame(delta);
        } else {
            finishTimer += delta;
            if (finishTimer > 2.5f || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                endGame();
                return;
            }
        }

        updateParticles(delta);
        updateGlitch(delta);

        vp.apply();
        int W = Gdx.graphics.getWidth();
        int H = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.02f, 0.03f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shape.setProjectionMatrix(vp.getCamera().combined);

        drawBackground(W, H);
        drawMonitorFrame(W, H);
        drawLanes(W, H);
        drawTokens(W, H);
        drawParticles();
        drawGlitchOverlay(W, H);
        drawScanline(W, H);
        drawHUD(W, H);
        if (finished) drawResult(W, H);
    }

    // ─────────────────────────── UPDATE ──────────────────────────────────────

    private void updateGame(float delta) {
        // Speed ramp: increase every 4 fixes
        spawnInterval = Math.max(0.7f, 1.8f - fixes * 0.045f);

        for (int i = 0; i < LANES; i++) {
            if (!tokenActive[i]) {
                spawnTimer[i] -= delta;
                if (spawnTimer[i] <= 0f) {
                    spawnToken(i);
                    spawnTimer[i] = spawnInterval + (float)(Math.random() * 0.4);
                }
            } else {
                // Drop token
                tokenY[i] -= (LANE_SPEEDS[i] + fixes * 3f) * delta;
                if (tokenY[i] < 100f) {
                    // Missed!
                    misses++;
                    combo = 0;
                    Sfx.breach();
                    emitParticles(laneCenter(i, Gdx.graphics.getWidth()), 140f, 1f, 0.1f, 0.1f, 8);
                    tokenActive[i] = false;
                    if (misses >= MAX_MISSES) { finished = true; won = false; Sfx.miniGameLose(); }
                }
            }
        }

        // Input
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) selectedLane = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.W)) selectedLane = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) selectedLane = 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) selectedLane = 3;

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            fixToken(selectedLane);
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            endGame(); // Give up without win
        }
    }

    private void spawnToken(int lane) {
        tokenActive[lane] = true;
        tokenY[lane] = Gdx.graphics.getHeight() - 60f;
        tokenType[lane] = (fixes > 0 && fixes % 6 == 0) ? 1 : 0; // boss every 6 fixes
    }

    private void fixToken(int lane) {
        if (tokenActive[lane]) {
            Sfx.hit(combo + 1);
            combo++;
            comboDisplayTimer = 1.5f;
            int pts = 100 * combo + (tokenType[lane] == 1 ? 200 : 0);
            game.session.score += pts;
            fixes++;
            emitParticles(laneCenter(lane, Gdx.graphics.getWidth()),
                tokenY[lane] + TOKEN_H / 2, 0.1f, 1f, 0.3f, 12);
            tokenActive[lane] = false;
            if (fixes >= WIN_FIXES) { finished = true; won = true; Sfx.miniGameWin(); }
        } else {
            Sfx.wrongFix();
        }
    }

    private float laneCenter(int lane, int W) {
        float laneArea = LANES * LANE_W;
        float startX = (W - laneArea) / 2f;
        return startX + lane * LANE_W + LANE_W / 2f;
    }

    // ─────────────────────────── PARTICLES ───────────────────────────────────

    private void emitParticles(float x, float y, float r, float g, float b, int count) {
        for (int i = 0; i < count; i++) {
            int idx = pHead % MAX_PARTICLES;
            pX[idx] = x; pY[idx] = y;
            double ang = Math.random() * Math.PI * 2;
            float spd = 40f + (float)(Math.random() * 80f);
            pVX[idx] = (float)(Math.cos(ang) * spd);
            pVY[idx] = (float)(Math.sin(ang) * spd);
            pLife[idx] = 0.6f + (float)(Math.random() * 0.4f);
            pR[idx] = r; pG[idx] = g;
            pHead++;
        }
    }

    private void updateParticles(float delta) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (pLife[i] > 0f) {
                pX[i] += pVX[i] * delta;
                pY[i] += pVY[i] * delta;
                pVY[i] -= 60f * delta;
                pLife[i] -= delta;
            }
        }
    }

    private void updateGlitch(float delta) {
        glitchTimer -= delta;
        if (glitchTimer <= 0f) {
            glitchTimer = 0.5f + (float)(Math.random() * 1.5f);
            for (int i = 0; i < glitchX.length; i++) {
                glitchX[i] = (float)(Math.random() * Gdx.graphics.getWidth());
                glitchY[i] = (float)(Math.random() * Gdx.graphics.getHeight());
                glitchW[i] = 20f + (float)(Math.random() * 100f);
            }
        }
    }

    // ─────────────────────────── DRAW ────────────────────────────────────────

    private void drawBackground(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        // Dark green phosphor background
        shape.setColor(0.02f, 0.04f, 0.02f, 1f);
        shape.rect(0, 0, W, H);
        // Grid
        shape.end();
        shape.begin(ShapeRenderer.ShapeType.Line);
        for (int x = 0; x < W; x += 40) {
            shape.setColor(0f, 0.05f, 0f, 1f);
            shape.line(x, 0, x, H);
        }
        for (int y = 0; y < H; y += 40) {
            shape.setColor(0f, 0.05f, 0f, 1f);
            shape.line(0, y, W, y);
        }
        shape.end();
    }

    private void drawMonitorFrame(int W, int H) {
        // Monitor bezel
        float bx = (W - LANES * LANE_W - 40) / 2f;
        float by = 80f;
        float bw = LANES * LANE_W + 40;
        float bh = H - 180f;

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.1f, 0.1f, 0.08f, 1f);
        shape.rect(bx - 20, by - 20, bw + 40, bh + 40);
        shape.setColor(0.0f, 0.07f, 0.02f, 1f);
        shape.rect(bx, by, bw, bh);
        shape.end();

        // Corner screws
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.3f, 0.3f, 0.28f, 1f);
        float[] corners = {bx-14, by-14, bx+bw+6, by-14, bx-14, by+bh+6, bx+bw+6, by+bh+6};
        for (int i = 0; i < 4; i++) {
            shape.circle(corners[i*2], corners[i*2+1], 5, 8);
        }
        shape.end();
    }

    private void drawLanes(int W, int H) {
        float laneArea = LANES * LANE_W;
        float startX = (W - laneArea) / 2f;

        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < LANES; i++) {
            float lx = startX + i * LANE_W;
            boolean sel = (i == selectedLane);
            shape.setColor(sel ? 0.04f : 0.02f, sel ? 0.1f : 0.04f, sel ? 0.04f : 0.02f, 1f);
            shape.rect(lx + 4, 100, LANE_W - 8, H - 210);
        }
        shape.end();

        // Lane dividers
        shape.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i <= LANES; i++) {
            float lx = startX + i * LANE_W;
            shape.setColor(0f, 0.25f, 0.08f, 1f);
            shape.line(lx + 4, 100, lx + 4, H - 110);
        }
        shape.end();

        // Key labels at bottom
        game.batch.begin();
        for (int i = 0; i < LANES; i++) {
            float lx = startX + i * LANE_W + LANE_W / 2f - 10;
            boolean sel = i == selectedLane;
            game.bigFont.setColor(sel ? 0f : 0f, sel ? 1f : 0.5f, sel ? 0.4f : 0.2f, 1f);
            game.bigFont.draw(game.batch, "[" + LANE_KEYS[i] + "]", lx - 8, 110);
        }
        game.batch.end();

        // Catch zone bar
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0f, 0.35f, 0.1f, 0.7f);
        shape.rect(startX + 4, 100, laneArea - 8, 48);
        shape.end();
        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setColor(0f, 1f, 0.3f, 0.8f);
        shape.line(startX, 148, startX + laneArea, 148);
        shape.end();
    }

    private void drawTokens(int W, int H) {
        float startX = (W - LANES * LANE_W) / 2f;
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < LANES; i++) {
            if (!tokenActive[i]) continue;
            float lx = startX + i * LANE_W + (LANE_W - TOKEN_W) / 2f;
            boolean boss = tokenType[i] == 1;

            // Glow bg
            shape.setColor(boss ? 0.5f : 0f, boss ? 0.1f : 0.6f, boss ? 0.1f : 0.1f, 0.3f);
            shape.rect(lx - 6, tokenY[i] - 6, TOKEN_W + 12, TOKEN_H + 12);

            // Body
            shape.setColor(boss ? 0.6f : 0.05f, boss ? 0.1f : 0.5f, boss ? 0.1f : 0.05f, 1f);
            shape.rect(lx, tokenY[i], TOKEN_W, TOKEN_H);

            // Top border
            shape.setColor(boss ? 1f : 0f, boss ? 0.3f : 1f, 0.2f, 1f);
            shape.rect(lx, tokenY[i] + TOKEN_H - 3, TOKEN_W, 3);
        }
        shape.end();

        // Token text
        game.batch.begin();
        for (int i = 0; i < LANES; i++) {
            if (!tokenActive[i]) continue;
            float lx = (W - LANES * LANE_W) / 2f + i * LANE_W + 5f;
            boolean boss = tokenType[i] == 1;
            game.font.setColor(boss ? 1f : 0.1f, boss ? 0.4f : 1f, 0.2f, 1f);
            String code = boss ? "!BOSS!" : TOKEN_CODES[(i + fixes) % TOKEN_CODES.length];
            game.font.draw(game.batch, code, lx + 4, tokenY[i] + TOKEN_H - 8);
            game.font.setColor(0f, 0.7f, 0.3f, 0.8f);
            game.font.draw(game.batch, "FIX[SPC]", lx + 4, tokenY[i] + 16);
        }
        game.batch.end();
    }

    private void drawParticles() {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (pLife[i] > 0f) {
                shape.setColor(pR[i], pG[i], 0.1f, pLife[i]);
                shape.rect(pX[i], pY[i], 4, 4);
            }
        }
        shape.end();
    }

    private void drawGlitchOverlay(int W, int H) {
        if (misses == 0) return;
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < glitchX.length; i++) {
            shape.setColor(0f, 0.3f + (float)Math.random()*0.3f, 0.1f, 0.15f);
            shape.rect(glitchX[i], glitchY[i], glitchW[i], 3f);
        }
        shape.end();
    }

    private void drawScanline(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.4f, 0.9f, 0.4f, 0.06f);
        shape.rect(0, scanLine, W, 3);
        // CRT curvature lines (horizontal banding)
        for (int y = 0; y < H; y += 3) {
            shape.setColor(0f, 0f, 0f, 0.04f);
            shape.rect(0, y, W, 1);
        }
        shape.end();
    }

    private void drawHUD(int W, int H) {
        // Top HUD
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.04f, 0.08f, 0.04f, 0.95f);
        shape.rect(0, H - 72, W, 72);
        shape.end();

        // Miss indicators
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < MAX_MISSES; i++) {
            shape.setColor(i < misses ? new Color(1f, 0.2f, 0.1f, 1f) : new Color(0.1f, 0.3f, 0.1f, 1f));
            shape.circle(30 + i * 30, H - 36, 10, 12);
        }
        shape.end();

        game.batch.begin();
        // Title
        game.bigFont.setColor(0f, 1f, 0.3f, 1f);
        game.bigFont.draw(game.batch, "KERNEL PANIC", W / 2f - 140, H - 10);

        // Fix count
        game.font.setColor(0f, 0.8f, 0.3f, 1f);
        game.font.draw(game.batch, "FIXES: " + fixes + "/" + WIN_FIXES, W / 2f + 60, H - 10);

        // Combo
        comboDisplayTimer = Math.max(0f, comboDisplayTimer - Gdx.graphics.getDeltaTime());
        if (combo > 1 && comboDisplayTimer > 0f) {
            game.font.setColor(1f, 0.9f, 0.1f, comboDisplayTimer);
            game.font.draw(game.batch, "COMBO x" + combo + "!", W / 2f + 80, H - 40);
        }

        // Instruction
        game.font.setColor(0f, 0.6f, 0.25f, 0.8f);
        game.font.draw(game.batch, "Q/W/E/R select lane  SPACE fix  ESC quit", W/2f - 220, 28);
        game.batch.end();
    }

    private void drawResult(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(won ? 0f : 0.15f, won ? 0.08f : 0f, 0f, 0.85f);
        shape.rect(W/2f - 250, H/2f - 80, 500, 160);
        shape.end();

        game.batch.begin();
        if (won) {
            game.bigFont.setColor(0f, 1f, 0.4f, 1f);
            game.bigFont.draw(game.batch, "KERNEL PANIC — FIXED!", W/2f - 250, H/2f + 60);
            game.font.setColor(0.8f, 0.9f, 0.8f, 1f);
            game.font.draw(game.batch, "Glitch token secured. Token " + (game.session.tokensFound + 1) + " / 3", W/2f - 180, H/2f + 10);
        } else {
            game.bigFont.setColor(1f, 0.2f, 0.2f, 1f);
            game.bigFont.draw(game.batch, "SYSTEM FAILURE", W/2f - 180, H/2f + 60);
        }
        game.font.setColor(0.6f, 0.8f, 0.6f, 0.9f);
        game.font.draw(game.batch, "Press ENTER to continue...", W/2f - 150, H/2f - 40);
        game.batch.end();
    }

    private void endGame() {
        if (won) game.session.onMiniGameComplete(0, 350 + fixes * 15);
        game.toRoom(roomId);
    }

    @Override public void show() {}
    @Override public void resize(int w, int h) { vp.update(w, h, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() { shape.dispose(); }
}
