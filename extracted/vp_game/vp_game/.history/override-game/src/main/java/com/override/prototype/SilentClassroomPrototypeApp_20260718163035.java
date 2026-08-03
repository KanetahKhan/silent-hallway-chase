package com.override.prototype;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
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
    private static final double EXIT_INTERACTION_DISTANCE = 55;

    private static final String CLASSROOM_MAP = "iut-classroom.tmx";
    private static final String ICT_LAB_MAP = "iut-ict-lab.tmx";

    /*
     * Change this to ICT_LAB_MAP to start directly in the ICT lab.
     */
    private static final String START_MAP = CLASSROOM_MAP;

    /*
     * true  = show transparent red collision rectangles
     * false = normal game view
     */
    private static final boolean SHOW_COLLISION_DEBUG = false;

    private final List<Rectangle2D> collisionBoxes = new ArrayList<>();
    private final List<NamedPoint> exitPoints = new ArrayList<>();

    private Entity player;
    private Point2D playerSpawn;

    private String currentMapFile;
    private String currentMapDisplayName;

    private int mapPixelWidth;
    private int mapPixelHeight;

    private Text hudText;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(VIEW_WIDTH);
        settings.setHeight(VIEW_HEIGHT);

        settings.setTitle("Silent Classroom - Classroom and ICT Lab");
        settings.setVersion("1.0");

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
         * Register once before loading any TMX map.
         * This factory supports the classroom, ICT lab and older floor map.
         */
        getGameWorld().addEntityFactory(
                new SilentClassroomFactory()
        );

        loadMap(START_MAP);
    }

    @Override
    protected void initUI() {
        hudText = new Text();
        hudText.setFill(Color.WHITE);
        hudText.setFont(Font.font("Consolas", 18));
        hudText.setStroke(Color.rgb(0, 0, 0, 0.8));
        hudText.setStrokeWidth(3);

        addUINode(hudText, 18, 28);
        updateHud();
    }

    @Override
    protected void initInput() {
        /*
         * Continuous movement.
         */
        onKey(KeyCode.W, () -> movePlayer(0, -PLAYER_SPEED));
        onKey(KeyCode.S, () -> movePlayer(0, PLAYER_SPEED));
        onKey(KeyCode.A, () -> movePlayer(-PLAYER_SPEED, 0));
        onKey(KeyCode.D, () -> movePlayer(PLAYER_SPEED, 0));

        onKey(KeyCode.UP, () -> movePlayer(0, -PLAYER_SPEED));
        onKey(KeyCode.DOWN, () -> movePlayer(0, PLAYER_SPEED));
        onKey(KeyCode.LEFT, () -> movePlayer(-PLAYER_SPEED, 0));
        onKey(KeyCode.RIGHT, () -> movePlayer(PLAYER_SPEED, 0));

        /*
         * One-press actions.
         *
         * 1 = classroom
         * 2 = ICT lab
         * E = use the nearby room exit
         * R = reload current room
         */
        onKeyDown(
                KeyCode.DIGIT1,
                () -> loadMapIfDifferent(CLASSROOM_MAP)
        );

        onKeyDown(
                KeyCode.DIGIT2,
                () -> loadMapIfDifferent(ICT_LAB_MAP)
        );

        onKeyDown(
                KeyCode.E,
                this::useNearbyExit
        );

        onKeyDown(
                KeyCode.R,
                () -> loadMap(currentMapFile)
        );
    }

    private void loadMapIfDifferent(String mapFile) {
        if (!mapFile.equals(currentMapFile)) {
            loadMap(mapFile);
        }
    }

    private void loadMap(String mapFile) {
        ensureMapExists(mapFile);

        currentMapFile = mapFile;

        /*
         * setLevelFromMap() clears the old level and loads the requested TMX.
         */
        setLevelFromMap(mapFile);

        loadMapData(mapFile);
        createPlayer();
        configureCamera();

        if (SHOW_COLLISION_DEBUG) {
            drawCollisionDebugBoxes();
        }

        updateHud();

        System.out.println("--------------------------------");
        System.out.println("Loaded: " + currentMapDisplayName);
        System.out.println("File: " + currentMapFile);
        System.out.println(
                "Map size: "
                        + mapPixelWidth
                        + " x "
                        + mapPixelHeight
        );
        System.out.println(
                "Collision rectangles: "
                        + collisionBoxes.size()
        );
        System.out.println("PlayerSpawn: " + playerSpawn);
    }

    private void createPlayer() {
        Rectangle playerView = new Rectangle(
                PLAYER_SIZE,
                PLAYER_SIZE,
                Color.CYAN
        );

        playerView.setStroke(Color.WHITE);
        playerView.setStrokeWidth(1.5);

        player = entityBuilder()
                .at(
                        playerSpawn.getX() - PLAYER_SIZE / 2.0,
                        playerSpawn.getY() - PLAYER_SIZE / 2.0
                )
                .type(SilentClassroomType.PLAYER)
                .viewWithBBox(playerView)
                .zIndex(1000)
                .buildAndAttach();

        if (isTouchingCollisionBox()) {
            throw new IllegalStateException(
                    "PlayerSpawn is inside a collision rectangle in "
                            + currentMapFile
                            + ". Move PlayerSpawn on the Objects layer in Tiled."
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

    private void movePlayer(
            double movementX,
            double movementY
    ) {
        /*
         * Resolve X and Y separately so the player slides along walls,
         * benches and PC desks instead of sticking at corners.
         */
        moveHorizontally(movementX);
        moveVertically(movementY);
    }

    private void moveHorizontally(double amount) {
        if (amount == 0 || player == null) {
            return;
        }

        double oldX = player.getX();

        double nextX = clamp(
                oldX + amount,
                0,
                mapPixelWidth - PLAYER_SIZE
        );

        player.setX(nextX);

        if (isTouchingCollisionBox()) {
            player.setX(oldX);
        }
    }

    private void moveVertically(double amount) {
        if (amount == 0 || player == null) {
            return;
        }

        double oldY = player.getY();

        double nextY = clamp(
                oldY + amount,
                0,
                mapPixelHeight - PLAYER_SIZE
        );

        player.setY(nextY);

        if (isTouchingCollisionBox()) {
            player.setY(oldY);
        }
    }

    private boolean isTouchingCollisionBox() {
        if (player == null) {
            return false;
        }

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

    private void useNearbyExit() {
        if (player == null) {
            return;
        }

        Point2D playerCentre = new Point2D(
                player.getX() + PLAYER_SIZE / 2.0,
                player.getY() + PLAYER_SIZE / 2.0
        );

        NamedPoint nearestExit = null;
        double nearestDistance = Double.MAX_VALUE;

        for (NamedPoint exit : exitPoints) {
            double distance = playerCentre.distance(
                    exit.x(),
                    exit.y()
            );

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestExit = exit;
            }
        }

        if (nearestExit == null
                || nearestDistance > EXIT_INTERACTION_DISTANCE) {

            System.out.println(
                    "Move closer to the room exit, then press E."
            );
            return;
        }

        /*
         * During this prototype:
         * Classroom exit -> ICT lab
         * ICT lab exit   -> Classroom
         *
         * Later this can be changed so both exits return to the corridor map.
         */
        if (CLASSROOM_MAP.equals(currentMapFile)) {
            loadMap(ICT_LAB_MAP);
        } else {
            loadMap(CLASSROOM_MAP);
        }
    }

    private void loadMapData(String mapFile) {
        collisionBoxes.clear();
        exitPoints.clear();

        Document document = loadTmxDocument(mapFile);
        Element map = document.getDocumentElement();

        int columns = readIntAttribute(
                map,
                "width",
                1
        );

        int rows = readIntAttribute(
                map,
                "height",
                1
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

        mapPixelWidth = columns * tileWidth;
        mapPixelHeight = rows * tileHeight;

        currentMapDisplayName = readMapDisplayName(
                document,
                mapFile
        );

        loadCollisionObjects(document);

        playerSpawn = findRequiredPointObject(
                document,
                "PlayerSpawn"
        );

        exitPoints.addAll(
                findPointObjectsByType(
                        document,
                        "Exit"
                )
        );
    }

    private void loadCollisionObjects(Document document) {
        Element collisionLayer = findObjectGroup(
                document,
                "Collision"
        );

        if (collisionLayer == null) {
            throw new IllegalStateException(
                    currentMapFile
                            + " does not contain an object layer named Collision."
            );
        }

        NodeList objects =
                collisionLayer.getElementsByTagName("object");

        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i);

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

    private Element findObjectGroup(
            Document document,
            String groupName
    ) {
        NodeList groups =
                document.getElementsByTagName("objectgroup");

        for (int i = 0; i < groups.getLength(); i++) {
            Element group = (Element) groups.item(i);

            if (groupName.equals(group.getAttribute("name"))) {
                return group;
            }
        }

        return null;
    }

    private Point2D findRequiredPointObject(
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

            return new Point2D(
                    readDoubleAttribute(object, "x", 0),
                    readDoubleAttribute(object, "y", 0)
            );
        }

        throw new IllegalStateException(
                currentMapFile
                        + " does not contain an object named "
                        + objectName
                        + "."
        );
    }

    private List<NamedPoint> findPointObjectsByType(
            Document document,
            String expectedType
    ) {
        List<NamedPoint> points = new ArrayList<>();

        NodeList objects =
                document.getElementsByTagName("object");

        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i);

            String type = object.getAttribute("type");
            String objectClass = object.getAttribute("class");

            if (!expectedType.equals(type)
                    && !expectedType.equals(objectClass)) {
                continue;
            }

            points.add(
                    new NamedPoint(
                            object.getAttribute("name"),
                            readDoubleAttribute(object, "x", 0),
                            readDoubleAttribute(object, "y", 0)
                    )
            );
        }

        return points;
    }

    private String readMapDisplayName(
            Document document,
            String fallbackFileName
    ) {
        NodeList properties =
                document.getElementsByTagName("property");

        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);

            if ("displayName".equals(
                    property.getAttribute("name")
            )) {
                String value = property.getAttribute("value");

                if (!value.isBlank()) {
                    return value;
                }
            }
        }

        return fallbackFileName;
    }

    private Document loadTmxDocument(String mapFile) {
        String resourcePath =
                "/assets/levels/" + mapFile;

        try (InputStream inputStream =
                     getClass().getResourceAsStream(
                             resourcePath
                     )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Could not find "
                                + resourcePath
                                + ". Copy the TMX and its tileset PNG into "
                                + "src/main/resources/assets/levels/."
                );
            }

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            /*
             * Prevent the parser from loading external XML entities.
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
                    "Failed to load TMX map: " + mapFile,
                    exception
            );
        }
    }

    private void ensureMapExists(String mapFile) {
        String path =
                "/assets/levels/" + mapFile;

        try (InputStream inputStream =
                     getClass().getResourceAsStream(path)) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Missing map: "
                                + path
                                + ". Put both classroom and ICT lab files "
                                + "inside src/main/resources/assets/levels/."
                );
            }

        } catch (Exception exception) {
            if (exception instanceof IllegalStateException) {
                throw (IllegalStateException) exception;
            }

            throw new RuntimeException(
                    "Could not check map resource: " + path,
                    exception
            );
        }
    }

    private int readIntAttribute(
            Element element,
            String name,
            int defaultValue
    ) {
        String value = element.getAttribute(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }

    private double readDoubleAttribute(
            Element element,
            String name,
            double defaultValue
    ) {
        String value = element.getAttribute(name);

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

    private void updateHud() {
        if (hudText == null) {
            return;
        }

        hudText.setText(
                currentMapDisplayName
                        + "\nWASD / Arrows: Move"
                        + "   E: Use exit"
                        + "   1: Classroom"
                        + "   2: ICT Lab"
                        + "   R: Reload"
        );
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

    private record NamedPoint(
            String name,
            double x,
            double y
    ) {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
