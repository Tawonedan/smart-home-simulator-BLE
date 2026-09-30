package io.github.phlekies.smarthome;

import javafx.application.Application;

import io.github.phlekies.smarthome.ui.SimulatorApp;

/**
 * Application entry point.
 *
 * <p>The JVM refuses to start a {@link Application} subclass directly when JavaFX is on the
 * class path instead of the module path (as in the distribution scripts and executable JARs).
 * Launching it from a plain class avoids "JavaFX runtime components are missing".
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(SimulatorApp.class, args);
    }
}
