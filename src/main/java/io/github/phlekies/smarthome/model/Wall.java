package io.github.phlekies.smarthome.model;

import java.util.Objects;

import io.github.phlekies.smarthome.model.material.Material;
import io.github.phlekies.smarthome.model.material.Materials;

/** A straight wall segment in metres, made of a material with a given thickness. */
public class Wall {

    private static final double TOLERANCE = 1e-9;

    private final double x1;
    private final double y1;
    private final double x2;
    private final double y2;
    private Material material;
    private double thicknessCm;

    /** Creates a wall from (x1, y1) to (x2, y2); a null material means drywall. */
    public Wall(double x1, double y1, double x2, double y2, Material material, double thicknessCm) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.material = (material == null) ? Materials.DRYWALL : material;
        this.thicknessCm = Math.max(0.0, thicknessCm);
    }

    /** Independent copy with the same geometry, material and thickness. */
    public Wall copy() {
        return new Wall(x1, y1, x2, y2, material, thicknessCm);
    }

    /** Copy shifted by an offset in metres. */
    public Wall translated(double dx, double dy) {
        return new Wall(x1 + dx, y1 + dy, x2 + dx, y2 + dy, material, thicknessCm);
    }

    /** Copy with both end points scaled from the origin. */
    public Wall scaled(double factor) {
        return new Wall(x1 * factor, y1 * factor, x2 * factor, y2 * factor, material, thicknessCm);
    }

    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    /** Length of the segment in metres. */
    public double length() {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = Objects.requireNonNull(material, "material");
    }

    public double getThicknessCm() {
        return thicknessCm;
    }

    public void setThicknessCm(double thicknessCm) {
        this.thicknessCm = Math.max(0.0, thicknessCm);
    }

    /** Penetration loss when a path crosses this wall. */
    public double transmissionLossDb(double freqMHz) {
        return material.transmissionLossDb(freqMHz, thicknessCm);
    }

    /** Same geometry, material and thickness (walls are mutable, so this is not {@code equals}). */
    public boolean isEquivalentTo(Wall other) {
        return other != null
                && Math.abs(x1 - other.x1) < TOLERANCE
                && Math.abs(y1 - other.y1) < TOLERANCE
                && Math.abs(x2 - other.x2) < TOLERANCE
                && Math.abs(y2 - other.y2) < TOLERANCE
                && Math.abs(thicknessCm - other.thicknessCm) < TOLERANCE
                && material == other.material;
    }
}
