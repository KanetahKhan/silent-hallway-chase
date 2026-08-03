package com.override.prototype;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
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
import java.util.Set;

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

    private static final String MAP_FILE =
            "academic-building-2-floor-2.tmx";

    private static final String MAP_RESOURCE_PATH =
            "/assets/levels/" + MAP_FILE;

    /*
     * Set this to true only when you want to see red collision boxes.
     * After testing, set it back to false.
     */
    private static final boolean SHOW_COLLISION_DEBUG = false;

    /*
     * In this generated map:
     * GID 8 = open door.
     * Open doors should not block the player.
     */
    private static final Set<Integer> NON_BLOCKING_STRUCTURE_GIDS =
            Set.of(8);

    private Entity player;

    private final List<Rectangle2D> collisionBoxes =
            new ArrayList<>();

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(VIEW_WIDTH);
        settings.setHeight(VIEW_HEIGHT);

        settings.setTitle("Silent Classroom - Collision Test");
        settings.setVersion("0.6");

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

        setLevelFromMap(MAP_FILE);

        loadCollisionBoxes();

        if (SHOW_COLLISION_DEBUG) {
            drawCollisionDebugBoxes();
        }

        createPlayer();
        configureCamera();
    }

    private void createPlayer() {
        Point2D spawnPoint = findPointObject("PlayerSpawn");

        Rectangle temporaryPlayerView = new Rectangle(
                PLAYER_SIZE,
                PLAYER_SIZE,
                Color.CYAN
        );

        temporaryPlayerView.setStroke(Color.WHITE);
        temporaryPlayerView.setStrokeWidth(1.5);

        player = entityBuilder()
                .at(
                        spawnPoint.getX() - PLAYER_SIZE / 2.0,
                        spawnPoint.getY() - PLAYER_SIZE / 2.0
                )
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
        onKey(KeyCode.W, () -> movePlayer(0, -PLAYER_SPEED));
        onKey(KeyCode.S, () -> movePlayer(0, PLAYER_SPEED));
        onKey(KeyCode.A, () -> movePlayer(-PLAYER_SPEED, 0));
        onKey(KeyCode.D, () -> movePlayer(PLAYER_SPEED, 0));

        onKey(KeyCode.UP, () -> movePlayer(0, -PLAYER_SPEED));
        onKey(KeyCode.DOWN, () -> movePlayer(0, PLAYER_SPEED));
        onKey(KeyCode.LEFT, () -> movePlayer(-PLAYER_SPEED, 0));
        onKey(KeyCode.RIGHT, () -> movePlayer(PLAYER_SPEED, 0));
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

        double oldX = player.getX();

        double nextX = clamp(
                oldX + amount,
                0,
                MAP_WIDTH - PLAYER_SIZE
        );

        player.setX(nextX);

        if (isTouchingCollisionBox()) {
            player.setX(oldX);
        }
    }

    private void moveVertically(double amount) {
        if (amount == 0) {
            return;
        }

        double oldY = player.getY();

        double nextY = clamp(
                oldY + amount,
                0,
                MAP_HEIGHT - PLAYER_SIZE
        );

        player.setY(nextY);

        if (isTouchingCollisionBox()) {
            player.setY(oldY);
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

        Document document = loadTmxDocument();

        /*
         * 1. Load proper rectangle collision objects from the Collision layer.
         */
        loadObjectLayerCollision(document);

        /*
         * 2. Also treat the Structure layer as collision.
         * This includes room walls, closed doors, windows, railings, etc.
         */
        loadTileLayerCollision(
                document,
                "Structure",
                true
        );

        /*
         * 3. Also treat the Furniture layer as collision.
         * This fixes collision with PCs, desks, server racks, tables, etc.
         */
        loadTileLayerCollision(
                document,
                "Furniture",
                false
        );

        System.out.println(
                "Loaded collision boxes: "
                        + collisionBoxes.size()
        );
    }

    private void loadObjectLayerCollision(Document document) {
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

                double x = readDoubleAttribute(object, "x", 0);
                double y = readDoubleAttribute(object, "y", 0);
                double width = readDoubleAttribute(object, "width", 0);
                double height = readDoubleAttribute(object, "height", 0);

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
    }

    private void loadTileLayerCollision(
            Document document,
            String layerName,
            boolean isStructureLayer
    ) {
        Element layer = findLayerByName(
                document,
                layerName
        );

        if (layer == null) {
            System.out.println(
                    "Layer not found: " + layerName
            );
            return;
        }

        Element data =
                (Element) layer
                        .getElementsByTagName("data")
                        .item(0);

        if (data == null) {
            return;
        }

        String csvText = data.getTextContent();

        String[] values =
                csvText
                        .replace("\n", "")
                        .replace("\r", "")
                        .split(",");

        for (int index = 0; index < values.length; index++) {
            String rawText = values[index].trim();

            if (rawText.isEmpty()) {
                continue;
            }

            long rawGid = Long.parseLong(rawText);

            /*
             * Tiled can store flip flags in the high bits.
             * This removes those flags and keeps the real tile ID.
             */
            int gid = (int) (rawGid & 0x1FFFFFFF);

            if (gid == 0) {
                continue;
            }

            if (isStructureLayer
                    && NON_BLOCKING_STRUCTURE_GIDS.contains(gid)) {
                continue;
            }

            int tileX = index % MAP_COLUMNS;
            int tileY = index / MAP_COLUMNS;

            collisionBoxes.add(
                    new Rectangle2D(
                            tileX * TILE_SIZE,
                            tileY * TILE_SIZE,
                            TILE_SIZE,
                            TILE_SIZE
                    )
            );
        }
    }

    private Element findLayerByName(
            Document document,
            String layerName
    ) {
        NodeList layers =
                document.getElementsByTagName("layer");

        for (int i = 0; i < layers.getLength(); i++) {
            Element layer = (Element) layers.item(i);

            if (layerName.equals(layer.getAttribute("name"))) {
                return layer;
            }
        }

        return null;
    }

    private Point2D findPointObject(String objectName) {
        Document document = loadTmxDocument();

        NodeList objects =
                document.getElementsByTagName("object");

        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i);

            if (!objectName.equals(object.getAttribute("name"))) {
                continue;
            }

            double x = readDoubleAttribute(object, "x", 0);
            double y = readDoubleAttribute(object, "y", 0);

            return new Point2D(x, y);
        }

        throw new IllegalStateException(
                "Could not find object named: " + objectName
        );
    }

    private Document loadTmxDocument() {
        try (InputStream inputStream =
                     getClass().getResourceAsStream(
                             MAP_RESOURCE_PATH
                     )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Could not find TMX file: "
                                + MAP_RESOURCE_PATH
                );
            }

            Document document =
                    DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(inputStream);

            document.getDocumentElement().normalize();

            return document;

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Failed to load TMX file.",
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