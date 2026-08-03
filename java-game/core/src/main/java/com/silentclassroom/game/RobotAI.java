package com.silentclassroom.game;

/**
 * Robot AI for the hallway.
 * FSM: PATROL → ALERT → CHASE → SEARCH → PATROL
 * Ported from SentinelController.java (FXGL version).
 */
public class RobotAI {

    public enum State { PATROL, ALERT, CHASE, SEARCH }

    public State state = State.PATROL;
    public float x = 0f;
    public float z = 0f;

    // Vision
    public static final float VISION_RANGE = 10f;
    public static final float CAPTURE_RANGE = 1.1f;

    // Speeds (units/sec)
    public static final float PATROL_SPEED = 3.5f;
    public static final float ALERT_SPEED  = 2f;
    public static final float CHASE_SPEED  = 6.2f;
    public static final float SEARCH_SPEED = 1.5f;

    // Timers
    private float alertTimer  = 0f;
    private float searchTimer = 0f;

    // Patrol
    private static final float[] PATROL_POINTS = {-22f, -11f, 0f, 11f, 22f};
    private int patrolIndex = 0;
    private float targetZ = PATROL_POINTS[0];

    // Direction robot is facing (along Z: +1 = south, -1 = north)
    public float facingDir = -1f;

    public RobotAI() {
        this.x = 0f;
        this.z = 0f;
    }

    public void update(float dt, float playerX, float playerZ, boolean playerHiding) {
        switch (state) {
            case PATROL:  updatePatrol(dt, playerX, playerZ, playerHiding); break;
            case ALERT:   updateAlert(dt, playerX, playerZ, playerHiding);  break;
            case CHASE:   updateChase(dt, playerX, playerZ, playerHiding);  break;
            case SEARCH:  updateSearch(dt);                                  break;
        }
        // Clamp to hallway bounds
        z = Math.max(-27f, Math.min(27f, z));
    }

    private void updatePatrol(float dt, float px, float pz, boolean hiding) {
        float dz = targetZ - z;
        if (Math.abs(dz) < 0.15f) {
            patrolIndex = (patrolIndex + 1) % PATROL_POINTS.length;
            targetZ = PATROL_POINTS[patrolIndex];
            dz = targetZ - z;
        }
        float step = Math.signum(dz) * PATROL_SPEED * dt;
        z += step;
        facingDir = Math.signum(dz);

        if (canSeePlayer(px, pz, hiding)) {
            state = State.ALERT;
            alertTimer = 0.9f;
        }
    }

    private void updateAlert(float dt, float px, float pz, boolean hiding) {
        alertTimer -= dt;
        if (!canSeePlayer(px, pz, hiding)) {
            // Lost sight during alert — stand down
            state = State.PATROL;
            targetZ = PATROL_POINTS[patrolIndex];
            return;
        }
        if (alertTimer <= 0f) {
            state = State.CHASE; // fixed detection duration, no endless reset
        }
    }

    private void updateChase(float dt, float px, float pz, boolean hiding) {
        if (hiding) {
            transitionToSearch();
            return;
        }
        if (!canSeePlayer(px, pz, hiding)) {
            transitionToSearch();
            return;
        }
        float dz = pz - z;
        float step = Math.signum(dz) * CHASE_SPEED * dt;
        z += step;
        facingDir = Math.signum(dz);
    }

    private void updateSearch(float dt) {
        searchTimer -= dt;
        // Slow oscillating drift during search
        z += (float) Math.sin(searchTimer * 3.0) * SEARCH_SPEED * dt;
        if (searchTimer <= 0f) {
            state = State.PATROL;
            targetZ = PATROL_POINTS[patrolIndex];
        }
    }

    private void transitionToSearch() {
        state = State.SEARCH;
        searchTimer = 4.5f;
    }

    private boolean canSeePlayer(float px, float pz, boolean hiding) {
        if (hiding) return false;
        float dx = px - x;
        float dz = pz - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        return dist <= VISION_RANGE;
    }

    /** Reset after a capture: back off to the far end and resume patrol. */
    public void resetAfterCapture(float playerZ) {
        state = State.PATROL;
        z = playerZ > 0 ? -22f : 22f;
        patrolIndex = 0;
        targetZ = PATROL_POINTS[patrolIndex];
    }

    public boolean isCatchingPlayer(float px, float pz, boolean hiding) {
        if (hiding || state != State.CHASE) return false;
        float dx = px - x;
        float dz = pz - z;
        return (dx * dx + dz * dz) < CAPTURE_RANGE * CAPTURE_RANGE;
    }

    public boolean isAlerting() {
        return state == State.ALERT || state == State.CHASE;
    }
}
