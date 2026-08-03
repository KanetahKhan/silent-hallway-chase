package com.override.prototype;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

public final class SilentClassroomPrototypeApp extends GameApplication {

    private static final int VIEW_WIDTH = 1280;
    private static final int VIEW_HEIGHT = 720;

    private static final int TILE_SIZE = 32;
    private static final int MAP_COLUMNS = 72;
    private static final int MAP_ROWS = 100;

    private static final int MAP_WIDTH =
            MAP_COLUMNS * TILE_SIZE;

    private static final int MAP_HEIGHT =
            MAP_ROWS * TILE_SIZE;

    private static final double PLAYER_SIZE = 24;
    private static final double PLAYER_SPEED = 4;

    private static final double PLAYER_START_X =
            35 * TILE_SIZE
                    + TILE_SIZE / 2.0
                    - PLAYER_SIZE / 2.0;

    private static final double PLAYER_START_Y =
            70 * TILE_SIZE
                    + TILE_SIZE / 2.0
                    - PLAYER_SIZE / 2.0;

    /*
     * Turn this true if you want to see red collision boxes.
     */
    private static final boolean SHOW_COLLISION_DEBUG = false;

    private Entity player;

    private final List<Rectangle2D> collisionBoxes =
            new ArrayList<>();

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(VIEW_WIDTH);
        settings.setHeight(VIEW_HEIGHT);

        settings.setTitle("Silent Classroom - Collision Test");
        settings.setVersion("0.5");

        settings.setIntroEnabled(false);
        settings.setMainMenuEnabled(false);
        settings.setGameMenuEnabled(false);

        settings.setManualResizeEnabled(true);
        settings.setPreserveResizeRatio(true);

        settings.setFullScreenAllowed(true);
        settings.setFullScreenFromStart(false);
    }

    @Override
    protected void initGame() {
        getGameScene().setBackgroundColor(
                Color.rgb(14, 17, 23)
        );

        getGameWorld().addEntityFactory(
                new SilentClassroomFactory()
        );

        setLevelFromMap(
                "academic-building-2-floor-2.tmx"
        );

        /*
         * Read the Collision object layer from the TMX.
         * This makes walls, desks, PCs and furniture block the player.
         */
        loadCollisionBoxes();

        if (SHOW_COLLISION_DEBUG) {
            drawCollisionDebugBoxes();
        }

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
                .type(SilentClassroomType.PLAYER)
                .viewWithBBox(temporaryPlayerView)
                .collidable()
                .zIndex(100)
                .buildAndAttach();
    }

    private void configureCamera() {
        var viewport = getGameScene().getViewport();

        viewport.setBounds(
                0,
                0,
                MAP_WIDTH,
                MAP_HEIGHT
        );

        viewport.bindToEntity(
                player,
                getAppWidth() / 2.0,
                getAppHeight() / 2.0
        );

        viewport.setZoom(1.0);
    }

    @Override
    protected void initInput() {
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

    private void movePlayer(
            double movementX,
            double movementY
    ) {
        moveHorizontally(movementX);
        moveVertically(movementY);
    }

    private void moveHorizontally(double amount) {
        if (amount == 0) {
            return;
        }

        double previousX = player.getX();

        double nextX = clamp(
                previousX + amount,
                0,
                MAP_WIDTH - PLAYER_SIZE
        );

        player.setX(nextX);

        if (isTouchingCollisionBox()) {
            player.setX(previousX);
        }
    }

    private void moveVertically(double amount) {
        if (amount == 0) {
            return;
        }

        double previousY = player.getY();

        double nextY = clamp(
                previousY + amount,
                0,
                MAP_HEIGHT - PLAYER_SIZE
        );

        player.setY(nextY);

        if (isTouchingCollisionBox()) {
            player.setY(previousY);
        }
    }

    private boolean isTouchingCollisionBox() {
        Rectangle2D playerBox = new Rectangle2D(
                player.getX(),
                player.getY(),
                PLAYER_SIZE,
                PLAYER_SIZE
        );

        return collisionBoxes
                .stream()
                .anyMatch(playerBox::intersects);
    }

    private void loadCollisionBoxes() {
        collisionBoxes.clear();

        try (InputStream inputStream =
                     getClass().getResourceAsStream(
                             "/assets/levels/academic-building-2-floor-2.tmx"
                     )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Could not find TMX file in resources/assets/levels."
                );
            }

            Document document =
                    DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(inputStream);

            document.getDocumentElement().normalize();

            NodeList objectGroups =
                    document.getElementsByTagName("objectgroup");

            for (int i = 0; i < objectGroups.getLength(); i++) {
                Element group = (Element) objectGroups.item(i);

                if (!"Collision".equals(group.getAttribute("name"))) {
                    continue;
                }

                NodeList objects =
                        group.getElementsByTagName("object");

                for (int j = 0; j < objects.getLength(); j++) {
                    Element object = (Element) objects.item(j);

                    double x = readDoubleAttribute(
                            object,
                            "x",
                            0
                    );

                    double y = readDoubleAttribute(
                            object,
                            "y",
                            0
                    );

                    double width = readDoubleAttribute(
                            object,
                            "width",
                            0
                    );

                    double height = readDoubleAttribute(
                            object,
                            "height",
                            0
                    );

                    if (width <= 0 || height <= 0) {
                        continue;
                    }

                    collisionBoxes.add(
                            new Rectangle2D(
                                    x,
                                    y,
                                    width,
                                    height
                            )
                    );
                }
            }

            System.out.println(
                    "Loaded collision boxes: "
                            + collisionBoxes.size()
            );

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Failed to load collision boxes from TMX.",
                    exception
            );
        }
    }

    private double readDoubleAttribute(
            Element element,
            String attributeName,
            double defaultValue
    ) {
        String value = element.getAttribute(attributeName);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Double.parseDouble(value);
    }

    private void drawCollisionDebugBoxes() {
        for (Rectangle2D box : collisionBoxes) {
            Rectangle debugView = new Rectangle(
                    box.getWidth(),
                    box.getHeight(),
                    Color.rgb(255, 0, 0, 0.25)
            );

            debugView.setStroke(Color.RED);
            debugView.setStrokeWidth(1);

            entityBuilder()
                    .at(
                            box.getMinX(),
                            box.getMinY()
                    )
                    .view(debugView)
                    .zIndex(500)
                    .buildAndAttach();
        }
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