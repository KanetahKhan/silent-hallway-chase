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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.silentclassroom.SilentClassroomGame;
import com.silentclassroom.game.Room;
import com.silentclassroom.game.RobotAI;

/**
 * Top-down 3D hallway: player runs through a 60-unit corridor,
 * robot patrols and chases. 7 doors lead to rooms.
 */
public class HallwayScreen implements Screen {

    private static final float WALL_X = 2.1f;
    private static final float HALL_LEN = 60f;
    private static final float CEIL_H = 3.2f;
    private static final float DOOR_W = 1.4f;
    private static final float DOOR_H = 2.4f;

    private final SilentClassroomGame game;
    private final RobotAI robot;
    private final Room[] rooms;

    // 3D
    private ModelBatch modelBatch;
    private PerspectiveCamera camera;
    private Environment environment;
    private Array<Model> ownedModels;
    private Array<ModelInstance> staticInstances;
    private ModelInstance[] doorInstances;  // door panels (7)
    private ModelInstance playerInst;
    private ModelInstance robotBodyInst;
    private ModelInstance robotEyeInst;
    private Model playerModel;
    private Model robotBodyModel;
    private Model robotEyeModel;
    private String playerFacing = "south";

    // 2D HUD
    private ShapeRenderer shape;
    private ScreenViewport hudViewport;

    // Gameplay state
    private int nearDoorId = -1;
    private float captureFlash = 0f;
    private float alertFlash = 0f;
    private float totalTime = 0f;
    private String captureMsg = "";
    private float captureMsgTimer = 0f;
    private float doorPromptAlpha = 0f;
    private float captureCooldown = 0f;

    // Door model references
    private final Model[] doorPanelModels = new Model[7];

    public HallwayScreen(SilentClassroomGame game) {
        this.game = game;
        this.robot = new RobotAI();
        this.rooms = buildRooms();

        setupCamera();
        setupEnvironment();
        buildScene();
        shape = new ShapeRenderer();
        hudViewport = new ScreenViewport();
    }

    private Room[] buildRooms() {
        Room[] r = new Room[7];
        for (int i = 0; i < 7; i++) r[i] = new Room(i, game.session.roomMiniGame[i]);
        return r;
    }

    // ─────────────────────────────────────── SETUP ─────────────────────────

