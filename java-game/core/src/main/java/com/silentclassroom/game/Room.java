package com.silentclassroom.game;

/**
 * Metadata for one of the 7 rooms (4 classrooms + 3 labs).
 */
public class Room {

    public enum Type { CLASSROOM, LAB }

    // Door positions along the hallway Z axis
    public static final float[] DOOR_Z  = {-22f, -14f, -6f, 2f, 10f, 18f, 26f};
    // Doors alternate left/right (X side)
    public static final float[] DOOR_X  = {-2f, 2f, -2f, 2f, -2f, 2f, -2f};

    public static final String[] ROOM_NAMES = {
        "Classroom A", "Classroom B", "Classroom C", "Classroom D",
        "Biology Lab", "Chemistry Lab", "Computer Lab"
    };

    public final int id;
    public final Type type;
    public final String name;

    /** Index of mini-game assigned here, or -1 if none. */
    public final int miniGameType;

    /** True once the player has triggered the mini-game from this room. */
    public boolean miniGameTriggered = false;

    /** How many pieces of furniture have been searched here. */
    public int searchCount = 0;

    /** Number of searchable furniture pieces in this room. */
    public static final int FURNITURE_SLOTS = 5;

    /** Which slot (0..4) hides the mini-game terminal (random per room at start). */
    public final int terminalSlot;

    public Room(int id, int miniGameType) {
        this.id = id;
        this.type = (id < 4) ? Type.CLASSROOM : Type.LAB;
        this.name = ROOM_NAMES[id];
        this.miniGameType = miniGameType;
        // Deterministic slot based on id so it's fixed per session
        this.terminalSlot = (id * 3 + 2) % FURNITURE_SLOTS;
    }

    public boolean hasGame() {
        return miniGameType != -1;
    }

    public boolean canSearch() {
        return searchCount < FURNITURE_SLOTS;
    }

    /** Returns true when the current search slot reveals the mini-game. */
    public boolean searchNext() {
        if (!canSearch()) return false;
        boolean found = hasGame() && !miniGameTriggered && (searchCount == terminalSlot);
        searchCount++;
        if (found) miniGameTriggered = true;
        return found;
    }
}
