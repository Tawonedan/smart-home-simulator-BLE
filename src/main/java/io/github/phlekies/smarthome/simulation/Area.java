package io.github.phlekies.smarthome.simulation;

import java.util.List;

import io.github.phlekies.smarthome.model.Wall;

/**
 * Axis-aligned rectangle in metres, used as the region of interest of an analysis.
 *
 * @param minX left edge
 * @param minY bottom edge
 * @param maxX right edge
 * @param maxY top edge
 */
public record Area(double minX, double minY, double maxX, double maxY) {

    /** Validates that the rectangle is not inverted. */
    public Area {
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("Empty area: " + minX + "," + minY + " → " + maxX + "," + maxY);
        }
    }

    /**
     * Bounding box of the walls (the building footprint).
     *
     * @param walls    the walls of the plan
     * @param fallback area returned when there are no walls
     * @return the smallest rectangle containing every wall end point
     */
    public static Area footprintOf(List<Wall> walls, Area fallback) {
        if (walls.isEmpty()) {
            return fallback;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Wall wall : walls) {
            minX = Math.min(minX, Math.min(wall.getX1(), wall.getX2()));
            minY = Math.min(minY, Math.min(wall.getY1(), wall.getY2()));
            maxX = Math.max(maxX, Math.max(wall.getX1(), wall.getX2()));
            maxY = Math.max(maxY, Math.max(wall.getY1(), wall.getY2()));
        }
        return new Area(minX, minY, maxX, maxY);
    }

    /** @return whether the point lies inside the rectangle or on its border */
    public boolean contains(double x, double y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    /** @return horizontal extent in metres */
    public double width() {
        return maxX - minX;
    }

    /** @return vertical extent in metres */
    public double height() {
        return maxY - minY;
    }
}