    private void setupCamera() {
        camera = new PerspectiveCamera(55f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far  = 120f;
        updateCamera();
    }

    private void updateCamera() {
        float px = game.session.playerX;
        float pz = game.session.playerZ;
        camera.position.set(px, 11f, pz + 5.5f);
        camera.lookAt(px, 0f, pz - 2f);
        camera.up.set(0f, 1f, 0f);
        camera.update();
    }

    private void setupEnvironment() {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.25f, 0.25f, 0.30f, 1f));
        environment.add(new DirectionalLight().set(0.6f, 0.6f, 0.65f, -0.5f, -1f, -0.3f));
        // Corridor ceiling strip lights (point lights)
        for (int i = -2; i <= 2; i++) {
            environment.add(new PointLight().set(0.4f, 0.42f, 0.5f, 0f, 2.8f, i * 12f, 18f));
        }
    }

    private void buildScene() {
        modelBatch    = new ModelBatch();
        ownedModels   = new Array<>();
        staticInstances = new Array<>();
        doorInstances  = new ModelInstance[7];
        ModelBuilder mb = new ModelBuilder();

        long attr = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        // ── Floor (original school tileset texture) ──
        Model floorM = game.assets.texturedBox(mb, 4f, 0.1f, HALL_LEN,
            game.assets.texturedMaterial(game.assets.tile("hall-floor")), 4f, HALL_LEN);
        ownedModels.add(floorM);
        ModelInstance floorInst = new ModelInstance(floorM);
        floorInst.transform.setToTranslation(0f, -0.05f, 0f);
        staticInstances.add(floorInst);

        // ── Ceiling ──
        Model ceilM = mb.createBox(4f, 0.1f, HALL_LEN,
            new Material(ColorAttribute.createDiffuse(0.55f, 0.55f, 0.6f, 1f)), attr);
        ownedModels.add(ceilM);
        ModelInstance ceilInst = new ModelInstance(ceilM);
        ceilInst.transform.setToTranslation(0f, CEIL_H + 0.05f, 0f);
        staticInstances.add(ceilInst);

        // ── Ceiling strip lights ──
        Model lightStripM = mb.createBox(0.3f, 0.05f, 1.5f,
            new Material(ColorAttribute.createDiffuse(0.95f, 0.95f, 1f, 1f)), attr);
        ownedModels.add(lightStripM);
        for (int i = -2; i <= 2; i++) {
            ModelInstance ls = new ModelInstance(lightStripM);
            ls.transform.setToTranslation(0f, CEIL_H, i * 12f);
            staticInstances.add(ls);
        }

        // ── Walls (segmented to allow door openings) ──
        buildSegmentedWalls(mb, attr);

        // ── End caps (brick from original tileset) ──
        Model northWallM = game.assets.texturedBox(mb, 4.4f, CEIL_H, 0.2f,
            game.assets.texturedMaterial(game.assets.tile("hall-brick")), 4.4f, CEIL_H);
        ownedModels.add(northWallM);
        ModelInstance nw = new ModelInstance(northWallM);
        nw.transform.setToTranslation(0f, CEIL_H / 2f, -HALL_LEN / 2f - 0.1f);
        staticInstances.add(nw);

        Model southWallM = game.assets.texturedBox(mb, 4.4f, CEIL_H, 0.2f,
            game.assets.texturedMaterial(game.assets.tile("hall-brick")), 4.4f, CEIL_H);
        ownedModels.add(southWallM);
        ModelInstance sw = new ModelInstance(southWallM);
        sw.transform.setToTranslation(0f, CEIL_H / 2f, HALL_LEN / 2f + 0.1f);
        staticInstances.add(sw);

        // ── Exit door (north, original door art with gold tint) ──
        Model exitM = mb.createBox(1.4f, DOOR_H, 0.15f,
            new Material(
                TextureAttribute.createDiffuse(game.assets.tile("door")),
                ColorAttribute.createDiffuse(1f, 0.85f, 0.4f, 1f)),
            attr | VertexAttributes.Usage.TextureCoordinates);
        ownedModels.add(exitM);
        ModelInstance exitInst = new ModelInstance(exitM);
        exitInst.transform.setToTranslation(0f, DOOR_H / 2f, -HALL_LEN / 2f + 0.05f);
        staticInstances.add(exitInst);

        // ── Door panels (7 rooms, original door artwork) ──
        for (int i = 0; i < 7; i++) {
            Color col = (i < 4)
                ? new Color(0.75f, 0.8f, 1f, 1f)   // classroom: cool tint
                : new Color(0.85f, 0.75f, 1f, 1f); // lab: purple tint
            Model doorM = mb.createBox(0.12f, DOOR_H, DOOR_W,
                new Material(
                    TextureAttribute.createDiffuse(game.assets.tile("door")),
                    ColorAttribute.createDiffuse(col)),
                attr | VertexAttributes.Usage.TextureCoordinates);
            doorPanelModels[i] = doorM;
            ownedModels.add(doorM);
            ModelInstance di = new ModelInstance(doorM);
            float dx = (Room.DOOR_X[i] > 0) ? WALL_X + 0.01f : -WALL_X - 0.01f;
            di.transform.setToTranslation(dx, DOOR_H / 2f, Room.DOOR_Z[i]);
            doorInstances[i] = di;
            staticInstances.add(di);
        }

        // ── Floor arrow markers at doors ──
        Model arrowM = mb.createBox(0.15f, 0.02f, 0.6f,
            new Material(ColorAttribute.createDiffuse(0.5f, 0.5f, 0.8f, 1f)), attr);
        ownedModels.add(arrowM);
        for (int i = 0; i < 7; i++) {
            ModelInstance ar = new ModelInstance(arrowM);
            ar.transform.setToTranslation(Room.DOOR_X[i] * 0.8f, 0.02f, Room.DOOR_Z[i]);
            staticInstances.add(ar);
        }

        // ── Player: Ayan sprite billboard (original vp_game character art) ──
        playerModel = game.assets.spriteQuad(mb, 1.3f, 1.9f,
            game.assets.spriteMaterial(game.assets.ayan("south")));
        ownedModels.add(playerModel);
        playerInst = new ModelInstance(playerModel);

        // ── Robot: kernel-panic boss artwork billboard ──
        robotBodyModel = game.assets.spriteQuad(mb, 1.5f, 1.9f,
            game.assets.spriteMaterial(game.assets.robot()));
        ownedModels.add(robotBodyModel);
        robotBodyInst = new ModelInstance(robotBodyModel);

        // ── Robot eye (red light) ──
        robotEyeModel = mb.createBox(0.25f, 0.12f, 0.08f,
            new Material(ColorAttribute.createDiffuse(1f, 0.05f, 0.05f, 1f)), attr);
        ownedModels.add(robotEyeModel);
        robotEyeInst = new ModelInstance(robotEyeModel);
    }

    /** Build left and right walls with openings for each door. */
    private void buildSegmentedWalls(ModelBuilder mb, long attr) {
        Color wallColor = new Color(0.48f, 0.48f, 0.56f, 1f);

        // Left wall doors at Z: -22, -6, 10, 26 (rooms 0,2,4,6)
        float[] leftDoorZ  = {Room.DOOR_Z[0], Room.DOOR_Z[2], Room.DOOR_Z[4], Room.DOOR_Z[6]};
        // Right wall doors at Z: -14, 2, 18 (rooms 1,3,5)
        float[] rightDoorZ = {Room.DOOR_Z[1], Room.DOOR_Z[3], Room.DOOR_Z[5]};

        addWallSegments(mb, attr, -WALL_X, leftDoorZ,  wallColor, true);
        addWallSegments(mb, attr,  WALL_X, rightDoorZ, wallColor, false);

        // Door frames (trim strips beside openings)
        Color trimColor = new Color(0.3f, 0.3f, 0.38f, 1f);
        for (int i = 0; i < 7; i++) {
            float doorZi = Room.DOOR_Z[i];
            float wx = (Room.DOOR_X[i] > 0) ? WALL_X : -WALL_X;
            // Lintel above door
            Model lintelM = mb.createBox(0.2f, 0.35f, DOOR_W + 0.2f,
                new Material(ColorAttribute.createDiffuse(trimColor)), attr);
            ownedModels.add(lintelM);
            ModelInstance li = new ModelInstance(lintelM);
            li.transform.setToTranslation(wx, DOOR_H + 0.175f, doorZi);
            staticInstances.add(li);
        }
    }

    private void addWallSegments(ModelBuilder mb, long attr,
                                  float wallX, float[] doorZs,
                                  Color wallColor, boolean isLeft) {
        float[] zBoundaries = buildBoundaries(doorZs);
        for (int seg = 0; seg < zBoundaries.length - 1; seg++) {
            float z0 = zBoundaries[seg];
            float z1 = zBoundaries[seg + 1];
            float len = z1 - z0;
            if (len < 0.01f) continue;
            Model segM = game.assets.texturedBox(mb, 0.2f, CEIL_H, len,
                game.assets.texturedMaterial(game.assets.tile("hall-brick")),
                len, CEIL_H);
            ownedModels.add(segM);
            ModelInstance si = new ModelInstance(segM);
            si.transform.setToTranslation(wallX, CEIL_H / 2f, (z0 + z1) / 2f);
            staticInstances.add(si);
        }
    }

    /** Build sorted boundary array merging corridor ends + door gaps. */
    private float[] buildBoundaries(float[] doorZs) {
        float half = DOOR_W / 2f + 0.05f;
        float start = -HALL_LEN / 2f;
        float end   =  HALL_LEN / 2f;
        // Collect all boundary points
        float[] pts = new float[2 + doorZs.length * 2];
        pts[0] = start; pts[1] = end;
        int idx = 2;
        for (float dz : doorZs) {
            pts[idx++] = dz - half; // gap start
            pts[idx++] = dz + half; // gap end
        }
        // Sort
        java.util.Arrays.sort(pts);
        return pts;
    }

    // ─────────────────────────────────────── UPDATE ─────────────────────────

    @Override
    public void render(float delta) {
        totalTime += delta;
        captureFlash = Math.max(0f, captureFlash - delta * 3f);
        alertFlash   = Math.max(0f, alertFlash   - delta * 2f);
        captureMsgTimer = Math.max(0f, captureMsgTimer - delta);

        // Update session timer
        game.session.update(delta);
        if (game.session.isGameOver()) { game.toGameOver(false); return; }

        // Move player
        updatePlayerMovement(delta);

        // Update robot
        robot.update(delta, game.session.playerX, game.session.playerZ, game.session.playerHiding);

        // Check capture (with post-capture invulnerability cooldown)
        captureCooldown = Math.max(0f, captureCooldown - delta);
        if (captureCooldown <= 0f
                && robot.isCatchingPlayer(game.session.playerX, game.session.playerZ, game.session.playerHiding)) {
            game.session.onCapture();
            captureFlash = 1f;
            captureMsg = "CAUGHT!  -150 pts  -20 sec  HP:" + game.session.hp;
            captureMsgTimer = 2.5f;
            captureCooldown = 3f;
            robot.resetAfterCapture(game.session.playerZ);
            if (game.session.isGameOver()) { game.toGameOver(false); return; }
        }

        // Robot alert flash
        if (robot.isAlerting()) alertFlash = Math.min(alertFlash + delta * 4f, 1f);

        // Check near door / exit
        scanNearby();

        // Check exit reached
        if (game.session.isWon() && Math.abs(game.session.playerZ + 29f) < 1.5f) {
            game.toGameOver(true);
            return;
        }

        // Update model positions (sprite quads are anchored at their base)
        playerInst.transform.setToTranslation(game.session.playerX, 0.02f, game.session.playerZ);
        robotBodyInst.transform.setToTranslation(robot.x, 0.02f, robot.z);
        robotEyeInst.transform.setToTranslation(robot.x + (robot.facingDir * 0.25f), 1.5f, robot.z - 0.24f);
        updateCamera();

        // Update door panel tint (highlight nearby)
        for (int i = 0; i < 7; i++) {
            boolean near = (i == nearDoorId);
            float brightness = near ? 1.25f : 0.7f;
            boolean isClass = i < 4;
            ((ColorAttribute) doorPanelModels[i].materials.first()
                .get(ColorAttribute.Diffuse))
                .color.set(
                    (isClass ? 0.75f : 0.85f) * brightness,
                    (isClass ? 0.8f : 0.75f) * brightness,
                    1f * brightness,
                    1f);
        }

        // Update robot eye colour by state
        float ec = robot.state == RobotAI.State.CHASE ? 1f : (robot.state == RobotAI.State.ALERT ? 0.7f : 0.3f);
        ((ColorAttribute) robotEyeModel.materials.first().get(ColorAttribute.Diffuse)).color.set(ec, 0.05f, 0.05f, 1f);

        // ── 3D Render ──
        Gdx.gl.glClearColor(0.04f, 0.04f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        modelBatch.begin(camera);
        for (ModelInstance inst : staticInstances) modelBatch.render(inst, environment);
        modelBatch.render(playerInst, environment);
        modelBatch.render(robotBodyInst, environment);
        modelBatch.render(robotEyeInst, environment);
        modelBatch.end();

        // ── 2D HUD ──
        renderHUD();
    }

    private void updatePlayerMovement(float delta) {
        float speed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) ? 8f : 5f;
        float dx = 0f, dz = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP))    dz -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN))  dz += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT))  dx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx += 1f;

        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 0) { dx /= len; dz /= len; updatePlayerFacing(dx, dz); }

        float nx = game.session.playerX + dx * speed * delta;
        float nz = game.session.playerZ + dz * speed * delta;
        game.session.playerX = Math.max(-1.7f, Math.min(1.7f, nx));
        game.session.playerZ = Math.max(-29f,  Math.min(29f,  nz));

        // No hiding in the open hallway — hiding is only possible under furniture in rooms.
        game.session.playerHiding = false;

        // Press E to enter nearby room
        if (Gdx.input.isKeyJustPressed(Input.Keys.E) && nearDoorId >= 0) {
            game.toRoom(nearDoorId);
        }
    }

    /** Swap the Ayan sprite to match the movement direction (8-way art). */
    private void updatePlayerFacing(float dx, float dz) {
        String ns = dz < -0.35f ? "north" : (dz > 0.35f ? "south" : "");
        String ew = dx >  0.35f ? "east"  : (dx < -0.35f ? "west"  : "");
        String dir = ns.isEmpty() ? ew : (ew.isEmpty() ? ns : ns + "-" + ew);
        if (dir.isEmpty() || dir.equals(playerFacing)) return;
        playerFacing = dir;
        playerModel.materials.first().set(
            TextureAttribute.createDiffuse(game.assets.ayan(dir)));
    }

    private void scanNearby() {
        nearDoorId = -1;
        doorPromptAlpha = Math.max(0f, doorPromptAlpha - 0.05f);
        for (int i = 0; i < 7; i++) {
            float distZ = Math.abs(game.session.playerZ - Room.DOOR_Z[i]);
            float distX = Math.abs(game.session.playerX - Room.DOOR_X[i]);
            if (distZ < 2.0f && distX < 2.2f) {
                nearDoorId = i;
                doorPromptAlpha = Math.min(1f, doorPromptAlpha + 0.1f);
                break;
            }
        }
    }

    // ─────────────────────────────────────── HUD ─────────────────────────────

    private void renderHUD() {
        hudViewport.apply();
        int W = Gdx.graphics.getWidth();
        int H = Gdx.graphics.getHeight();
        shape.setProjectionMatrix(hudViewport.getCamera().combined);

        // -- Alert flash overlay --
        if (alertFlash > 0.01f) {
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(1f, 0.15f, 0.05f, alertFlash * 0.18f);
            shape.rect(0, 0, W, H);
            shape.end();
        }
        // -- Capture flash overlay --
        if (captureFlash > 0.01f) {
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(1f, 0f, 0f, captureFlash * 0.55f);
            shape.rect(0, 0, W, H);
            shape.end();
        }

        // -- Timer bar (top) --
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.1f, 0.1f, 0.15f, 0.85f);
        shape.rect(0, H - 34, W, 34);
        float frac = game.session.getTimeFraction();
        float barColor_r = frac > 0.5f ? 0.1f : (frac > 0.25f ? 0.9f : 1f);
        float barColor_g = frac > 0.5f ? 0.9f : (frac > 0.25f ? 0.6f : 0.1f);
        shape.setColor(barColor_r, barColor_g, 0.1f, 1f);
        shape.rect(2, H - 32, (W - 4) * frac, 30);
        shape.end();

        // -- Bottom HUD bar --
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0.05f, 0.05f, 0.1f, 0.88f);
        shape.rect(0, 0, W, 48);
        shape.end();

        // -- HP dots --
        shape.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < 3; i++) {
            if (i < game.session.hp) shape.setColor(0.1f, 1f, 0.3f, 1f);
            else                     shape.setColor(0.25f, 0.25f, 0.3f, 1f);
            shape.circle(20 + i * 28, 24, 10, 12);
        }
        // Token slots
        for (int i = 0; i < 3; i++) {
            if (i < game.session.tokensFound) shape.setColor(0.9f, 0.8f, 0.1f, 1f);
            else                              shape.setColor(0.2f, 0.2f, 0.28f, 1f);
            shape.circle(W / 2f - 28 + i * 28, 24, 10, 12);
        }
        shape.end();

        // -- Robot alert indicator (top right) --
        if (robot.isAlerting()) {
            float blink = (float)(0.5 + 0.5 * Math.sin(totalTime * 8f));
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(1f, 0.1f, 0.1f, blink);
            shape.rect(W - 180, H - 34, 180, 34);
            shape.end();
        }

        game.batch.begin();

        // Timer
        game.font.setColor(1f, 1f, 1f, 1f);
        game.font.draw(game.batch, game.session.getTimeString(), W / 2f - 22, H - 8);

        // Labels
        game.font.setColor(0.6f, 0.9f, 0.6f, 0.9f);
        game.font.draw(game.batch, "HP", 8, 48);
        game.font.draw(game.batch, "TOKENS", W / 2f - 52, 48);

        // Score
        game.font.setColor(0.8f, 0.8f, 0.3f, 1f);
        game.font.draw(game.batch, "SCORE: " + game.session.score, W - 180, 42);

        // Robot alert text
        if (robot.isAlerting()) {
            game.font.setColor(1f, 0.8f, 0.8f, 1f);
            String alertStr = robot.state == RobotAI.State.CHASE ? "  ALERT!" : " CAUTION";
            game.font.draw(game.batch, alertStr, W - 168, H - 10);
        }

        // Door prompt
        if (nearDoorId >= 0 && doorPromptAlpha > 0.1f) {
            game.font.setColor(0.4f, 0.9f, 1f, doorPromptAlpha);
            boolean done = game.session.roomMiniGame[nearDoorId] >= 0
                && game.session.miniGameComplete[game.session.roomMiniGame[nearDoorId]];
            String rName = (done ? "[CLEAR] " : "") + rooms[nearDoorId].name;
            game.font.draw(game.batch,
                "[E] Enter: " + rName,
                W / 2f - 130, H / 2f - 80);
        }

        // Hiding indicator
        if (game.session.playerHiding) {
            game.font.setColor(0.2f, 0.7f, 1f, 1f);
            game.font.draw(game.batch, "[HIDING]", W / 2f - 50, H / 2f);
        }

        // Capture message
        if (captureMsgTimer > 0.1f) {
            game.font.setColor(1f, 0.3f, 0.3f, Math.min(1f, captureMsgTimer));
            game.font.draw(game.batch, captureMsg, W / 2f - 200, H / 2f + 60);
        }

        // Exit hint
        if (game.session.exitUnlocked) {
            float blink = (float)(0.5 + 0.5 * Math.sin(totalTime * 3f));
            game.font.setColor(0.9f, 0.8f, 0.1f, blink);
            game.font.draw(game.batch, "ALL TOKENS FOUND — HEAD NORTH TO EXIT!", W / 2f - 260, H - 52);
        }

        // Mini-game completion strip (bottom)
        String[] gameNames = {"KERNEL PANIC", "CIRCUIT BREAKER", "SILENT CODE"};
        for (int i = 0; i < 3; i++) {
            if (game.session.miniGameComplete[i]) {
                game.font.setColor(0.5f, 1f, 0.5f, 0.8f);
            } else {
                game.font.setColor(0.35f, 0.35f, 0.4f, 0.8f);
            }
            game.font.draw(game.batch, gameNames[i], 110 + i * 340, 42);
        }

        // Controls reminder (small)
        game.font.setColor(0.35f, 0.35f, 0.45f, 0.8f);
        game.font.draw(game.batch, "WASD move  SHIFT sprint  E enter room", W - 395, 16);

        game.batch.end();
    }

    // ─────────────────────────────────────── LIFECYCLE ───────────────────────

    @Override public void show() {}

    @Override
    public void resize(int w, int h) {
        hudViewport.update(w, h, true);
        camera.viewportWidth = w;
        camera.viewportHeight = h;
        camera.update();
    }

    @Override public void pause() { game.session.paused = true; }
    @Override public void resume() { game.session.paused = false; }
    @Override public void hide() {}

    @Override
    public void dispose() {
        modelBatch.dispose();
        shape.dispose();
        for (Model m : ownedModels) m.dispose();
    }
}
