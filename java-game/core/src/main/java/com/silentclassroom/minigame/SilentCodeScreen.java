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
 * SILENT CODE — holographic code-block sorting puzzle.
 * 5 code fragments are scrambled; player must arrange them in logical order.
 * Arrow keys move cursor, ENTER to select/place, R to reset.
 * Aesthetic: neon-blue holographic display.
 */
public class SilentCodeScreen implements Screen {

    private static final int BLOCK_COUNT = 5;
    private static final float BLOCK_W = 480f;
    private static final float BLOCK_H = 80f;
    private static final float BLOCK_GAP = 18f;
    private static final float TIME_LIMIT = 75f;

    private final SilentClassroomGame game;
    private final int roomId;
    private final ShapeRenderer shape;
    private final ScreenViewport vp;

    // Code blocks: label + correct order index
    private static final String[][] BLOCKS = {
        {"// 1. INITIALIZE OVERRIDE PROTOCOL",      "0"},
        {"    if (sentinelActive) { hide(); }",      "1"},
        {"    glitchToken = scan(room);",             "2"},
        {"    uploadToken(glitchToken, system);",     "3"},
        {"// 5. MISSION_COMPLETE: escape();",         "4"},
    };

    private final int[] arrangement; // arrangement[i] = block index displayed at position i
    private final String[][] displayBlocks;

    private int cursorPos = 0;       // highlighted slot
    private int selectedSlot = -1;   // slot currently grabbed (-1=none)

    private boolean finished = false;
    private boolean won = false;
    private float finishTimer = 0f;
    private float totalTime = 0f;
    private float pulseTime = 0f;
    private float timeUsed = 0f;
    private int attempts = 0;

    // Particle hologram sparks
    private static final int MAX_P = 80;
    private float[] pX = new float[MAX_P], pY = new float[MAX_P];
    private float[] pVX = new float[MAX_P], pVY = new float[MAX_P];
    private float[] pLife = new float[MAX_P];
    private int pHead = 0;

    // Glitch displacement per row
    private float[] glitchOffset = new float[BLOCK_COUNT];
    private float glitchTimer = 0f;

    // Wrong-answer flash
    private float wrongFlash = 0f;

    public SilentCodeScreen(SilentClassroomGame game, int roomId) {
        this.game   = game;
        this.roomId = roomId;
        this.shape  = new ShapeRenderer();
        this.vp     = new ScreenViewport();

        arrangement = new int[BLOCK_COUNT];
        displayBlocks = new String[BLOCK_COUNT][2];

        // Scramble initial arrangement (Fisher-Yates)
        for (int i = 0; i < BLOCK_COUNT; i++) arrangement[i] = i;
        for (int i = BLOCK_COUNT - 1; i > 0; i--) {
            int j = (int)(Math.random() * (i + 1));
            int tmp = arrangement[i]; arrangement[i] = arrangement[j]; arrangement[j] = tmp;
        }
        // Ensure it's not already sorted
        if (isSorted()) {
            int t = arrangement[0]; arrangement[0] = arrangement[1]; arrangement[1] = t;
        }
        rebuildDisplay();
    }

    private void rebuildDisplay() {
        for (int i = 0; i < BLOCK_COUNT; i++) {
            displayBlocks[i][0] = BLOCKS[arrangement[i]][0];
            displayBlocks[i][1] = BLOCKS[arrangement[i]][1];
        }
    }

    private boolean isSorted() {
        for (int i = 0; i < BLOCK_COUNT; i++) if (arrangement[i] != i) return false;
        return true;
    }

    @Override
    public void render(float delta) {
        // Global 9-minute run timer keeps ticking during mini-games
        game.session.update(delta);
        if (game.session.isGameOver()) { game.toGameOver(false); return; }
        totalTime += delta;
        pulseTime += delta;
        wrongFlash = Math.max(0f, wrongFlash - delta * 3f);

        if (!finished) {
            timeUsed += delta;
            if (timeUsed >= TIME_LIMIT) { finished = true; won = false; Sfx.miniGameLose(); }
            handleInput();
            updateGlitch(delta);
        } else {
            finishTimer += delta;
            if (finishTimer > 2.5f || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                endGame();
                return;
            }
        }

        updateParticles(delta);

        vp.apply();
        int W = Gdx.graphics.getWidth();
        int H = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.02f, 0.03f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shape.setProjectionMatrix(vp.getCamera().combined);

        drawBackground(W, H);
        drawHologramBezel(W, H);
        drawBlocks(W, H);
        drawParticles();
        drawHUD(W, H);
        if (finished) drawResult(W, H);
    }

