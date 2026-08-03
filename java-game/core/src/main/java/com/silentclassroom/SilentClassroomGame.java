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

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.4f);
        bigFont = new BitmapFont();
        bigFont.getData().setScale(2.5f);
        toMainMenu();
    }

    /** Swap screens and dispose the outgoing one (LibGDX only hides it). */
    private void switchScreen(com.badlogic.gdx.Screen next) {
        com.badlogic.gdx.Screen old = getScreen();
        setScreen(next);
        if (old != null) old.dispose();
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
    }
}
