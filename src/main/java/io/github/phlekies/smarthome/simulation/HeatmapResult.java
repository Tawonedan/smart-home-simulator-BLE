package io.github.phlekies.smarthome.simulation;

import java.util.Objects;

/**
 * Grid of link metrics, one {@link CellResult} per square metre.
 *
 * @param width    number of columns (metres along x)
 * @param height   number of rows (metres along y)
 * @param cells    results indexed as {@code cells[x][y]}
 * @param settings the settings the grid was computed with
 */
public record HeatmapResult(int width, int height, CellResult[][] cells, SimulationSettings settings) {

    public HeatmapResult {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(settings, "settings");
    }

    /** The cell at column {@code x}, row {@code y}, or {@code null} outside the grid. */
    public CellResult cellAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }
        return cells[x][y];
    }
}
