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
 * CIRCUIT BREAKER — neon lab circuit puzzle.
 * Player traces a continuous path connecting all nodes on a grid.
 * Arrow keys move the wire cursor. SPACE/ENTER commits a segment.
 * Holographic neon-blue aesthetic.
 */
public class CircuitBreakerScreen implements Screen {

    private static final int COLS = 8;
    private static final int ROWS = 6;
    private static final float CELL_SIZE = 70f;

    private final SilentClassroomGame game;
    private final int roomId;
    private final ShapeRenderer shape;
    private final ScreenViewport vp;

    // Grid state
    private final boolean[][] nodeFixed = new boolean[COLS][ROWS]; // pre-placed nodes
    private final boolean[][] wireH = new boolean[COLS][ROWS];     // horizontal wire right from (c,r)
    private final boolean[][] wireV = new boolean[COLS][ROWS];     // vertical wire down from (c,r)
    private final int[][] nodeColor = new int[COLS][ROWS];          // 0=none,1=red,2=blue,3=green

    // Cursor
    private int cursorX = 0, cursorY = 0;

    // Path (sequence of nodes the player has connected)
    private int[] pathX = new int[COLS * ROWS + 1];
    private int[] pathY = new int[COLS * ROWS + 1];
    private int pathLen = 0;
    private boolean selecting = false; // currently drawing a path
    private int selStartX, selStartY;

    // Win condition: connect all numbered nodes in order
    private int[][] requiredPath; // [[x,y],...] ordered sequence to connect
    private int requiredLen;
    private int connectedCount = 0;

    // State
    private boolean finished = false;
    private boolean won = false;
    private float finishTimer = 0f;
    private float totalTime = 0f;
    private float pulseTime = 0f;
    private float timeLimit = 90f; // 90 second time limit for this puzzle
    private float timeUsed = 0f;

    // Particles for connected segments
    private static final int MAX_P = 60;
    private float[] pX = new float[MAX_P], pY = new float[MAX_P];
    private float[] pVX = new float[MAX_P], pVY = new float[MAX_P];
    private float[] pLife = new float[MAX_P];
    private int pHead = 0;

    public CircuitBreakerScreen(SilentClassroomGame game, int roomId) {
        this.game   = game;
        this.roomId = roomId;
        this.shape  = new ShapeRenderer(20000);
        this.vp     = new ScreenViewport();
        buildPuzzle();
    }

    private void buildPuzzle() {
        // Design a solvable circuit: a winding path through the grid.
        // Required path: snake through specific nodes
        int[][] path = {
            {1,1},{2,1},{3,1},{4,1},{5,1},{5,2},{5,3},{4,3},
            {3,3},{2,3},{2,4},{3,4},{4,4},{5,4},{6,4},{6,3},{6,2}
        };
        requiredPath = path;
        requiredLen  = path.length;

        // Mark start, end, and intermediate nodes
        for (int i = 0; i < requiredLen; i++) {
            int c = path[i][0], r = path[i][1];
            nodeFixed[c][r] = true;
            if      (i == 0)               nodeColor[c][r] = 1; // red = start
            else if (i == requiredLen - 1) nodeColor[c][r] = 3; // green = end
            else if (i % 3 == 0)           nodeColor[c][r] = 2; // blue = waypoints
        }

        // Cursor starts at the start node
        cursorX = path[0][0];
        cursorY = path[0][1];
    }

