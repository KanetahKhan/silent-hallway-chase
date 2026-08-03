package com.silentclassroom;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.silentclassroom.game.GameSession;
import com.silentclassroom.screen.GameOverScreen;
import com.silentclassroom.screen.HallwayScreen;
import com.silentclassroom.screen.MainMenuScreen;
import com.silentclassroom.screen.RoomScreen;
import com.silentclassroom.minigame.CircuitBreakerScreen;
import com.silentclassroom.minigame.KernelPanicScreen;
import com.silentclassroom.minigame.SilentCodeScreen;

public class SilentClassroomGame extends Game {

    public SpriteBatch batch;
    public BitmapFont font;
    public BitmapFont bigFont;
    public GameSession session;
    public GameAssets assets;

    @Override
    public void create() {
        assets = new GameAssets();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.4f);
        bigFont = new BitmapFont();
        bigFont.getData().setScale(2.5f);
        toMainMenu();
    }

    /**
     * Swap screens. The outgoing screen is disposed on the next frame rather
     * than immediately: the incoming screen has already built its GL resources
     * by the time we get here, and tearing the old one down in the middle of
     * the swap frees buffers that are still bound, corrupting the native heap.
     */
    private void switchScreen(com.badlogic.gdx.Screen next) {
        final com.badlogic.gdx.Screen old = getScreen();
        setScreen(next);
        if (old != null) {
            com.badlogic.gdx.Gdx.app.postRunnable(old::dispose);
        }
    }

    public void toMainMenu() {
        switchScreen(new MainMenuScreen(this));
    }

    public void startNewGame() {
        session = new GameSession();
        switchScreen(new HallwayScreen(this));
    }

    public void toHallway() {
        switchScreen(new HallwayScreen(this));
    }

    public void toRoom(int roomId) {
        switchScreen(new RoomScreen(this, roomId));
    }

    public void toMiniGame(int type, int roomId) {
        switch (type) {
            case 0: switchScreen(new KernelPanicScreen(this, roomId)); break;
            case 1: switchScreen(new CircuitBreakerScreen(this, roomId)); break;
            case 2: switchScreen(new SilentCodeScreen(this, roomId)); break;
        }
    }

    public void toGameOver(boolean won) {
        switchScreen(new GameOverScreen(this, won));
    }

    @Override
    public void dispose() {
        super.dispose();
        batch.dispose();
        font.dispose();
        bigFont.dispose();
        assets.dispose();
    }
}
