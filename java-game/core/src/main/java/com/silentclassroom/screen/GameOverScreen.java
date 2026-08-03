package com.silentclassroom.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.silentclassroom.SilentClassroomGame;

public class GameOverScreen implements Screen {

    private final SilentClassroomGame game;
    private final boolean won;
    private final ShapeRenderer shape;
    private final ScreenViewport vp;
    private float totalTime = 0f;

    public GameOverScreen(SilentClassroomGame game, boolean won) {
        this.game  = game;
        this.won   = won;
        this.shape = new ShapeRenderer(20000);
        this.vp    = new ScreenViewport();
    }

    @Override
    public void render(float delta) {
        totalTime += delta;

        Gdx.gl.glClearColor(0.02f, 0.02f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        vp.apply();
        int W = Gdx.graphics.getWidth();
        int H = Gdx.graphics.getHeight();
        shape.setProjectionMatrix(vp.getCamera().combined);

        // Background pulse
        shape.begin(ShapeRenderer.ShapeType.Filled);
        float pulse = (float)(0.5 + 0.5 * Math.sin(totalTime * 2));
        if (won) {
            shape.setColor(0f, pulse * 0.12f, 0f, 1f);
        } else {
            shape.setColor(pulse * 0.12f, 0f, 0f, 1f);
        }
        shape.rect(0, 0, W, H);
        shape.end();

        // Horizontal separator lines
        shape.begin(ShapeRenderer.ShapeType.Line);
        float lineAlpha = 0.4f;
        if (won) shape.setColor(0f, 1f, 0.3f, lineAlpha);
        else     shape.setColor(1f, 0.2f, 0.2f, lineAlpha);
        shape.line(80, H * 0.6f, W - 80, H * 0.6f);
        shape.line(80, H * 0.28f, W - 80, H * 0.28f);
        shape.end();

        game.batch.begin();

        // Outcome
        if (won) {
            game.bigFont.setColor(0f, 1f, 0.4f, 1f);
            game.bigFont.draw(game.batch, "MISSION COMPLETE", W * 0.5f - 230, H * 0.82f);
        } else {
            game.bigFont.setColor(1f, 0.2f, 0.2f, 1f);
            boolean hp0 = game.session.hp <= 0;
            game.bigFont.draw(game.batch,
                hp0 ? "CAPTURED — SYSTEM DOWN" : "TIME EXPIRED", W * 0.5f - 240, H * 0.82f);
        }

        // Stats
        float sx = 260f, sy = H * 0.55f;
        float lineH = 38f;
        game.font.setColor(0.8f, 0.9f, 0.8f, 1f);
        game.font.draw(game.batch, "Tokens Retrieved  :  " + game.session.tokensFound + " / 3",  sx, sy);
        game.font.draw(game.batch, "Captures Taken    :  " + game.session.captures,              sx, sy - lineH);
        game.font.draw(game.batch, "Time Remaining    :  " + game.session.getTimeString(),       sx, sy - lineH * 2);
        game.font.draw(game.batch, "Raw Score         :  " + game.session.score,                sx, sy - lineH * 3);

        // Final score (big)
        int finalScore = game.session.getFinalScore();
        if (won) game.bigFont.setColor(1f, 0.9f, 0.1f, 1f);
        else     game.bigFont.setColor(0.7f, 0.7f, 0.7f, 1f);
        game.bigFont.draw(game.batch, "FINAL SCORE:  " + finalScore, W * 0.5f - 200, H * 0.3f);

        // Grade
        String grade = won ? (finalScore > 1200 ? "S" : finalScore > 900 ? "A" : "B") : "F";
        game.bigFont.setColor(1f, 0.8f, 0.1f, 1f);
        game.bigFont.draw(game.batch, "GRADE:  " + grade, W * 0.5f - 100, H * 0.22f);

        // Prompt
        boolean blink = (int)(totalTime * 2) % 2 == 0;
        if (blink) {
            game.font.setColor(0.5f, 0.8f, 0.5f, 0.9f);
            game.font.draw(game.batch, "ENTER — Play Again     ESC — Main Menu",
                W * 0.5f - 200, H * 0.1f);
        }

        game.batch.end();

        // Input
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) game.startNewGame();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) game.toMainMenu();
    }

    @Override public void show() {}
    @Override public void resize(int w, int h) { vp.update(w, h, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() { shape.dispose(); }
}