    // ─────────────────────────── INPUT ───────────────────────────────────────

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP))   cursorPos = Math.max(0, cursorPos - 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) cursorPos = Math.min(BLOCK_COUNT - 1, cursorPos + 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.W))    cursorPos = Math.max(0, cursorPos - 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.S))    cursorPos = Math.min(BLOCK_COUNT - 1, cursorPos + 1);

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (selectedSlot < 0) {
                // Pick up block
                Sfx.blip();
                selectedSlot = cursorPos;
                emitSparks(cursorPos, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), 0.3f, 0.7f, 1f);
            } else {
                // Place block: swap selected with cursor
                int tmp = arrangement[selectedSlot];
                arrangement[selectedSlot] = arrangement[cursorPos];
                arrangement[cursorPos] = tmp;
                rebuildDisplay();
                emitSparks(cursorPos, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), 0.5f, 1f, 0.5f);
                selectedSlot = -1;
                // Check win
                if (isSorted()) { finished = true; won = true; Sfx.miniGameWin(); }
                else if (arrangement[cursorPos] == cursorPos) Sfx.hit(1);
                else Sfx.wrongFix();
            }
        }

        // ENTER to submit check (alternative)
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            selectedSlot = -1; // deselect
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            Sfx.breach();
            resetArrangement();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            endGame();
        }
    }

    private void resetArrangement() {
        for (int i = 0; i < BLOCK_COUNT; i++) arrangement[i] = i;
        // Re-scramble
        for (int i = BLOCK_COUNT-1; i > 0; i--) {
            int j = (int)(Math.random() * (i+1));
            int t = arrangement[i]; arrangement[i] = arrangement[j]; arrangement[j] = t;
        }
        if (isSorted()) { int t = arrangement[0]; arrangement[0] = arrangement[1]; arrangement[1] = t; }
        rebuildDisplay();
        selectedSlot = -1;
        attempts++;
    }

    // ─────────────────────────── PARTICLES ───────────────────────────────────

    private void emitSparks(int slot, int W, int H, float r, float g, float b) {
        float blockX = (W - BLOCK_W) / 2f;
        float totalH = BLOCK_COUNT * (BLOCK_H + BLOCK_GAP);
        float startY = H/2f + totalH/2f;
        float by = startY - slot * (BLOCK_H + BLOCK_GAP) - BLOCK_H;
        float cy = by + BLOCK_H/2f;
        float cx = blockX + BLOCK_W/2f;

        for (int i = 0; i < 12; i++) {
            int idx = pHead % MAX_P;
            pX[idx] = cx + (-BLOCK_W/2f + (float)(Math.random()*BLOCK_W));
            pY[idx] = cy;
            double ang = Math.random() * Math.PI * 2;
            float spd = 30f + (float)(Math.random() * 60f);
            pVX[idx] = (float)(Math.cos(ang) * spd);
            pVY[idx] = (float)(Math.sin(ang) * spd);
            pLife[idx] = 0.6f + (float)(Math.random() * 0.4f);
            pHead++;
        }
    }

    private void updateParticles(float delta) {
        for (int i = 0; i < MAX_P; i++) {
            if (pLife[i] > 0) { pX[i] += pVX[i]*delta; pY[i] += pVY[i]*delta; pLife[i] -= delta; }
        }
    }

    private void updateGlitch(float delta) {
        glitchTimer -= delta;
        if (glitchTimer <= 0f) {
            glitchTimer = 0.3f + (float)(Math.random() * 0.8f);
            for (int i = 0; i < BLOCK_COUNT; i++) {
                glitchOffset[i] = (float)(Math.random() < 0.15 ? (Math.random() * 8 - 4) : 0);
            }
        }
    }

    // ─────────────────────────── DRAW ────────────────────────────────────────

    private void drawBackground(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.02f, 0.03f, 0.06f, 1f);
        shape.rect(0, 0, W, H);
        shape.end();

        // Holographic scanlines
        shape.begin(ShapeRenderer.ShapeType.Filled);
        float puls = (float)(0.5 + 0.5*Math.sin(pulseTime*2));
        shape.setColor(0f, 0.1f, 0.3f * puls, 0.06f);
        for (int y = 0; y < H; y += 4) shape.rect(0, y, W, 2);
        shape.end();

        // Floating hex grid background
        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setColor(0.05f, 0.08f, 0.15f, 1f);
        for (int x = 0; x < W; x += 50) for (int y = 0; y < H; y += 44) {
            int col = x / 50;
            float yo = (col % 2 == 0) ? 0 : 22;
            shape.circle(x, y + yo, 20, 6);
        }
        shape.end();
    }

    private void drawHologramBezel(int W, int H) {
        float bx = (W - BLOCK_W - 80) / 2f;
        float totalH = BLOCK_COUNT * (BLOCK_H + BLOCK_GAP);
        float by = H/2f - totalH/2f - 70;
        float bw = BLOCK_W + 80;
        float bh = totalH + 140;

        // Outer frame glow
        shape.begin(ShapeRenderer.ShapeType.Filled);
        float glow = (float)(0.5 + 0.5*Math.sin(pulseTime * 3));
        shape.setColor(0f, 0.15f * glow, 0.4f * glow, 0.4f);
        shape.rect(bx-10, by-10, bw+20, bh+20);
        // Inner background
        shape.setColor(0.02f, 0.04f, 0.1f, 0.9f);
        shape.rect(bx, by, bw, bh);
        shape.end();

        // Frame lines
        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setColor(0.1f, 0.4f * glow, 1f * glow, 0.8f);
        shape.rect(bx, by, bw, bh);
        shape.setColor(0.05f, 0.2f, 0.5f, 0.5f);
        shape.rect(bx+4, by+4, bw-8, bh-8);
        shape.end();

        // Corner deco
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.3f, 0.7f, 1f, glow);
        float[][] corners = {{bx,by},{bx+bw,by},{bx,by+bh},{bx+bw,by+bh}};
        for (float[] cp : corners) shape.circle(cp[0], cp[1], 5, 8);
        shape.end();

        // Title above bezel
        game.batch.begin();
        game.bigFont.setColor(0.1f, 0.6f * glow, 1f * glow, 1f);
        game.bigFont.draw(game.batch, "SILENT CODE", W/2f - 130, by + bh + 48);
        game.font.setColor(0.3f, 0.6f, 0.9f, 0.8f);
        game.font.draw(game.batch, "Arrange code fragments in logical order to unlock the token", W/2f - 290, by + bh + 18);
        game.batch.end();
    }

    private void drawBlocks(int W, int H) {
        float blockX = (W - BLOCK_W) / 2f;
        float totalH = BLOCK_COUNT * (BLOCK_H + BLOCK_GAP);
        float startY = H/2f + totalH/2f;
        float pulse = (float)(0.7 + 0.3*Math.sin(pulseTime * 2));

        for (int i = 0; i < BLOCK_COUNT; i++) {
            float by = startY - i * (BLOCK_H + BLOCK_GAP) - BLOCK_H;
            float bx = blockX + glitchOffset[i];
            boolean isCursor   = (i == cursorPos);
            boolean isSelected = (i == selectedSlot);
            int correctIdx = Integer.parseInt(displayBlocks[i][1]);
            boolean isCorrect  = (arrangement[i] == i);

            // Block background
            Color bgCol;
            if (isSelected)       bgCol = new Color(0.1f, 0.3f, 0.6f, 0.95f);
            else if (isCursor)    bgCol = new Color(0.06f, 0.15f, 0.35f, 0.9f);
            else if (isCorrect)   bgCol = new Color(0.04f, 0.15f, 0.08f, 0.85f);
            else                  bgCol = new Color(0.03f, 0.07f, 0.15f, 0.85f);

            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(bgCol);
            shape.rect(bx, by, BLOCK_W, BLOCK_H);

            // Left accent bar (colour-coded by block's correct number)
            float[][] accColors = {{1f,0.3f,0.3f},{1f,0.6f,0.1f},{0.2f,0.9f,0.3f},{0.2f,0.5f,1f},{0.8f,0.2f,1f}};
            float[] ac = accColors[correctIdx % accColors.length];
            shape.setColor(ac[0], ac[1], ac[2], 0.9f);
            shape.rect(bx, by, 6, BLOCK_H);
            shape.end();

            // Border
            shape.begin(ShapeRenderer.ShapeType.Line);
            if (isSelected)     shape.setColor(0.2f, 0.8f, 1f, pulse);
            else if (isCursor)  shape.setColor(1f, 0.9f, 0.1f, pulse);
            else if (isCorrect) shape.setColor(0.1f, 1f, 0.3f, 0.6f);
            else                shape.setColor(0.1f, 0.3f, 0.7f, 0.5f);
            shape.rect(bx, by, BLOCK_W, BLOCK_H);
            shape.end();

            // Line number badge
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(ac[0]*0.4f, ac[1]*0.4f, ac[2]*0.4f, 1f);
            shape.rect(bx, by, 38, BLOCK_H);
            shape.end();

            // Code text
            game.batch.begin();
            // Line number
            game.font.setColor(ac[0], ac[1], ac[2], 1f);
            game.font.draw(game.batch, String.valueOf(correctIdx + 1), bx + 12, by + BLOCK_H - 10);
            // Code content
            if (isSelected)     game.font.setColor(0.5f, 1f, 1f, 1f);
            else if (isCursor)  game.font.setColor(1f, 0.98f, 0.7f, 1f);
            else if (isCorrect) game.font.setColor(0.4f, 1f, 0.5f, 1f);
            else                game.font.setColor(0.5f, 0.75f, 1f, 1f);
            game.font.draw(game.batch, displayBlocks[i][0], bx + 46, by + BLOCK_H - 10);

            // Status tag
            if (isSelected) {
                game.font.setColor(1f, 1f, 0.2f, 0.9f);
                game.font.draw(game.batch, "[HELD]", bx + BLOCK_W - 80, by + BLOCK_H - 10);
            } else if (isCorrect) {
                game.font.setColor(0.2f, 1f, 0.4f, 0.9f);
                game.font.draw(game.batch, "✓", bx + BLOCK_W - 30, by + BLOCK_H - 10);
            }
            game.batch.end();
        }

        // Sorted count indicator
        int sortedCount = 0;
        for (int i = 0; i < BLOCK_COUNT; i++) if (arrangement[i] == i) sortedCount++;
        float frac = (float)sortedCount / BLOCK_COUNT;

        float barX = (W - BLOCK_W) / 2f;
        float barY = startY - BLOCK_COUNT * (BLOCK_H + BLOCK_GAP) - 30;
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.05f, 0.05f, 0.1f, 0.8f);
        shape.rect(barX, barY, BLOCK_W, 10);
        shape.setColor(0.1f, 0.8f, 0.3f, 1f);
        shape.rect(barX, barY, BLOCK_W * frac, 10);
        shape.end();
    }

    private void drawParticles() {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < MAX_P; i++) {
            if (pLife[i] > 0) {
                shape.setColor(0.3f, 0.7f, 1f, pLife[i]);
                shape.circle(pX[i], pY[i], 3, 6);
            }
        }
        shape.end();
    }

    private void drawHUD(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        // Bottom bar
        shape.setColor(0.02f, 0.03f, 0.08f, 0.92f);
        shape.rect(0, 0, W, 52);
        // Time bar
        float timeFrac = 1f - (timeUsed / TIME_LIMIT);
        shape.setColor(timeFrac>0.4f ? 0.1f:1f, timeFrac>0.4f ? 0.7f:0.2f, 0.1f, 1f);
        shape.rect(0, 0, W * timeFrac, 4);
        shape.end();

        game.batch.begin();
        game.font.setColor(0.4f, 0.7f, 1f, 0.85f);
        game.font.draw(game.batch, "↑↓/WS move  ENTER grab/place  R reset  ESC quit", W/2f-240, 38);

        int secs = (int)(TIME_LIMIT - timeUsed);
        game.font.setColor(secs < 20 ? new Color(1f,0.3f,0.3f,1f) : new Color(0.6f,0.9f,1f,1f));
        game.font.draw(game.batch, "TIME: " + secs + "s", 16, 38);

        if (selectedSlot >= 0) {
            game.font.setColor(1f, 0.9f, 0.2f, 1f);
            game.font.draw(game.batch, "Holding block " + (selectedSlot+1) + " — navigate to new slot, press ENTER to place", W/2f-260, 14);
        } else {
            game.font.setColor(0.35f, 0.55f, 0.85f, 0.8f);
            game.font.draw(game.batch, "Select a block with ENTER, then move and place it in the correct order", W/2f-290, 14);
        }
        game.batch.end();
    }

    private void drawResult(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(won ? 0f : 0.1f, won ? 0.05f : 0f, won ? 0.12f : 0f, 0.9f);
        shape.rect(W/2f-280, H/2f-90, 560, 180);
        shape.end();
        game.batch.begin();
        if (won) {
            game.bigFont.setColor(0.1f, 0.7f, 1f, 1f);
            game.bigFont.draw(game.batch, "CODE SEQUENCE RESTORED!", W/2f-280, H/2f+60);
            game.font.setColor(0.6f, 0.9f, 1f, 1f);
            game.font.draw(game.batch, "Glitch token secured. Token " + (game.session.tokensFound+1) + " / 3", W/2f-200, H/2f+10);
        } else {
            game.bigFont.setColor(1f, 0.3f, 0.2f, 1f);
            game.bigFont.draw(game.batch, "COMPILATION ERROR", W/2f-220, H/2f+60);
        }
        game.font.setColor(0.4f, 0.75f, 1f, 0.9f);
        game.font.draw(game.batch, "Press ENTER to continue...", W/2f-160, H/2f-50);
        game.batch.end();
    }

    private void endGame() {
        if (won) game.session.onMiniGameComplete(2, 250 + (int)((TIME_LIMIT - timeUsed) * 4));
        game.toRoom(roomId);
    }

    @Override public void show() {}
    @Override public void resize(int w, int h) { vp.update(w, h, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() { shape.dispose(); }
}
