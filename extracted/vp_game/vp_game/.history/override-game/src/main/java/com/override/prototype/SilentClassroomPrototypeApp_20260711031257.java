package com.override.prototype;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.*;

public final class SilentClassroomPrototypeApp extends GameApplication {

    private static final int VIEW_WIDTH = 960;
    private static final int VIEW_HEIGHT = 540;

    private static final double MAP_WIDTH = 72 * 32;
    private static final double MAP_HEIGHT = 100 * 32;

    private static final double PLAYER_SIZE = 24;
    private static final double SPEED = 4;

    private Entity player;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(VIEW_WIDTH);
        settings.setHeight(VIEW_HEIGHT);
        settings.setTitle("Silent Classroom - Map Test");
        settings.setVersion("0.2");

        settings.setMainMenuEnabled(false);
        settings.setGameMenuEnabled(false);
    }

    @Override
    protected void initGame() {
        // Required for the objects stored inside the Tiled map.
        getGameWorld().addEntityFactory(
                new SilentClassroomFactory()
        );

        // Loads from src/main/resources/assets/levels/
        setLevelFromMap(
                "academic-building-2-floor-2.tmx"
        );

        createPlayer();

        // Camera follows the player through the large map.
        getGameScene()
                .getViewport()
                .bindToEntity(
                        player,
                        VIEW_WIDTH / 2.0,
                        VIEW_HEIGHT / 2.0
                );
    }

    private void createPlayer() {
        // Near the PlayerSpawn point in the generated map.
        player = entityBuilder()
                .at(1120, 2220)
                .viewWithBBox(
                        new Rectangle(
                                PLAYER_SIZE,
                                PLAYER_SIZE,
                                Color.CYAN
                        )
                )
                .buildAndAttach();
    }

    @Override
    protected void initInput() {
        onKey(KeyCode.W, () -> movePlayer(0, -SPEED));
        onKey(KeyCode.S, () -> movePlayer(0, SPEED));
        onKey(KeyCode.A, () -> movePlayer(-SPEED, 0));
        onKey(KeyCode.D, () -> movePlayer(SPEED, 0));
    }

    private void movePlayer(double dx, double dy) {
        double nextX = clamp(
                player.getX() + dx,
                0,
                MAP_WIDTH - PLAYER_SIZE
        );

        double nextY = clamp(
                player.getY() + dy,
                0,
                MAP_HEIGHT - PLAYER_SIZE
        );

        player.setPosition(nextX, nextY);
    }

    private double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}