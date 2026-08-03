package com.silentclassroom.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.silentclassroom.SilentClassroomGame;
import com.silentclassroom.game.Room;

/**
 * First-person 3D room view.
 * WASD movement, mouse look, E to search furniture, F to hide.
 */
public class RoomScreen implements Screen {

    private static final float ROOM_W   = 9f;
    private static final float ROOM_D   = 9f;
    private static final float ROOM_H   = 3f;
    private static final float EYE_H    = 1.65f;
    private static final float HIDE_H   = 0.35f;

    private final SilentClassroomGame game;
    private final int roomId;
    private final Room room;

    // 3D
    private ModelBatch modelBatch;
    private PerspectiveCamera camera;
    private Environment environment;
    private Array<Model> ownedModels;
    private Array<ModelInstance> instances;

    // Furniture
    private static final int MAX_FURNITURE = 6;
    private float[] furnitureX  = new float[MAX_FURNITURE];
    private float[] furnitureZ  = new float[MAX_FURNITURE];
    private String[] furnitureName = new String[MAX_FURNITURE];
    private int furnitureCount = 0;
    private ModelInstance[] furnitureInsts;

    // Robot intruder
    private ModelInstance robotInst;
    private Model robotModel;
    private boolean robotInRoom = false;
    private float robotTimer   = 35f; // seconds until robot enters
    private float robotX = 0f, robotZ = -3.5f; // enters from door side
    private float robotSweepTime = 0f;

    // Player first-person state
    private float fpX = 0f;
    private float fpZ = 2.5f; // start near the door side
    private float yaw = 0f; // degrees, 0 = looking north (-Z)
    private boolean hiding = false;
    private float camY = EYE_H;

    // Search state
    private int searchingFurnitureIdx = -1;
    private float searchProgress = 0f;
    private boolean searchResult = false;
    private float msgTimer = 0f;
    private String msgText = "";
    private boolean foundGameThisSearch = false;
    private boolean awaitingGameStart = false;
    private int pendingGameType = -1;

    // HUD
    private ShapeRenderer shape;
    private ScreenViewport hudViewport;
    private float totalTime = 0f;
    private float captureFlash = 0f;
    private float robotAlertLevel = 0f;
    private float captureCooldown = 0f;

    public RoomScreen(SilentClassroomGame game, int roomId) {
        this.game   = game;
        this.roomId = roomId;
        this.room   = new Room(roomId, game.session.roomMiniGame[roomId]);

        // Restore search progress from session (persists across visits)
        this.room.searchCount = game.session.roomSearchCount[roomId];
        this.room.miniGameTriggered = game.session.miniGameFoundInRoom[roomId];

        setupCamera();
        setupEnvironment();
        buildScene();

        shape       = new ShapeRenderer();
        hudViewport = new ScreenViewport();

        Gdx.input.setCursorCatched(true);
        game.session.roomVisited[roomId] = true;
    }

    // ───────────────────────────────── SETUP ──────────────────────────────────

    private void setupCamera() {
        camera = new PerspectiveCamera(75f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.08f;
        camera.far  = 50f;
        updateCamera();
    }

    private void updateCamera() {
        camera.position.set(fpX, camY, fpZ);
        float rad = MathUtils.degreesToRadians * yaw;
        camera.direction.set(MathUtils.sin(rad), 0f, -MathUtils.cos(rad));
        camera.up.set(0f, 1f, 0f);
        camera.update();
    }

    private void setupEnvironment() {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.35f, 0.33f, 0.3f, 1f));

