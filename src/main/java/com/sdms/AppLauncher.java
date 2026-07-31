package com.sdms;

/**
 * Bootstrap entry point for packaged launchers.
 *
 * Keeping the launcher class separate avoids Java's direct JavaFX Application
 * detection in some packaged/runtime setups.
 */
public final class AppLauncher {

    private AppLauncher() {}

    public static void main(String[] args) {
        MainApp.main(args);
    }
}