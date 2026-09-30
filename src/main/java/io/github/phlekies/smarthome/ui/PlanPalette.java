package io.github.phlekies.smarthome.ui;

import java.util.Map;

import javafx.scene.paint.Color;

import io.github.phlekies.smarthome.model.material.Material;
import io.github.phlekies.smarthome.model.material.Materials;

/** Colours of everything drawn on the plan canvases, for the light and the dark theme. */
record PlanPalette(Color background, Color gridLine, Color gridLineMajor, Color axisLabel, Color deviceLabel,
                   Color sensor, Color hub, Color selection, Color linkOk, Color linkBad, Color ray,
                   Map<Material, Color> materials) {

    static final PlanPalette LIGHT = new PlanPalette(
            Color.WHITE, Color.web("#e6ebf0"), Color.web("#d2dae2"), Color.web("#8795a1"), Color.web("#1f2937"),
            Color.web("#1e88e5"), Color.web("#dc2626"), Color.web("#f59e0b"), Color.web("#16a34a"),
            Color.web("#dc2626"), Color.web("#0b5394"),
            Map.of(Materials.DRYWALL, Color.web("#8a9aac"),
                    Materials.BRICK, Color.web("#b5562c"),
                    Materials.CONCRETE, Color.web("#4b5563"),
                    Materials.GLASS, Color.web("#0ea5e9"),
                    Materials.WOOD, Color.web("#b7791f"),
                    Materials.METAL_DOOR, Color.web("#9f1239")));

    static final PlanPalette DARK = new PlanPalette(
            Color.web("#0d1117"), Color.web("#161b22"), Color.web("#21262d"), Color.web("#7d8590"),
            Color.web("#e6edf3"), Color.web("#58a6ff"), Color.web("#ff7b72"), Color.web("#f2cc60"),
            Color.web("#3fb950"), Color.web("#f85149"), Color.web("#e0f2fe"),
            Map.of(Materials.DRYWALL, Color.web("#8b98a9"),
                    Materials.BRICK, Color.web("#e07a4f"),
                    Materials.CONCRETE, Color.web("#b1bac4"),
                    Materials.GLASS, Color.web("#39c5f5"),
                    Materials.WOOD, Color.web("#d9a441"),
                    Materials.METAL_DOOR, Color.web("#ff6b8b")));

    static PlanPalette of(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    Color materialColor(Material material) {
        return materials.getOrDefault(material, gridLineMajor);
    }

    /** Thicker walls are drawn with a wider stroke (2.6 px for 4 cm up to 5 px for 20 cm). */
    static double wallStroke(double thicknessCm) {
        return 2.0 + Math.min(thicknessCm, 25.0) * 0.15;
    }
}
