package com.silentclassroom.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.silentclassroom.SilentClassroomGame;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Silent Classroom");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.setResizable(false);
        config.useVsync(true);
        new Lwjgl3Application(new SilentClassroomGame(), config);
    }
}
