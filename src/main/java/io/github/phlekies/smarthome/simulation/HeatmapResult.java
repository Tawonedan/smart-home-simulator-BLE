package io.github.phlekies.smarthome.simulation;

import java.util.Objects;

/** Grid of link metrics, one {@link CellResult} per square metre. */
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
