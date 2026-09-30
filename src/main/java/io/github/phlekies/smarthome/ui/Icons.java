package io.github.phlekies.smarthome.ui;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/** Material Design icons used across the UI. */
final class Icons {

    static final Ikon SELECT = Material2MZ.NEAR_ME;
    static final Ikon WALL = Material2AL.HORIZONTAL_RULE;
    static final Ikon ROOM = Material2AL.CROP_SQUARE;
    static final Ikon SENSOR = Material2MZ.SETTINGS_INPUT_ANTENNA;
    static final Ikon HUB = Material2MZ.ROUTER;
    static final Ikon ERASE = Material2AL.DELETE_OUTLINE;
    static final Ikon UNDO = Material2MZ.UNDO;
    static final Ikon REDO = Material2MZ.REDO;
    static final Ikon HEATMAP = Material2AL.LAYERS;
    static final Ikon RAYS = Material2AL.CALL_MADE;
    static final Ikon WAVES = Material2MZ.WIFI_TETHERING;
    static final Ikon OPTIMISE = Material2AL.GPS_FIXED;
    static final Ikon NEW = Material2MZ.NOTE_ADD;
    static final Ikon OPEN = Material2AL.FOLDER_OPEN;
    static final Ikon SAVE = Material2MZ.SAVE;
    static final Ikon IMAGE = Material2AL.IMAGE;
    static final Ikon REPORT = Material2AL.DESCRIPTION;
    static final Ikon SUMMARY = Material2AL.ASSESSMENT;
    static final Ikon DARK = Material2MZ.NIGHTS_STAY;
    static final Ikon LIGHT = Material2MZ.WB_SUNNY;
    static final Ikon LINKS = Material2MZ.TIMELINE;
    static final Ikon INFO = Material2AL.INFO;

    private Icons() {
    }

    static FontIcon of(Ikon ikon) {
        return new FontIcon(ikon);
    }
}
