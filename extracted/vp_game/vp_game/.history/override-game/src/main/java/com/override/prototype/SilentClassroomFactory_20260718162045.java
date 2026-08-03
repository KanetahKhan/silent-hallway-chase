package com.override.prototype;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

public final class SilentClassroomFactory
        implements EntityFactory {

    /*
     * Collision is now read directly from the TMX Collision layer
     * inside SilentClassroomPrototypeApp.
     *
     * Therefore, these methods only create invisible metadata entities.
     */

    @Spawns("Wall")
    public Entity createWall(SpawnData data) {
        return createMarker(data);
    }

    @Spawns("Spawn")
    public Entity createSpawn(SpawnData data) {
        return createMarker(data);
    }

    @Spawns("Exit")
    public Entity createExit(SpawnData data) {
        return createMarker(data);
    }

    @Spawns("Dialogue")
    public Entity createDialogue(SpawnData data) {
        return createMarker(data);
    }

    @Spawns("RoomZone")
    public Entity createRoomZone(SpawnData data) {
        return createMarker(data);
    }

    @Spawns("Light")
    public Entity createLight(SpawnData data) {
        return createMarker(data);
    }

    /*
     * These are kept so the same factory can also load your older
     * academic-building TMX later.
     */

    @Spawns("Arcade")
    public Entity createArcade(SpawnData data) {
        return createMarker(data);
    }

    @Spawns("Transition")
    public Entity createTransition(SpawnData data) {
        return createMarker(data);
    }

    @Spawns("EnemySpawn")
    public Entity createEnemySpawn(SpawnData data) {
        return createMarker(data);
    }

    @Spawns("PatrolPath")
    public Entity createPatrolPath(SpawnData data) {
        return createMarker(data);
    }

    private Entity createMarker(SpawnData data) {
        return entityBuilder(data).build();
    }
}