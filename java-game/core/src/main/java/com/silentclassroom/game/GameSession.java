package com.silentclassroom.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds all mutable game state for a single run.
 * Ported from SilentClassroomSession.java (FXGL version).
 */
public class GameSession {

    public static final float TOTAL_TIME = 9 * 60f; // 9 minutes

    // --- Timer & Status ---
    public float timeRemaining = TOTAL_TIME;
    public boolean paused = false;

    // --- Player vitals ---
    public int hp = 3;
    public int score = 0;
    public int captures = 0;
    public int tokensFound = 0;

    // --- Mini-game state ---
    /** roomMiniGame[roomId] = -1 (none), 0 (Kernel Panic), 1 (Circuit Breaker), 2 (Silent Code) */
    public int[] roomMiniGame = new int[7];
    public boolean[] miniGameComplete = new boolean[3];
    public boolean exitUnlocked = false;

    // --- Player world state (persisted between room transitions) ---
    public float playerX = 0f;
    public float playerZ = 26f; // starts south end of hallway
    public boolean playerHiding = false;

    // --- Room tracking ---
    public boolean[] roomVisited = new boolean[7];
    /** Furniture already searched per room, tracks found-game flag */
    public boolean[] miniGameFoundInRoom = new boolean[7];
    /** How many furniture slots have been searched per room (persists across visits). */
    public int[] roomSearchCount = new int[7];

    public GameSession() {
        for (int i = 0; i < 7; i++) roomMiniGame[i] = -1;

        // Randomly assign 3 mini-games to 3 distinct rooms
        List<Integer> rooms = new ArrayList<>();
        for (int i = 0; i < 7; i++) rooms.add(i);
        Collections.shuffle(rooms);
        roomMiniGame[rooms.get(0)] = 0; // Kernel Panic
        roomMiniGame[rooms.get(1)] = 1; // Circuit Breaker
        roomMiniGame[rooms.get(2)] = 2; // Silent Code
    }

    public void update(float dt) {
        if (!paused && !exitUnlocked) {
            timeRemaining = Math.max(0f, timeRemaining - dt);
        }
        exitUnlocked = miniGameComplete[0] && miniGameComplete[1] && miniGameComplete[2];
    }

    /** Called when robot captures the player. */
    public void onCapture() {
        hp--;
        captures++;
        score = Math.max(0, score - 150);
        timeRemaining = Math.max(0f, timeRemaining - 20f);
        playerHiding = false;
    }

    /** Called when a mini-game is successfully completed. */
    public void onMiniGameComplete(int type, int gameScore) {
        if (!miniGameComplete[type]) {
            miniGameComplete[type] = true;
            tokensFound++;
            score += gameScore;
        }
    }

    /** Called when searching furniture (foundToken = found the mini-game trigger). */
    public void onSearch(boolean foundToken) {
        score += foundToken ? 45 : 5;
    }

    // --- Queries ---

    public boolean isGameOver() {
        // Once the exit is unlocked the timer no longer matters — a win at the
        // final second counts as a win, not a loss.
        return hp <= 0 || (timeRemaining <= 0f && !exitUnlocked);
    }

    public boolean isWon() {
        return exitUnlocked;
    }

    public int getFinalScore() {
        int timeBonus = (int) (timeRemaining / 540f * 300f);
        int noCapBonus = captures == 0 ? 200 : 0;
        return Math.max(0, score + timeBonus + noCapBonus);
    }

    /** HUD-ready time string "M:SS" */
    public String getTimeString() {
        int mins = (int) (timeRemaining / 60f);
        int secs = (int) (timeRemaining % 60f);
        return String.format("%d:%02d", mins, secs);
    }

    /** Fraction 0..1 of time remaining (for timer bar). */
    public float getTimeFraction() {
        return timeRemaining / TOTAL_TIME;
    }
}
