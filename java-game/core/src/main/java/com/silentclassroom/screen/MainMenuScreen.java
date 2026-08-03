package com.silentclassroom.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.silentclassroom.SilentClassroomGame;

public class MainMenuScreen implements Screen {

    private final SilentClassroomGame game;
    private final ShapeRenderer shape;
    private final ScreenViewport vp;

    private float flashTimer = 0f;
    private float bgPulse = 0f;
    private int selectedButton = 0; // 0=PLAY, 1=QUIT

    // Grid flicker
    private final float[] gridPulse = new float[30];
    private float totalTime = 0f;

    public MainMenuScreen(SilentClassroomGame game) {
        this.game = game;
        this.shape = new ShapeRenderer();
        this.vp = new ScreenViewport();
        for (int i = 0; i < gridPulse.length; i++) {
            gridPulse[i] = (float)(Math.random() * Math.PI * 2);
        }
    }

    @Override
    public void render(float delta) {
        totalTime += delta;
        flashTimer += delta;
        bgPulse += delta * 0.8f;

        handleInput();

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        vp.apply();
        int W = Gdx.graphics.getWidth();
        int H = Gdx.graphics.getHeight();

        shape.setProjectionMatrix(vp.getCamera().combined);

        // --- Background grid ---
        shape.begin(ShapeRenderer.ShapeType.Line);
        int cols = 20, rows = 12;
        float cellW = (float) W / cols, cellH = (float) H / rows;
        for (int r = 0; r <= rows; r++) {
            for (int c = 0; c <= cols; c++) {
                int idx = (r * cols + c) % gridPulse.length;
                float brightness = 0.04f + 0.04f * (float) Math.sin(totalTime * 1.2f + gridPulse[idx]);
                shape.setColor(brightness, brightness * 1.5f, brightness * 2f, 1f);
                if (c < cols) {
                    shape.line(c * cellW, r * cellH, (c + 1) * cellW, r * cellH);
                }
                if (r < rows) {
                    shape.line(c * cellW, r * cellH, c * cellW, (r + 1) * cellH);
                }
            }
        }
        shape.end();

        // --- Scanner line ---
        shape.begin(ShapeRenderer.ShapeType.Filled);
        float scanY = (float)(H * 0.5 + H * 0.4 * Math.sin(totalTime * 0.5));
        shape.setColor(0.05f, 0.3f, 0.5f, 0.15f);
        shape.rect(0, scanY - 3, W, 6);
        shape.end();

        game.batch.begin();

        // --- Title ---
        float titleX = W * 0.5f - 280;
        float titleY = H * 0.75f;
        game.bigFont.setColor(0f, 1f, 0.4f, 1f);
        game.bigFont.draw(game.batch, "SILENT CLASSROOM", titleX, titleY);

        game.font.setColor(0.4f, 0.8f, 0.4f, 0.85f);
        game.font.draw(game.batch, "Find 3 mini-games hidden across 7 rooms.", W * 0.5f - 220, titleY - 40);
        game.font.draw(game.batch, "Evade the AI sentinel. Stay silent. Survive.", W * 0.5f - 220, titleY - 65);

        // --- Buttons ---
        float btnX = W * 0.5f - 100;
        float btnY0 = H * 0.45f;
        float btnY1 = H * 0.35f;

        // PLAY button
        boolean playHover = selectedButton == 0;
        float pa = playHover ? 1f : 0.6f;
        game.bigFont.setColor(playHover ? 0f : 0.1f, playHover ? 1f : 0.7f, playHover ? 0.3f : 0.3f, pa);
        game.bigFont.draw(game.batch, playHover ? "[ PLAY ]" : "  PLAY  ", btnX, btnY0 + 30);

        // QUIT button
        boolean quitHover = selectedButton == 1;
        float qa = quitHover ? 1f : 0.6f;
        game.bigFont.setColor(quitHover ? 1f : 0.7f, quitHover ? 0.2f : 0.2f, quitHover ? 0.2f : 0.2f, qa);
        game.bigFont.draw(game.batch, quitHover ? "[ QUIT ]" : "  QUIT  ", btnX, btnY1 + 30);

        // --- Controls hint ---
        game.font.setColor(0.3f, 0.6f, 0.3f, 0.7f);
        game.font.draw(game.batch, "Arrow keys / W-S to navigate   ENTER to select", W * 0.5f - 230, H * 0.15f);

        // --- Lore strip ---
        game.font.setColor(0.3f, 0.5f, 0.8f, 0.7f);
        game.font.draw(game.batch, "OVERRIDE SYSTEM v3.1 | CHAPTER 1: SILENT CLASSROOM", 20, 28);

        game.batch.end();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.W) || Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            selectedButton = Math.max(0, selectedButton - 1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.S) || Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            selectedButton = Math.min(1, selectedButton + 1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (selectedButton == 0) game.startNewGame();
            else Gdx.app.exit();
        }
    }

    @Override public void show() {}
    @Override public void resize(int w, int h) { vp.update(w, h, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() { shape.dispose(); }
}
