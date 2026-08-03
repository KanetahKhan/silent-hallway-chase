package com.override.prototype;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.entity.Entity;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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

    private static final double DOOR_INTERACTION_DISTANCE = 90;

    private static final String CLASSROOM_MAP =
            "iut-classroom.tmx";

    private static final String ICT_LAB_MAP =
            "iut-ict-lab.tmx";

    /*
     * Change this to ICT_LAB_MAP to start inside the lab.
     */
    private static final String START_MAP =
            CLASSROOM_MAP;

    private static final boolean SHOW_COLLISION_DEBUG =
            false;

    private final List<Rectangle2D> collisionBoxes =
            new ArrayList<>();

    private Entity player;
    private Entity doorEntity;

    private Group doorView;
    private Text roomHudText;
    private Text doorPromptText;
    private Rectangle doorPromptBackground;

    private Point2D playerSpawn;
    private Point2D exitInteractionPoint;

    private Rectangle2D closedDoorCollision;

    private String currentMapFile;
    private String currentMapDisplayName;
    private String targetMapFile;
    private String targetRoomName;

    private int mapPixelWidth;
    private int mapPixelHeight;

    private DoorState doorState =
            DoorState.CLOSED;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(VIEW_WIDTH);
        settings.setHeight(VIEW_HEIGHT);

        settings.setTitle(
                "Silent Classroom - Classroom and ICT Lab"
        );

        settings.setVersion("1.1");

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

        getGameWorld().addEntityFactory(
                new SilentClassroomFactory()
        );

        loadMap(START_MAP);
    }

    @Override
    protected void initUI() {
        createRoomHud();
        createDoorPrompt();
        updateRoomHud();
    }

    private void createRoomHud() {
        Rectangle background = new Rectangle(
                500,
                42,
                Color.rgb(0, 0, 0, 0.72)
        );

        background.setArcWidth(12);
        background.setArcHeight(12);

        roomHudText = new Text();
        roomHudText.setFill(Color.WHITE);
        roomHudText.setFont(
                Font.font(
                        "Consolas",
                        FontWeight.BOLD,
                        15
                )
        );

        addUINode(background, 12, 10);
        addUINode(roomHudText, 25, 36);
    }

    private void createDoorPrompt() {
        doorPromptBackground = new Rectangle(
                570,
                58,
                Color.rgb(0, 0, 0, 0.84)
        );

        doorPromptBackground.setArcWidth(18);
        doorPromptBackground.setArcHeight(18);
        doorPromptBackground.setStroke(
                Color.rgb(214, 185, 90)
        );

        doorPromptBackground.setStrokeWidth(2);

        doorPromptText = new Text();
        doorPromptText.setFill(Color.WHITE);
        doorPromptText.setFont(
                Font.font(
                        "Consolas",
                        FontWeight.BOLD,
                        18
                )
        );

        addUINode(
                doorPromptBackground,
                355,
                640
        );

        addUINode(
                doorPromptText,
                380,
                676
        );

        setDoorPromptVisible(false);
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

        /*
         * Nun-style door controls:
         *
         * E:
         * - closed door -> open
         * - open door   -> enter the next room
         * - locked door -> show locked message
         *
         * F:
         * - close an open door
         *
         * L:
         * - lock/unlock a closed door
         */
        onKeyDown(
                KeyCode.E,
                this::interactWithDoor
        );

        onKeyDown(
                KeyCode.F,
                this::closeDoor
        );

        onKeyDown(
                KeyCode.L,
                this::toggleDoorLock
        );

        onKeyDown(
                KeyCode.DIGIT1,
                () -> loadMapIfDifferent(CLASSROOM_MAP)
        );

        onKeyDown(
                KeyCode.DIGIT2,
                () -> loadMapIfDifferent(ICT_LAB_MAP)
        );

        onKeyDown(
                KeyCode.R,
                () -> loadMap(currentMapFile)
        );
    }

    @Override
    protected void onUpdate(double tpf) {
        updateDoorPrompt();
    }

    private void loadMapIfDifferent(String mapFile) {
        if (!mapFile.equals(currentMapFile)) {
            loadMap(mapFile);
        }
    }

    private void loadMap(String mapFile) {
        currentMapFile = mapFile;

        setLevelFromMap(mapFile);

        loadMapData(mapFile);

        doorState = DoorState.CLOSED;

        createDoor();
        createPlayer();
        configureCamera();

        if (SHOW_COLLISION_DEBUG) {
            drawCollisionDebugBoxes();
        }

        updateRoomHud();
        updateDoorPrompt();

        System.out.println("--------------------------------");
        System.out.println(
                "Loaded map: " + currentMapDisplayName
        );

        System.out.println(
                "Map size: "
                        + mapPixelWidth
                        + " x "
                        + mapPixelHeight
        );

        System.out.println(
                "Collision boxes: "
                        + collisionBoxes.size()
        );

        System.out.println(
                "Exit interaction point: "
                        + exitInteractionPoint
        );
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
                        playerSpawn.getX()
                                - PLAYER_SIZE / 2.0,
                        playerSpawn.getY()
                                - PLAYER_SIZE / 2.0
                )
                .type(SilentClassroomType.PLAYER)
                .viewWithBBox(playerView)
                .zIndex(1000)
                .buildAndAttach();

        if (isTouchingCollisionBox()) {
            throw new IllegalStateException(
                    "PlayerSpawn overlaps a wall, desk, or closed door in "
                            + currentMapFile
                            + ". Move PlayerSpawn in Tiled."
            );
        }
    }

    private void createDoor() {
        /*
         * The old front-facing double-door tiles were removed from the TMX.
         * This creates a proper interactive top-down door at runtime.
         */
        double doorTopY =
                CLASSROOM_MAP.equals(currentMapFile)
                        ? 21 * 32.0
                        : 22 * 32.0;

        closedDoorCollision = new Rectangle2D(
                0,
                doorTopY,
                32,
                64
        );

        doorView = new Group();

        doorEntity = entityBuilder()
                .at(0, doorTopY)
                .type(SilentClassroomType.DOOR)
                .view(doorView)
                .zIndex(900)
                .buildAndAttach();

        refreshDoorVisual();
    }

    private void refreshDoorVisual() {
        doorView.getChildren().clear();

        if (doorState == DoorState.OPEN) {
            /*
             * Open top-down door:
             * panel turns into the room.
             */
            Rectangle openPanel = new Rectangle(
                    64,
                    13,
                    Color.rgb(117, 70, 39)
            );

            openPanel.setStroke(
                    Color.rgb(59, 34, 22)
            );

            openPanel.setStrokeWidth(3);

            Rectangle hinge = new Rectangle(
                    8,
                    18,
                    Color.rgb(75, 43, 25)
            );

            hinge.setTranslateY(46);

            doorView.getChildren().addAll(
                    openPanel,
                    hinge
            );

            return;
        }

        Rectangle frame = new Rectangle(
                32,
                64,
                Color.rgb(68, 40, 25)
        );

        Rectangle panel = new Rectangle(
                24,
                58,
                Color.rgb(126, 76, 42)
        );

        panel.setTranslateX(4);
        panel.setTranslateY(3);

        panel.setStroke(
                Color.rgb(63, 36, 23)
        );

        panel.setStrokeWidth(2);

        Circle knob = new Circle(
                3,
                Color.rgb(226, 190, 91)
        );

        knob.setCenterX(23);
        knob.setCenterY(33);

        doorView.getChildren().addAll(
                frame,
                panel,
                knob
        );

        if (doorState == DoorState.LOCKED) {
            Rectangle lockPlate = new Rectangle(
                    10,
                    12,
                    Color.rgb(62, 63, 67)
            );

            lockPlate.setTranslateX(11);
            lockPlate.setTranslateY(25);

            Circle redLight = new Circle(
                    2.5,
                    Color.RED
            );

            redLight.setCenterX(16);
            redLight.setCenterY(29);

            doorView.getChildren().addAll(
                    lockPlate,
                    redLight
            );
        }
    }

    private void interactWithDoor() {
        if (!isNearDoor()) {
            return;
        }

        switch (doorState) {
            case CLOSED -> openDoor();

            case OPEN -> loadMap(targetMapFile);

            case LOCKED -> {
                doorPromptText.setText(
                        "LOCKED — press [L] to unlock"
                );

                System.out.println(
                        "The door is locked."
                );
            }
        }
    }

    private void openDoor() {
        doorState = DoorState.OPEN;
        refreshDoorVisual();
        updateDoorPrompt();

        System.out.println(
                "Door opened. Press E again to enter "
                        + targetRoomName
                        + "."
        );
    }

    private void closeDoor() {
        if (!isNearDoor()
                || doorState != DoorState.OPEN) {
            return;
        }

        Rectangle2D playerBox = getPlayerBox();

        /*
         * Do not close the door directly on top of the player.
         */
        if (playerBox.intersects(closedDoorCollision)) {
            doorPromptText.setText(
                    "Move away from the doorway first"
            );

            return;
        }

        doorState = DoorState.CLOSED;
        refreshDoorVisual();
        updateDoorPrompt();
    }

    private void toggleDoorLock() {
        if (!isNearDoor()) {
            return;
        }

        if (doorState == DoorState.OPEN) {
            doorPromptText.setText(
                    "Close the door with [F] before locking"
            );

            return;
        }

        doorState =
                doorState == DoorState.LOCKED
                        ? DoorState.CLOSED
                        : DoorState.LOCKED;

        refreshDoorVisual();
        updateDoorPrompt();
    }

    private void updateDoorPrompt() {
        if (player == null
                || doorPromptText == null) {
            return;
        }

        if (!isNearDoor()) {
            setDoorPromptVisible(false);
            return;
        }

        setDoorPromptVisible(true);

        switch (doorState) {
            case CLOSED -> doorPromptText.setText(
                    "[E] Open Door       [L] Lock Door"
            );

            case OPEN -> doorPromptText.setText(
                    "[E] Enter "
                            + targetRoomName
                            + "       [F] Close Door"
            );

            case LOCKED -> doorPromptText.setText(
                    "Door Locked       [L] Unlock Door"
            );
        }
    }

    private void setDoorPromptVisible(boolean visible) {
        if (doorPromptBackground != null) {
            doorPromptBackground.setVisible(visible);
        }

        if (doorPromptText != null) {
            doorPromptText.setVisible(visible);
        }
    }

    private boolean isNearDoor() {
        if (player == null
                || exitInteractionPoint == null) {
            return false;
        }

        Point2D playerCentre = new Point2D(
                player.getX()
                        + PLAYER_SIZE / 2.0,
                player.getY()
                        + PLAYER_SIZE / 2.0
        );

        return playerCentre.distance(
                exitInteractionPoint
        ) <= DOOR_INTERACTION_DISTANCE;
    }

    private void configureCamera() {
        var viewport =
                getGameScene().getViewport();

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
        moveHorizontally(movementX);
        moveVertically(movementY);
    }

    private void moveHorizontally(double amount) {
        if (amount == 0
                || player == null) {
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
        if (amount == 0
                || player == null) {
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
        Rectangle2D playerBox =
                getPlayerBox();

        boolean touchingMapCollision =
                collisionBoxes
                        .stream()
                        .anyMatch(
                                playerBox::intersects
                        );

        boolean touchingClosedDoor =
                doorState != DoorState.OPEN
                        && closedDoorCollision != null
                        && playerBox.intersects(
                                closedDoorCollision
                        );

        return touchingMapCollision
                || touchingClosedDoor;
    }

    private Rectangle2D getPlayerBox() {
        return new Rectangle2D(
                player.getX(),
                player.getY(),
                PLAYER_SIZE,
                PLAYER_SIZE
        );
    }

    private void loadMapData(String mapFile) {
        collisionBoxes.clear();

        Document document =
                loadTmxDocument(mapFile);

        Element map =
                document.getDocumentElement();

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

        mapPixelWidth =
                columns * tileWidth;

        mapPixelHeight =
                rows * tileHeight;

        currentMapDisplayName =
                readMapDisplayName(
                        document,
                        mapFile
                );

        playerSpawn =
                findRequiredPointObject(
                        document,
                        "PlayerSpawn"
                );

        exitInteractionPoint =
                findFirstObjectByType(
                        document,
                        "Exit"
                );

        loadCollisionObjects(document);

        if (CLASSROOM_MAP.equals(mapFile)) {
            targetMapFile = ICT_LAB_MAP;
            targetRoomName = "ICT Lab";
        } else {
            targetMapFile = CLASSROOM_MAP;
            targetRoomName = "Classroom";
        }
    }

    private void loadCollisionObjects(
            Document document
    ) {
        Element collisionLayer =
                findObjectGroup(
                        document,
                        "Collision"
                );

        if (collisionLayer == null) {
            throw new IllegalStateException(
                    currentMapFile
                            + " has no Collision object layer."
            );
        }

        NodeList objects =
                collisionLayer
                        .getElementsByTagName(
                                "object"
                        );

        for (int i = 0;
             i < objects.getLength();
             i++) {

            Element object =
                    (Element) objects.item(i);

            double width =
                    readDoubleAttribute(
                            object,
                            "width",
                            0
                    );

            double height =
                    readDoubleAttribute(
                            object,
                            "height",
                            0
                    );

            if (width <= 0
                    || height <= 0) {
                continue;
            }

            collisionBoxes.add(
                    new Rectangle2D(
                            readDoubleAttribute(
                                    object,
                                    "x",
                                    0
                            ),
                            readDoubleAttribute(
                                    object,
                                    "y",
                                    0
                            ),
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
                document
                        .getElementsByTagName(
                                "objectgroup"
                        );

        for (int i = 0;
             i < groups.getLength();
             i++) {

            Element group =
                    (Element) groups.item(i);

            if (groupName.equals(
                    group.getAttribute("name")
            )) {
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
                document
                        .getElementsByTagName(
                                "object"
                        );

        for (int i = 0;
             i < objects.getLength();
             i++) {

            Element object =
                    (Element) objects.item(i);

            if (!objectName.equals(
                    object.getAttribute("name")
            )) {
                continue;
            }

            return readObjectPoint(object);
        }

        throw new IllegalStateException(
                currentMapFile
                        + " has no object named "
                        + objectName
                        + "."
        );
    }

    private Point2D findFirstObjectByType(
            Document document,
            String expectedType
    ) {
        NodeList objects =
                document
                        .getElementsByTagName(
                                "object"
                        );

        for (int i = 0;
             i < objects.getLength();
             i++) {

            Element object =
                    (Element) objects.item(i);

            String type =
                    object.getAttribute("type");

            String objectClass =
                    object.getAttribute("class");

            if (expectedType.equals(type)
                    || expectedType.equals(
                    objectClass
            )) {
                return readObjectPoint(object);
            }
        }

        throw new IllegalStateException(
                currentMapFile
                        + " has no "
                        + expectedType
                        + " object."
        );
    }

    private Point2D readObjectPoint(
            Element object
    ) {
        return new Point2D(
                readDoubleAttribute(
                        object,
                        "x",
                        0
                ),
                readDoubleAttribute(
                        object,
                        "y",
                        0
                )
        );
    }

    private String readMapDisplayName(
            Document document,
            String fallbackName
    ) {
        NodeList properties =
                document
                        .getElementsByTagName(
                                "property"
                        );

        for (int i = 0;
             i < properties.getLength();
             i++) {

            Element property =
                    (Element) properties.item(i);

            if (!"displayName".equals(
                    property.getAttribute(
                            "name"
                    )
            )) {
                continue;
            }

            String value =
                    property.getAttribute(
                            "value"
                    );

            if (!value.isBlank()) {
                return value;
            }
        }

        return fallbackName;
    }

    private Document loadTmxDocument(
            String mapFile
    ) {
        String resourcePath =
                "/assets/levels/" + mapFile;

        try (InputStream inputStream =
                     getClass()
                             .getResourceAsStream(
                                     resourcePath
                             )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Could not find "
                                + resourcePath
                                + ". Copy both TMX files and both PNG tilesets "
                                + "into src/main/resources/assets/levels/."
                );
            }

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory
                            .newInstance();

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

            document
                    .getDocumentElement()
                    .normalize();

            return document;

        } catch (Exception exception) {
            throw new RuntimeException(
                    "Failed to load map: "
                            + mapFile,
                    exception
            );
        }
    }

    private int readIntAttribute(
            Element element,
            String name,
            int defaultValue
    ) {
        String value =
                element.getAttribute(name);

        if (value == null
                || value.isBlank()) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }

    private double readDoubleAttribute(
            Element element,
            String name,
            double defaultValue
    ) {
        String value =
                element.getAttribute(name);

        if (value == null
                || value.isBlank()) {
            return defaultValue;
        }

        return Double.parseDouble(value);
    }

    private void drawCollisionDebugBoxes() {
        for (Rectangle2D box
                : collisionBoxes) {

            Rectangle debugView =
                    new Rectangle(
                            box.getWidth(),
                            box.getHeight(),
                            Color.rgb(
                                    255,
                                    0,
                                    0,
                                    0.25
                            )
                    );

            debugView.setStroke(Color.RED);

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

    private void updateRoomHud() {
        if (roomHudText == null) {
            return;
        }

        roomHudText.setText(
                currentMapDisplayName
                        + "    [1] Classroom"
                        + "    [2] ICT Lab"
                        + "    [R] Reload"
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

    private enum DoorState {
        CLOSED,
        OPEN,
        LOCKED
    }

    public static void main(String[] args) {
        launch(args);
    }
}
