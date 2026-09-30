package io.github.phlekies.smarthome.model;

import java.util.Objects;

/** A radio device placed on the floor plan. Coordinates are in metres (1 grid cell = 1 m). */
public abstract class Device {

    private final String id;
    private String name;
    private int x;
    private int y;

    /** Creates a device at whole-metre coordinates. */
    protected Device(String id, String name, int x, int y) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.x = x;
        this.y = y;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    /** Moves the device to whole-metre coordinates. */
    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** Straight-line distance to another device, in metres. */
    public double distanceTo(Device other) {
        return Math.hypot(other.x - x, other.y - y);
    }

    @Override
    public String toString() {
        return name;
    }
}
