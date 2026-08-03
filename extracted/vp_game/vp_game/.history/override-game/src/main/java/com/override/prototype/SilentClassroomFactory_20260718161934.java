package com.override.prototype;

import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityFactory;
import com.almasb.fxgl.entity.SpawnData;
import com.almasb.fxgl.entity.Spawns;

import static com.almasb.fxgl.dsl.FXGL.entityBuilder;

/*
 * FXGL's Tiled parser calls one @Spawns method for every object type found
 * in the TMX. These objects are metadata markers; movement collision is read
 * explicitly from the TMX Collision layer by SilentClassroomPrototypeApp.
 */
public final class SilentClassroomFactory
        implements EntityFactory {

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

    private Entity createMarker(SpawnData data) {
        return entityBuilder(data).build();
    }
}
