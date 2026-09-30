package core.sim;

import java.util.Objects;

public record HeatmapResult(int width, int height, CellResult[][] cells, SimulationSettings settings) {
    public HeatmapResult {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(settings, "settings");
    }

    public CellResult getCell(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }
        return cells[x][y];
    }
}
