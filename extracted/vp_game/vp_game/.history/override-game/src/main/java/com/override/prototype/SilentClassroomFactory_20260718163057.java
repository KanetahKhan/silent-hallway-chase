package com.override.prototype;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/*
 * One factory for:
 * - iut-classroom.tmx
 * - iut-ict-lab.tmx
 * - the older academic-building floor TMX
 *
 * Real movement collision is read from the TMX Collision object layer
 * by SilentClassroomPrototypeApp. These factory entities preserve the
 * TMX metadata and allow FXGL to load every object type successfully.
 */
public final class SilentClassroomFactory
        implements EntityFactory {

    @Spawns("Wall")
    public Entity createWall(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.WALL
        );
    }

    @Spawns("Spawn")
    public Entity createSpawn(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.SPAWN
        );
    }

    @Spawns("Exit")
    public Entity createExit(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.EXIT
        );
    }

    @Spawns("Dialogue")
    public Entity createDialogue(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.DIALOGUE
        );
    }

    @Spawns("RoomZone")
    public Entity createRoomZone(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.ROOM_ZONE
        );
    }

    @Spawns("Light")
    public Entity createLight(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.LIGHT
        );
    }

    /*
     * Kept for compatibility with your older academic-building map.
     */
    @Spawns("Arcade")
    public Entity createArcade(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.ARCADE
        );
    }

    @Spawns("Transition")
    public Entity createTransition(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.TRANSITION
        );
    }

    @Spawns("EnemySpawn")
    public Entity createEnemySpawn(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.ENEMY_SPAWN
        );
    }

    @Spawns("PatrolPath")
    public Entity createPatrolPath(SpawnData data) {
        return createMarker(
                data,
                SilentClassroomType.PATROL_PATH
        );
    }

    private Entity createMarker(
            SpawnData data,
            SilentClassroomType type
    ) {
        return entityBuilder(data)
                .type(type)
                .build();
    }
}
