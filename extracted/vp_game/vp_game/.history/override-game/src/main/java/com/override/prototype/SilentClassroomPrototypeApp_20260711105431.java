package com.override.prototype;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.scene.Viewport;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import static com.almasb.fxgl.dsl.FXGL.*;

public final class SilentClassroomPrototypeApp extends GameApplication {

    /*
     * Game window.
     * Change these to 1600 x 900 later if your monitor supports it.
     */
    private static final int VIEW_WIDTH = 1280;
    private static final int VIEW_HEIGHT = 720;

    /*
     * Tiled map information:
     * 72 columns × 100 rows
     * 32 × 32 pixels per tile
     */
    private static final int TILE_SIZE = 32;
    private static final int MAP_COLUMNS = 72;
    private static final int MAP_ROWS = 100;

    private static final double MAP_WIDTH =
            MAP_COLUMNS * TILE_SIZE;

    private static final double MAP_HEIGHT =
            MAP_ROWS * TILE_SIZE;

    private static final double PLAYER_SIZE = 24;
    private static final double PLAYER_SPEED = 4;

    /*
     * PlayerSpawn marker is located around tile 35,70.
     * Subtract half the player's size so its centre is on the marker.
     */
    private static final double PLAYER_START_X =
            35 * TILE_SIZE + TILE_SIZE / 2.0 - PLAYER_SIZE / 2.0;

    private static final double PLAYER_START_Y =
            70 * TILE_SIZE + TILE_SIZE / 2.0 - PLAYER_SIZE / 2.0;

    private Entity player;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(VIEW_WIDTH);
        settings.setHeight(VIEW_HEIGHT);

        settings.setTitle("Silent Classroom - Map Test");
        settings.setVersion("0.3");

        settings.setIntroEnabled(false);
        settings.setMainMenuEnabled(false);
        settings.setGameMenuEnabled(false);

        /*
         * Allow resizing and preserve the 16:9 ratio.
         */
        settings.setManualResizeEnabled(true);
        settings.setPreserveResizeRatio(true);

        /*
         * Fullscreen is allowed, but the game does not automatically
         * start in fullscreen.
         *
         * Change false to true to start fullscreen.
         */
        settings.setFullScreenAllowed(true);
        settings.setFullScreenFromStart(false);
    }

    @Override
    protected void initGame() {
        /*
         * The normal FXGL background is white.
         * The dark background makes empty areas around the map
         * match the Silent Classroom atmosphere.
         */
        getGameScene().setBackgroundColor(
                Color.rgb(14, 17, 23)
        );

        /*
         * Required because the TMX contains objects such as:
         * Wall, Spawn, Arcade, Dialogue, Exit and Light.
         */
        getGameWorld().addEntityFactory(
                new SilentClassroomFactory()
        );

        /*
         * Loads from:
         * src/main/resources/assets/levels/
         */
        setLevelFromMap(
                "academic-building-2-floor-2.tmx"
        );

        createPlayer();
        configureCamera();
    }

    private void createPlayer() {
        Rectangle temporaryPlayerView = new Rectangle(
                PLAYER_SIZE,
                PLAYER_SIZE,
                Color.CYAN
        );

        temporaryPlayerView.setStroke(Color.WHITE);
        temporaryPlayerView.setStrokeWidth(1.5);

        player = entityBuilder()
                .at(PLAYER_START_X, PLAYER_START_Y)
                .viewWithBBox(temporaryPlayerView)
                .zIndex(100)
                .buildAndAttach();
    }

    private void configureCamera() {
        Viewport viewport = getGameScene().getViewport();

        /*
         * Prevent the camera from travelling outside the full
         * 2304 × 3200 pixel map.
         */
        viewport.setBounds(
                0,
                0,
                MAP_WIDTH,
                MAP_HEIGHT
        );

        /*
         * Keep the player in the centre of the screen.
         */
        viewport.bindToEntity(
                player,
                getAppWidth() / 2.0,
                getAppHeight() / 2.0
        );

        viewport.setZoom(1.0);
    }

    @Override
    protected void initInput() {
        /*
         * WASD controls.
         */
        onKey(
                KeyCode.W,
                () -> movePlayer(0, -PLAYER_SPEED)
        );

        onKey(
                KeyCode.S,
                () -> movePlayer(0, PLAYER_SPEED)
        );

        onKey(
                KeyCode.A,
                () -> movePlayer(-PLAYER_SPEED, 0)
        );

        onKey(
                KeyCode.D,
                () -> movePlayer(PLAYER_SPEED, 0)
        );

        /*
         * Arrow-key controls.
         */
        onKey(
                KeyCode.UP,
                () -> movePlayer(0, -PLAYER_SPEED)
        );

        onKey(
                KeyCode.DOWN,
                () -> movePlayer(0, PLAYER_SPEED)
        );

        onKey(
                KeyCode.LEFT,
                () -> movePlayer(-PLAYER_SPEED, 0)
        );

        onKey(
                KeyCode.RIGHT,
                () -> movePlayer(PLAYER_SPEED, 0)
        );
    }

    private void movePlayer(double movementX, double movementY) {
        double nextX = player.getX() + movementX;
        double nextY = player.getY() + movementY;

        /*
         * Keep the player inside the complete map.
         * Actual wall collision will be added next.
         */
        nextX = clamp(
                nextX,
                0,
                MAP_WIDTH - PLAYER_SIZE
        );

        nextY = clamp(
                nextY,
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