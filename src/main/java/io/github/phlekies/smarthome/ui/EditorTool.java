package io.github.phlekies.smarthome.ui;

/** What a primary click on the floor plan does. */
enum EditorTool {
    SELECT("Select", "Click a wall to select it and edit its material or thickness."),
    WALL("Draw wall", "Click two points to draw a straight wall."),
    ROOM("Draw room", "Click two opposite corners to create a rectangular room."),
    SENSOR("Place beacon", "Click on the plan to place a BLE beacon."),
    HUB("Place scanner", "Click on the plan to place or move the BLE scanner."),
    DELETE("Erase wall", "Click a wall to delete it.");

    private final String label;
    private final String help;

    EditorTool(String label, String help) {
        this.label = label;
        this.help = help;
    }

    String help() {
        return help;
    }

    boolean placesDevices() {
        return this == SENSOR || this == HUB;
    }

    @Override
    public String toString() {
        return label;
    }
}
