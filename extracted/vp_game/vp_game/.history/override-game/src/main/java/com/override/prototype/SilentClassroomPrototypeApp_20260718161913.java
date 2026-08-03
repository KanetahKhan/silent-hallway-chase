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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

public final class SilentClassroomPrototypeApp extends GameApplication {

    private static final int VIEW_WIDTH = 1280;
    private static final int VIEW_HEIGHT = 720;

    private static final double PLAYER_SIZE = 24;
    private static final double PLAYER_SPEED = 4;

    private static final String MAP_FILE = "iut-ict-lab.tmx";
    private static final String MAP_RESOURCE_PATH =
            "/assets/levels/" + MAP_FILE;

    /*
     * Change to true when you want to see every collision rectangle.
     */
    private static final boolean SHOW_COLLISION_DEBUG = false;

    private final List<Rectangle2D> collisionBoxes = new ArrayList<>();

    private Entity player;
    private Point2D playerSpawn;

    private int mapPixelWidth;
    private int mapPixelHeight;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(VIEW_WIDTH);
        settings.setHeight(VIEW_HEIGHT);

        settings.setTitle("Silent Classroom - IUT ICT Lab");
        settings.setVersion("0.7");

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
                Color.rgb(17, 21, 27)
        );

        /*
         * Register this before setLevelFromMap(), because the TMX contains
         * Wall, Spawn, Exit, Dialogue, RoomZone and Light objects.
         */
        getGameWorld().addEntityFactory(
                new SilentClassroomFactory()
        );

        setLevelFromMap(MAP_FILE);

        /*
         * Reads map dimensions, PlayerSpawn and the Collision object layer.
         * Collision comes only from the explicit Collision layer, so
         * decorative lights, screens and chairs do not accidentally block.
         */
        loadTmxData();

        createPlayer();
        configureCamera();

        if (SHOW_COLLISION_DEBUG) {
            drawCollisionDebugBoxes();
        }

        System.out.println("Map size: "
                + mapPixelWidth + " x " + mapPixelHeight);
        System.out.println("Loaded collision boxes: "
                + collisionBoxes.size());
        System.out.println("Player spawn: " + playerSpawn);
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
                .at(
                        playerSpawn.getX() - PLAYER_SIZE / 2.0,
                        playerSpawn.getY() - PLAYER_SIZE / 2.0
                )
                .type(SilentClassroomType.PLAYER)
                .viewWithBBox(temporaryPlayerView)
                .zIndex(1000)
                .buildAndAttach();

        /*
         * Fail clearly if PlayerSpawn was accidentally placed inside a desk
         * or wall in Tiled.
         */
        if (isTouchingCollisionBox()) {
            throw new IllegalStateException(
                    "PlayerSpawn overlaps a collision rectangle. "
                            + "Move PlayerSpawn in the Objects layer in Tiled."
            );
        }
    }

    private void configureCamera() {
        var viewport = getGameScene().getViewport();

        viewport.setBounds(
                0,
                0,
                mapPixelWidth,
                mapPixelHeight
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
        /*
         * Resolve the two axes separately. This lets the player slide along
         * desks and walls instead of sticking at corners.
         */
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
                mapPixelWidth - PLAYER_SIZE
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
                mapPixelHeight - PLAYER_SIZE
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

    private void loadTmxData() {
        collisionBoxes.clear();

        Document document = loadTmxDocument();
        Element map = document.getDocumentElement();

        int mapColumns = readIntAttribute(
                map,
                "width",
                48
        );

        int mapRows = readIntAttribute(
                map,
                "height",
                30
        );

        int tileWidth = readIntAttribute(
                map,
                "tilewidth",
                32
        );

        int tileHeight = readIntAttribute(
                map,
                "tileheight",
                32
        );

        mapPixelWidth = mapColumns * tileWidth;
        mapPixelHeight = mapRows * tileHeight;

        loadCollisionObjects(document);
        playerSpawn = findPointObject(
                document,
                "PlayerSpawn"
        );
    }

    private void loadCollisionObjects(Document document) {
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

            return;
        }

        throw new IllegalStateException(
                "The TMX does not contain an object layer named Collision."
        );
    }

    private Point2D findPointObject(
            Document document,
            String objectName
    ) {
        NodeList objects =
                document.getElementsByTagName("object");

        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i);

            if (!objectName.equals(object.getAttribute("name"))) {
                continue;
            }

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

            return new Point2D(x, y);
        }

        throw new IllegalStateException(
                "Could not find a TMX object named "
                        + objectName
                        + "."
        );
    }

    private Document loadTmxDocument() {
        try (InputStream inputStream =
                     getClass().getResourceAsStream(
                             MAP_RESOURCE_PATH
                     )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Could not find "
                                + MAP_RESOURCE_PATH
                                + ". Copy the TMX and PNG into "
                                + "src/main/resources/assets/levels/."
                );
            }

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            /*
             * Safe XML parsing: the map must not load external entities.
             */
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
            );

            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            factory.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    ""
            );

            Document document =
                    factory
                            .newDocumentBuilder()
                            .parse(inputStream);

            document.getDocumentElement().normalize();

            return document;

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Failed to read " + MAP_RESOURCE_PATH,
                    exception
            );
        }
    }

    private int readIntAttribute(
            Element element,
            String attributeName,
            int defaultValue
    ) {
        String value = element.getAttribute(attributeName);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Integer.parseInt(value);
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
                    .zIndex(2000)
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