    @Override
    public void render(float delta) {
        // Global 9-minute run timer keeps ticking during mini-games
        game.session.update(delta);
        if (game.session.isGameOver()) {
            // A win already banked its token; route through endGame so the
            // player returns to the room (which handles game-over if it stands).
            if (finished && won) { endGame(); } else { game.toGameOver(false); }
            return;
        }
        totalTime += delta;
        pulseTime += delta;

        if (!finished) {
            timeUsed += delta;
            if (timeUsed >= timeLimit) { finished = true; won = false; Sfx.miniGameLose(); }
            handleInput();
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

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shape.setProjectionMatrix(vp.getCamera().combined);

        drawBackground(W, H);
        drawGrid(W, H);
        drawWires(W, H);
        drawNodes(W, H);
        drawCursor(W, H);
        drawParticles();
        drawHUD(W, H);
        if (finished) drawResult(W, H);
    }

    // ─────────────────────────── INPUT ───────────────────────────────────────

    private void handleInput() {
        int dx = 0, dy = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) dx = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT))  dx = -1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN))  dy = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP))    dy = -1;

        if (dx != 0 || dy != 0) {
            int nx = cursorX + dx;
            int ny = cursorY + dy;
            if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS) {
                // Place wire from old to new position
                if (selecting) {
                    placeWire(cursorX, cursorY, nx, ny, dx, dy);
                }
                cursorX = nx;
                cursorY = ny;
            }
        }

        // SPACE = toggle selection (start drawing path)
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (!selecting) {
                Sfx.blip();
                selecting = true;
                selStartX = cursorX;
                selStartY = cursorY;
                pathLen = 0;
                pathX[pathLen] = cursorX;
                pathY[pathLen] = cursorY;
                pathLen++;
            } else {
                selecting = false;
                boolean wasWon = checkWin();
                if (wasWon) Sfx.miniGameWin();
                else        Sfx.wrongFix();
            }
        }

        // R = reset all wires
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) { Sfx.breach(); resetWires(); }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { endGame(); }
    }

    private void placeWire(int fx, int fy, int tx, int ty, int dx, int dy) {
        if (dx == 1  && fx < COLS-1) { wireH[fx][fy] = true; emitWireParticles(fx, fy, true);  }
        if (dx == -1 && tx < COLS-1) { wireH[tx][ty] = true; emitWireParticles(tx, ty, true);  }
        if (dy == 1  && fy < ROWS-1) { wireV[fx][fy] = true; emitWireParticles(fx, fy, false); }
        if (dy == -1 && ty < ROWS-1) { wireV[tx][ty] = true; emitWireParticles(tx, ty, false); }
        if (pathLen < pathX.length) {
            pathX[pathLen] = tx;
            pathY[pathLen] = ty;
            pathLen++;
        }
    }

    private void resetWires() {
        for (int c = 0; c < COLS; c++) for (int r = 0; r < ROWS; r++) {
            wireH[c][r] = false; wireV[c][r] = false;
        }
        selecting = false; pathLen = 0; connectedCount = 0;
    }

    private boolean checkWin() {
        // Check if the drawn path visits all required nodes in order
        if (pathLen < requiredLen) return false;
        connectedCount = 0;
        int reqIdx = 0;
        for (int p = 0; p < pathLen && reqIdx < requiredLen; p++) {
            if (pathX[p] == requiredPath[reqIdx][0] && pathY[p] == requiredPath[reqIdx][1]) {
                reqIdx++;
                connectedCount++;
            }
        }
        if (reqIdx == requiredLen) {
            finished = true; won = true;
            // Credit the token immediately so a timer expiring during the
            // result screen can't discard a win (onMiniGameComplete is idempotent).
            game.session.onMiniGameComplete(1, 300 + (int)((timeLimit - timeUsed) * 5));
            return true;
        }
        return false;
    }

    // ─────────────────────────── PARTICLES ───────────────────────────────────

    private void emitWireParticles(int c, int r, boolean horiz) {
        float[] gc = gridCenter(c, r, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float px = gc[0] + (horiz ? CELL_SIZE/2 : 0);
        float py = gc[1] + (horiz ? 0 : -CELL_SIZE/2);
        for (int i = 0; i < 4; i++) {
            int idx = pHead % MAX_P;
            pX[idx] = px; pY[idx] = py;
            pVX[idx] = -20f + (float)(Math.random()*40);
            pVY[idx] = -20f + (float)(Math.random()*40);
            pLife[idx] = 0.5f;
            pHead++;
        }
    }

    private void updateParticles(float delta) {
        for (int i = 0; i < MAX_P; i++) {
            if (pLife[i] > 0) { pX[i] += pVX[i]*delta; pY[i] += pVY[i]*delta; pLife[i] -= delta; }
        }
    }

    // ─────────────────────────── DRAW ────────────────────────────────────────

    private float[] gridCenter(int c, int r, int W, int H) {
        float gw = COLS * CELL_SIZE;
        float gh = ROWS * CELL_SIZE;
        float ox = (W - gw) / 2f;
        float oy = (H - gh) / 2f + 20f;
        return new float[]{ox + c * CELL_SIZE + CELL_SIZE/2f, H - (oy + r * CELL_SIZE + CELL_SIZE/2f)};
    }

    private void drawBackground(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.02f, 0.02f, 0.06f, 1f);
        shape.rect(0, 0, W, H);
        shape.end();

        // Subtle radial-ish glow around grid center
        shape.begin(ShapeRenderer.ShapeType.Filled);
        float cx = W/2f, cy = H/2f + 20;
        float puls = (float)(0.5 + 0.5*Math.sin(pulseTime * 1.5));
        shape.setColor(0f, 0f, 0.12f * puls, 1f);
        shape.ellipse(cx - 300, cy - 200, 600, 400);
        shape.end();
    }

    private void drawGrid(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Line);
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                float[] gc = gridCenter(c, r, W, H);
                shape.setColor(0.08f, 0.1f, 0.2f, 1f);
                shape.rect(gc[0] - CELL_SIZE/2 + 2, gc[1] - CELL_SIZE/2 + 2, CELL_SIZE - 4, CELL_SIZE - 4);
            }
        }
        shape.end();
    }

    private void drawWires(int W, int H) {
        float pulse = (float)(0.7 + 0.3*Math.sin(pulseTime * 4));
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int c = 0; c < COLS; c++) for (int r = 0; r < ROWS; r++) {
            float[] gc = gridCenter(c, r, W, H);
            // Horizontal
            if (c < COLS-1 && wireH[c][r]) {
                float[] gc2 = gridCenter(c+1, r, W, H);
                shape.setColor(0.1f, 0.5f * pulse, 1f * pulse, 1f);
                shape.rectLine(gc[0], gc[1], gc2[0], gc2[1], 4);
                // Glow halo
                shape.setColor(0f, 0.2f, 0.5f, 0.3f);
                shape.rectLine(gc[0], gc[1], gc2[0], gc2[1], 10);
            }
            // Vertical
            if (r < ROWS-1 && wireV[c][r]) {
                float[] gc2 = gridCenter(c, r+1, W, H);
                shape.setColor(0.1f, 0.5f * pulse, 1f * pulse, 1f);
                shape.rectLine(gc[0], gc[1], gc2[0], gc2[1], 4);
                shape.setColor(0f, 0.2f, 0.5f, 0.3f);
                shape.rectLine(gc[0], gc[1], gc2[0], gc2[1], 10);
            }
        }
        shape.end();
    }

    private void drawNodes(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        float pulse = (float)(0.7 + 0.3*Math.sin(pulseTime * 2));

        for (int c = 0; c < COLS; c++) for (int r = 0; r < ROWS; r++) {
            if (!nodeFixed[c][r]) continue;
            float[] gc = gridCenter(c, r, W, H);
            int nc = nodeColor[c][r];

            // Glow
            float gr = nc==1?1f : nc==3?0.1f : 0.1f;
            float gg = nc==1?0.1f : nc==3?1f : 0.3f;
            float gb = nc==1?0.1f : nc==3?0.2f : 1f;
            shape.setColor(gr*0.3f, gg*0.3f, gb*0.3f, pulse);
            shape.circle(gc[0], gc[1], 20, 16);

            shape.setColor(gr, gg, gb, 1f);
            shape.circle(gc[0], gc[1], 12, 16);

            // Inner dot
            shape.setColor(1f, 1f, 1f, 0.9f);
            shape.circle(gc[0], gc[1], 4, 12);
        }
        shape.end();

        // Node labels (S/E/numbers)
        game.batch.begin();
        for (int c = 0; c < COLS; c++) for (int r = 0; r < ROWS; r++) {
            if (!nodeFixed[c][r]) continue;
            float[] gc = gridCenter(c, r, W, H);
            int nc = nodeColor[c][r];
            game.font.setColor(1f, 1f, 1f, 1f);
            String label = nc==1?"S" : nc==3?"E" : "+";
            game.font.draw(game.batch, label, gc[0] - 5, gc[1] + 6);
        }
        game.batch.end();
    }

    private void drawCursor(int W, int H) {
        float[] gc = gridCenter(cursorX, cursorY, W, H);
        float blink = (float)(0.5 + 0.5*Math.sin(pulseTime * 6));
        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setColor(1f, 1f, 0.2f * blink + 0.6f, blink);
        shape.rect(gc[0]-18, gc[1]-18, 36, 36);
        shape.setColor(1f, 1f, 0f, blink * 0.6f);
        shape.rect(gc[0]-22, gc[1]-22, 44, 44);
        shape.end();

        // Selection trail
        if (selecting && pathLen > 0) {
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(1f, 1f, 0f, 0.4f);
            for (int p = 0; p < pathLen; p++) {
                float[] pc = gridCenter(pathX[p], pathY[p], W, H);
                shape.circle(pc[0], pc[1], 5, 8);
            }
            shape.end();
        }
    }

    private void drawParticles() {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < MAX_P; i++) {
            if (pLife[i] > 0) {
                shape.setColor(0.2f, 0.6f, 1f, pLife[i] * 2f);
                shape.circle(pX[i], pY[i], 3, 6);
            }
        }
        shape.end();
    }

    private void drawHUD(int W, int H) {
        // Top bar
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.04f, 0.04f, 0.1f, 0.92f);
        shape.rect(0, H-70, W, 70);
        // Time bar
        float timeFrac = 1f - (timeUsed / timeLimit);
        shape.setColor(timeFrac > 0.5f ? 0.1f : 1f, timeFrac > 0.5f ? 0.8f : 0.3f, 0.1f, 1f);
        shape.rect(2, H-68, (W-4)*timeFrac, 16);
        // Bottom bar
        shape.setColor(0.03f, 0.03f, 0.09f, 0.9f);
        shape.rect(0, 0, W, 52);
        shape.end();

        game.batch.begin();
        game.bigFont.setColor(0.2f, 0.6f, 1f, 1f);
        game.bigFont.draw(game.batch, "CIRCUIT BREAKER", W/2f - 180, H - 12);

        // Connection progress
        game.font.setColor(0.5f, 0.8f, 1f, 1f);
        game.font.draw(game.batch, "Nodes: " + connectedCount + "/" + requiredLen, W/2f + 100, H - 12);

        int secs = (int)(timeLimit - timeUsed);
        game.font.setColor(secs < 20 ? new Color(1f,0.3f,0.3f,1f) : new Color(0.8f,0.9f,1f,1f));
        game.font.draw(game.batch, "TIME: " + secs + "s", W/2f - 220, H - 45);

        // Status
        game.font.setColor(selecting ? new Color(1f,0.9f,0.1f,1f) : new Color(0.4f,0.7f,0.4f,1f));
        game.font.draw(game.batch, selecting ? "● DRAWING PATH..." : "○ PRESS SPACE TO DRAW", W/2f - 140, 38);

        game.font.setColor(0.3f, 0.5f, 0.8f, 0.8f);
        game.font.draw(game.batch, "Arrows move  SPACE draw/stop  R reset  ESC quit", W/2f - 230, 16);
        game.batch.end();
    }

    private void drawResult(int W, int H) {
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(won ? 0f : 0.1f, won ? 0.06f : 0f, won ? 0.15f : 0f, 0.9f);
        shape.rect(W/2f - 280, H/2f - 90, 560, 180);
        shape.end();
        game.batch.begin();
        if (won) {
            game.bigFont.setColor(0.2f, 0.7f, 1f, 1f);
            game.bigFont.draw(game.batch, "CIRCUIT COMPLETE!", W/2f - 230, H/2f + 60);
            game.font.setColor(0.7f, 0.9f, 1f, 1f);
            game.font.draw(game.batch, "Glitch token secured. Token " + game.session.tokensFound + " / 3", W/2f-200, H/2f+10);
        } else {
            game.bigFont.setColor(1f, 0.3f, 0.3f, 1f);
            game.bigFont.draw(game.batch, "CIRCUIT OVERLOADED", W/2f - 240, H/2f + 60);
        }
        game.font.setColor(0.5f, 0.8f, 1f, 0.9f);
        game.font.draw(game.batch, "Press ENTER to continue...", W/2f-160, H/2f-50);
        game.batch.end();
    }

    private void endGame() {
        if (won) game.session.onMiniGameComplete(1, 300 + (int)((timeLimit - timeUsed) * 5));
        game.toRoom(roomId);
    }

    // The 9-minute run timer intentionally keeps ticking during mini-games;
    // show() clears any leftover lifecycle pause so it can never stay frozen
    // after a screen transition.
    @Override public void show() { game.session.paused = false; }
    @Override public void resize(int w, int h) { vp.update(w, h, true); }
    @Override public void pause() { game.session.paused = true; }
    @Override public void resume() { game.session.paused = false; }
    @Override public void hide() {}
    @Override public void dispose() { shape.dispose(); }
}