        boolean isLab = room.type == Room.Type.LAB;
        if (isLab) {
            environment.add(new DirectionalLight().set(0.5f, 0.55f, 0.6f, -0.3f, -1f, -0.2f));
            environment.add(new PointLight().set(0.5f, 0.6f, 0.7f, 0f, 2.5f, 0f, 20f));
        } else {
            environment.add(new DirectionalLight().set(0.7f, 0.65f, 0.55f, -0.3f, -1f, -0.2f));
            environment.add(new PointLight().set(0.7f, 0.65f, 0.6f, 0f, 2.8f, 0f, 18f));
        }
    }

    private void buildScene() {
        modelBatch   = new ModelBatch();
        ownedModels  = new Array<>();
        instances    = new Array<>();
        ModelBuilder mb = new ModelBuilder();
        long attr = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        boolean isLab = room.type == Room.Type.LAB;

        // ── Floor (original classroom / ICT-lab tileset textures) ──
        addTexturedBox(mb, ROOM_W, 0.08f, ROOM_D,
            isLab ? "lab-floor" : "class-floor", ROOM_W, ROOM_D,
            0f, -0.04f, 0f);

        // ── Ceiling ──
        Color ceilCol = isLab
            ? new Color(0.38f, 0.4f, 0.45f, 1f)
            : new Color(0.88f, 0.87f, 0.84f, 1f);
        addBox(mb, attr, ROOM_W, 0.08f, ROOM_D, ceilCol, 0f, ROOM_H + 0.04f, 0f);

        // ── Ceiling light panel ──
        addBox(mb, attr, 1.2f, 0.04f, 0.5f, new Color(0.95f, 0.95f, 1f, 1f), 0f, ROOM_H - 0.02f, 0f);

        // ── Walls (original brick tileset) ──
        String wallTile = isLab ? "hall-brick" : "class-brick";
        // North wall (board side)
        addTexturedBox(mb, ROOM_W, ROOM_H, 0.12f, wallTile, ROOM_W, ROOM_H,
            0f, ROOM_H/2f, -ROOM_D/2f - 0.06f);
        // South wall (door side)
        addTexturedBox(mb, ROOM_W, ROOM_H, 0.12f, wallTile, ROOM_W, ROOM_H,
            0f, ROOM_H/2f,  ROOM_D/2f + 0.06f);
        // Left wall
        addTexturedBox(mb, 0.12f, ROOM_H, ROOM_D, wallTile, ROOM_D, ROOM_H,
            -ROOM_W/2f - 0.06f, ROOM_H/2f, 0f);
        // Right wall
        addTexturedBox(mb, 0.12f, ROOM_H, ROOM_D, wallTile, ROOM_D, ROOM_H,
             ROOM_W/2f + 0.06f, ROOM_H/2f, 0f);

        // ── Board / front display (original board artwork) ──
        if (!isLab) {
            // Classroom: green blackboard art from tileset
            addTexturedBox(mb, ROOM_W - 1.5f, 1.4f, 0.06f, "greenboard", 1f, 1f,
                0f, 2f, -ROOM_D/2f + 0.04f);
            // Board trim
            addBox(mb, attr, ROOM_W - 1.5f, 0.08f, 0.08f,
                new Color(0.5f, 0.35f, 0.18f, 1f), 0f, 1.25f, -ROOM_D/2f + 0.06f);
        } else {
            // Lab: whiteboard art from ICT lab tileset
            addTexturedBox(mb, ROOM_W - 1.5f, 1.2f, 0.06f, "whiteboard", 1f, 1f,
                0f, 2f, -ROOM_D/2f + 0.04f);
        }

        // ── Teacher/Instructor desk at front ──
        addDesk(mb, attr, 0f, -3.0f, isLab,
            new Color(0.45f, 0.3f, 0.18f, 1f));

        // ── Student/Lab desks (4 desks = 2 rows × 2 cols) ──
        furnitureCount = 0;
        furnitureInsts = new ModelInstance[MAX_FURNITURE];

        float[][] deskPos = {{-2f,-1f},{2f,-1f},{-2f,1f},{2f,1f}};
        Color deskC = isLab
            ? new Color(0.2f, 0.35f, 0.38f, 1f)
            : new Color(0.48f, 0.32f, 0.18f, 1f);

        for (float[] dp : deskPos) {
            addSearchableFurniture(mb, attr, dp[0], dp[1], deskC,
                isLab ? "Lab Bench" : "Student Desk");
        }

        // ── Shelving / cabinets at back ──
        Color cabC = isLab
            ? new Color(0.22f, 0.28f, 0.35f, 1f)
            : new Color(0.38f, 0.28f, 0.16f, 1f);
        addSearchableFurniture(mb, attr, -3.5f, -3.8f, cabC,
            isLab ? "Equipment Rack" : "Bookshelf");

        // ── Room-specific decorative props ──
        if (isLab) {
            // Fume hood at back-right
            addBox(mb, attr, 1.2f, 2f, 0.7f,
                new Color(0.25f, 0.3f, 0.32f, 1f), 3.5f, 1f, -3.5f);
            addBox(mb, attr, 1.2f, 0.06f, 0.7f,
                new Color(0.7f, 0.8f, 0.9f, 1f), 3.5f, 2.06f, -3.5f);
            // Lab stools (chairs)
            for (float[] dp : deskPos) {
                addBox(mb, attr, 0.35f, 0.7f, 0.35f,
                    new Color(0.18f, 0.18f, 0.2f, 1f), dp[0] + 0.3f, 0.35f, dp[1] + 0.7f);
            }
        } else {
            // Classroom: student chairs
            for (float[] dp : deskPos) {
                // Chair seat
                addBox(mb, attr, 0.5f, 0.08f, 0.5f,
                    new Color(0.55f, 0.35f, 0.15f, 1f), dp[0] + 0.3f, 0.45f, dp[1] + 0.7f);
                // Chair back
                addBox(mb, attr, 0.5f, 0.55f, 0.06f,
                    new Color(0.5f, 0.3f, 0.12f, 1f), dp[0] + 0.3f, 0.75f, dp[1] + 0.95f);
            }
        }

        // ── Door indicator (original door artwork, where player entered) ──
        addTexturedBox(mb, 1.3f, 2.3f, 0.08f, "door", 1f, 1f,
            0f, 1.15f, ROOM_D / 2f - 0.04f);

        // ── Robot: kernel-panic boss artwork billboard (initially outside) ──
        robotModel = game.assets.spriteQuad(mb, 1.5f, 1.9f,
            game.assets.spriteMaterial(game.assets.robot()));
        ownedModels.add(robotModel);
        robotInst = new ModelInstance(robotModel);
        robotInst.transform.setToTranslation(0f, 0.02f, 8f); // off-screen initially
    }

    /** Helper: create and add a static box textured with original tileset art. */
    private void addTexturedBox(ModelBuilder mb, float w, float h, float d,
                                String tileName, float repU, float repV,
                                float x, float y, float z) {
        Model m = game.assets.texturedBox(mb, w, h, d,
            game.assets.texturedMaterial(game.assets.tile(tileName)), repU, repV);
        ownedModels.add(m);
        ModelInstance inst = new ModelInstance(m);
        inst.transform.setToTranslation(x, y, z);
        instances.add(inst);
    }

    /** Helper: create and add a static box. */
    private void addBox(ModelBuilder mb, long attr, float w, float h, float d,
                        Color c, float x, float y, float z) {
        Model m = mb.createBox(w, h, d, new Material(ColorAttribute.createDiffuse(c)), attr);
        ownedModels.add(m);
        ModelInstance inst = new ModelInstance(m);
        inst.transform.setToTranslation(x, y, z);
        instances.add(inst);
    }

    /** Helper: add a desk at (x,z) with legs. */
    private void addDesk(ModelBuilder mb, long attr, float x, float z,
                          boolean isLab, Color topColor) {
        // Top surface (original wood artwork)
        Model topM = mb.createBox(1.6f, 0.08f, 0.9f,
            new Material(
                TextureAttribute.createDiffuse(game.assets.tile("wood")),
                ColorAttribute.createDiffuse(topColor)),
            attr | VertexAttributes.Usage.TextureCoordinates);
        ownedModels.add(topM);
        ModelInstance top = new ModelInstance(topM);
        top.transform.setToTranslation(x, 0.8f, z);
        instances.add(top);
        // Legs
        Color legC = new Color(topColor).mul(0.7f);
        for (int lx = -1; lx <= 1; lx += 2) for (int lz = -1; lz <= 1; lz += 2) {
            Model legM = mb.createBox(0.07f, 0.76f, 0.07f,
                new Material(ColorAttribute.createDiffuse(legC)), attr);
            ownedModels.add(legM);
            ModelInstance leg = new ModelInstance(legM);
            leg.transform.setToTranslation(x + lx * 0.72f, 0.38f, z + lz * 0.38f);
            instances.add(leg);
        }
    }

    /** Helper: add a searchable furniture piece with legs. */
    private void addSearchableFurniture(ModelBuilder mb, long attr,
                                         float x, float z, Color topColor, String name) {
        if (furnitureCount >= MAX_FURNITURE) return;
        int idx = furnitureCount;
        furnitureX[idx] = x;
        furnitureZ[idx] = z;
        furnitureName[idx] = name;
        furnitureCount++;

        // Top (original wood artwork, tinted per room type)
        Model topM = mb.createBox(1.5f, 0.09f, 0.85f,
            new Material(
                TextureAttribute.createDiffuse(game.assets.tile("wood")),
                ColorAttribute.createDiffuse(topColor)),
            attr | VertexAttributes.Usage.TextureCoordinates);
        ownedModels.add(topM);
        ModelInstance topInst = new ModelInstance(topM);
        topInst.transform.setToTranslation(x, 0.8f, z);
        instances.add(topInst);
        furnitureInsts[idx] = topInst; // store for highlight

        // Under-desk body (hiding spot visual)
        Color underCol = new Color(topColor).mul(0.5f);
        Model underM = mb.createBox(1.4f, 0.74f, 0.8f,
            new Material(ColorAttribute.createDiffuse(underCol)), attr);
        ownedModels.add(underM);
        ModelInstance under = new ModelInstance(underM);
        under.transform.setToTranslation(x, 0.37f, z);
        instances.add(under);

        // Legs
        Color legC = new Color(topColor).mul(0.65f);
        for (int lx = -1; lx <= 1; lx += 2) for (int lz = -1; lz <= 1; lz += 2) {
            Model legM = mb.createBox(0.07f, 0.76f, 0.07f,
                new Material(ColorAttribute.createDiffuse(legC)), attr);
            ownedModels.add(legM);
            ModelInstance leg = new ModelInstance(legM);
            leg.transform.setToTranslation(x + lx * 0.66f, 0.38f, z + lz * 0.36f);
            instances.add(leg);
        }
    }

    // ───────────────────────────────── UPDATE ─────────────────────────────────

    @Override
    public void render(float delta) {
        totalTime += delta;
        captureFlash = Math.max(0f, captureFlash - delta * 3f);
        msgTimer     = Math.max(0f, msgTimer - delta);

        game.session.update(delta);
        if (game.session.isGameOver()) { exitRoom(); game.toGameOver(false); return; }

        handleInput(delta);
        updateRobot(delta);
        updateCamera();

        // Highlight nearest furniture
        int nearIdx = findNearestFurniture();
        for (int i = 0; i < furnitureCount; i++) {
            Material mat = furnitureInsts[i].materials.first();
            ColorAttribute ca = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
            if (ca != null) {
                float boost = (i == nearIdx) ? 1.5f : 1f;
                ca.color.r = Math.min(1f, ca.color.r * boost);
                ca.color.g = Math.min(1f, ca.color.g * boost);
            }
        }

        // Update robot position
        if (robotInRoom) {
            float sweepX = (float) Math.sin(robotSweepTime * 1.1f) * 2.5f;
            float sweepZ = (float) Math.cos(robotSweepTime * 0.7f) * 2.0f;
            // Sprite quad is anchored at its base; keep it facing the player
            robotInst.transform.setToTranslation(sweepX, 0.02f, sweepZ);
            robotInst.transform.rotate(Vector3.Y,
                MathUtils.atan2(fpX - sweepX, fpZ - sweepZ) * MathUtils.radiansToDegrees);
        }

        // ── Render 3D ──
        Gdx.gl.glClearColor(0.06f, 0.05f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        modelBatch.begin(camera);
        for (ModelInstance inst : instances) modelBatch.render(inst, environment);
        if (robotInRoom) modelBatch.render(robotInst, environment);
        modelBatch.end();

        renderHUD();
    }

    private void handleInput(float delta) {
        // Mouse look
        float mouseDX = Gdx.input.getDeltaX() * 0.25f;
        yaw += mouseDX;

        // Movement
        float speed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ? 4f : 2.5f;
        float rad = MathUtils.degreesToRadians * yaw;
        float dirX = MathUtils.sin(rad);
        float dirZ = -MathUtils.cos(rad);
        float rightX = MathUtils.cos(rad);
        float rightZ = MathUtils.sin(rad);

        float mvX = 0, mvZ = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) { mvX += dirX; mvZ += dirZ; }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) { mvX -= dirX; mvZ -= dirZ; }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) { mvX -= rightX; mvZ -= rightZ; }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) { mvX += rightX; mvZ += rightZ; }

        float len = (float) Math.sqrt(mvX * mvX + mvZ * mvZ);
        if (len > 0) { mvX /= len; mvZ /= len; }

        float nx = fpX + mvX * speed * delta;
        float nz = fpZ + mvZ * speed * delta;
        // Room bounds (slightly inside walls)
        fpX = Math.max(-ROOM_W/2f + 0.4f, Math.min(ROOM_W/2f - 0.4f, nx));
        fpZ = Math.max(-ROOM_D/2f + 0.4f, Math.min(ROOM_D/2f - 0.4f, nz));

        // Smooth camera height (hide = crouch)
        float targetCamY = hiding ? HIDE_H : EYE_H;
        camY += (targetCamY - camY) * Math.min(1f, delta * 8f);

        // F = toggle hide (must be near furniture)
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            int ni = findNearestFurniture();
            if (ni >= 0) {
                hiding = !hiding;
                game.session.playerHiding = hiding;
                if (hiding) { msgText = "Hiding under " + furnitureName[ni]; msgTimer = 2f; }
                else        { msgText = ""; }
            }
        }

        // E = interact / search nearest furniture
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            if (awaitingGameStart) {
                // Launch the mini-game
                exitRoom();
                game.toMiniGame(pendingGameType, roomId);
                return;
            }
            int ni = findNearestFurniture();
            if (ni >= 0) {
                triggerSearch(ni);
            }
        }

        // ESC = exit room
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            exitRoom();
            game.toHallway();
        }
    }

    private void triggerSearch(int furnitureIdx) {
        // Terminal already discovered here: allow retry until its mini-game is complete
        if (room.hasGame() && game.session.miniGameFoundInRoom[roomId]) {
            if (game.session.miniGameComplete[room.miniGameType]) {
                msgText = "Terminal already cleared.";
                msgTimer = 2f;
            } else {
                msgText = "Terminal found — Press E to start mini-game";
                msgTimer = 6f;
                awaitingGameStart = true;
                pendingGameType   = room.miniGameType;
            }
            return;
        }
        if (!room.canSearch()) {
            msgText = "You've searched everything here.";
            msgTimer = 2f;
            return;
        }
        boolean found = room.searchNext();
        game.session.roomSearchCount[roomId] = room.searchCount;
        game.session.onSearch(found);
        if (found) {
            game.session.miniGameFoundInRoom[roomId] = true;
            msgText = "You found a GLITCH TOKEN! [" + room.name + "] — Press E to start mini-game";
            msgTimer = 6f;
            awaitingGameStart = true;
            pendingGameType   = room.miniGameType;
        } else {
            String[] msgs = {"Nothing here.", "Just textbooks.", "Empty drawer.", "Some papers...", "Dust."};
            msgText = msgs[furnitureIdx % msgs.length];
            msgTimer = 1.5f;
        }
    }

    /** Find the furniture index within interaction range, or -1. */
    private int findNearestFurniture() {
        int best = -1;
        float bestDist = 2.2f;
        for (int i = 0; i < furnitureCount; i++) {
            float dx = fpX - furnitureX[i];
            float dz = fpZ - furnitureZ[i];
            float dist = (float) Math.sqrt(dx*dx + dz*dz);
            if (dist < bestDist) { bestDist = dist; best = i; }
        }
        return best;
    }

    private void updateRobot(float delta) {
        robotTimer -= delta;
        if (!robotInRoom && robotTimer <= 0f) {
            robotInRoom = true;
            msgText = "ROBOT DETECTED IN ROOM!";
            msgTimer = 3f;
        }
        if (robotInRoom) {
            robotSweepTime += delta;
            // Check if robot catches player
            float sweepX = (float) Math.sin(robotSweepTime * 1.1f) * 2.5f;
            float sweepZ = (float) Math.cos(robotSweepTime * 0.7f) * 2.0f;
            float dx = fpX - sweepX, dz = fpZ - sweepZ;
            float dist = (float) Math.sqrt(dx*dx + dz*dz);
            captureCooldown = Math.max(0f, captureCooldown - delta);
            if (dist < 1.5f && !hiding && captureCooldown <= 0f) {
                captureFlash = 1f;
                captureCooldown = 3f; // invulnerability window after capture
                game.session.onCapture();
                msgText = "CAUGHT! HP: " + game.session.hp;
                msgTimer = 2.5f;
                hiding = false;
                game.session.playerHiding = false;
                // Push the player back toward the door, away from the robot
                fpX = 0f; fpZ = ROOM_D / 2f - 0.6f;
                if (game.session.isGameOver()) { exitRoom(); game.toGameOver(false); return; }
            }
            // Robot leaves after 20 seconds
            if (robotSweepTime > 20f) {
                robotInRoom = false;
                robotSweepTime = 0f;
                robotTimer = 40f;
                msgText = "Robot left the room.";
                msgTimer = 2f;
            }
            robotAlertLevel = Math.min(1f, robotAlertLevel + delta * 2f);
        } else {
            robotAlertLevel = Math.max(0f, robotAlertLevel - delta * 1.5f);
        }
    }

    private void exitRoom() {
        Gdx.input.setCursorCatched(false);
        hiding = false;
        game.session.playerHiding = false;
    }

    // ───────────────────────────────── HUD ────────────────────────────────────

    private void renderHUD() {
        hudViewport.apply();
        int W = Gdx.graphics.getWidth();
        int H = Gdx.graphics.getHeight();
        shape.setProjectionMatrix(hudViewport.getCamera().combined);

        // Robot alert overlay
        if (robotAlertLevel > 0.01f) {
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(1f, 0.1f, 0.1f, robotAlertLevel * 0.2f);
            shape.rect(0, 0, W, H);
            shape.end();
        }

        // Capture flash
        if (captureFlash > 0.01f) {
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(1f, 0f, 0f, captureFlash * 0.6f);
            shape.rect(0, 0, W, H);
            shape.end();
        }

        // Crosshair
        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setColor(1f, 1f, 1f, 0.7f);
        shape.line(W/2f - 10, H/2f, W/2f + 10, H/2f);
        shape.line(W/2f, H/2f - 10, W/2f, H/2f + 10);
        shape.end();

        // Top HUD bar
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.05f, 0.05f, 0.1f, 0.8f);
        shape.rect(0, H - 36, W, 36);
        float frac = game.session.getTimeFraction();
        float r = frac > 0.5f ? 0.1f : (frac > 0.25f ? 0.9f : 1f);
        float g = frac > 0.5f ? 0.9f : (frac > 0.25f ? 0.6f : 0.1f);
        shape.setColor(r, g, 0.1f, 1f);
        shape.rect(2, H - 34, (W - 4) * frac, 32);
        shape.end();

        // Bottom info bar
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.04f, 0.04f, 0.08f, 0.85f);
        shape.rect(0, 0, W, 50);
        // HP
        for (int i = 0; i < 3; i++) {
            shape.setColor(i < game.session.hp ? new Color(0.1f, 1f, 0.3f, 1f) : new Color(0.3f, 0.3f, 0.35f, 1f));
            shape.circle(22 + i * 28, 25, 10, 12);
        }
        // Token slots
        for (int i = 0; i < 3; i++) {
            shape.setColor(i < game.session.tokensFound ? new Color(0.9f, 0.8f, 0.1f, 1f) : new Color(0.2f, 0.2f, 0.28f, 1f));
            shape.circle(W/2f - 28 + i*28, 25, 10, 12);
        }
        shape.end();

        game.batch.begin();

        // Room name & timer
        game.font.setColor(0.8f, 0.9f, 1f, 1f);
        game.font.draw(game.batch, room.name, 12, H - 10);
        game.font.setColor(1f, 1f, 1f, 1f);
        game.font.draw(game.batch, game.session.getTimeString(), W/2f - 22, H - 10);

        // HP / token labels
        game.font.setColor(0.6f, 0.9f, 0.6f, 0.8f);
        game.font.draw(game.batch, "HP", 8, 48);
        game.font.draw(game.batch, "TOKENS", W/2f - 52, 48);

        // Score
        game.font.setColor(0.8f, 0.8f, 0.3f, 1f);
        game.font.draw(game.batch, "SCORE: " + game.session.score, W - 185, 42);

        // Search progress / message
        if (msgTimer > 0f) {
            float alpha = Math.min(1f, msgTimer * 2f);
            game.font.setColor(awaitingGameStart ? 0f : 0.9f,
                               awaitingGameStart ? 1f : 0.9f,
                               awaitingGameStart ? 0.5f : 0.9f, alpha);
            game.font.draw(game.batch, msgText, W/2f - 260, H/2f + 80);
        }

        // Hide status
        if (hiding) {
            game.font.setColor(0.2f, 0.7f, 1f, 1f);
            game.font.draw(game.batch, "[ HIDING ]", W/2f - 50, H/2f - 80);
        }

        // Robot in room warning
        if (robotInRoom) {
            float blink = (float)(0.5 + 0.5 * Math.sin(totalTime * 8f));
            game.font.setColor(1f, 0.2f, 0.2f, blink);
            game.font.draw(game.batch, "! ROBOT IN ROOM !", W - 210, H - 10);
        }

        // Interaction hint
        int ni = findNearestFurniture();
        if (ni >= 0) {
            game.font.setColor(0.4f, 0.9f, 1f, 0.9f);
            game.font.draw(game.batch, "[E] Search: " + furnitureName[ni] + "   [F] Hide", W/2f - 160, H/2f - 100);
        }
        if (!robotInRoom) {
            game.font.setColor(0.4f, 0.6f, 0.4f, 0.65f);
            game.font.draw(game.batch, "[ESC] Exit room", 12, 16);
        }
        game.font.setColor(0.4f, 0.4f, 0.5f, 0.7f);
        game.font.draw(game.batch, "WASD move  Mouse look", W - 255, 16);

        game.batch.end();
    }

    // ───────────────────────────────── LIFECYCLE ──────────────────────────────

    @Override public void show() { Gdx.input.setCursorCatched(true); }
    @Override public void resize(int w, int h) {
        hudViewport.update(w, h, true);
        camera.viewportWidth = w; camera.viewportHeight = h; camera.update();
    }
    @Override public void pause() { game.session.paused = true; }
    @Override public void resume() { game.session.paused = false; }
    @Override public void hide() { Gdx.input.setCursorCatched(false); }
    @Override public void dispose() {
        modelBatch.dispose(); shape.dispose();
        for (Model m : ownedModels) m.dispose();
    }
}
