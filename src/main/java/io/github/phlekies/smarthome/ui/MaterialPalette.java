package io.github.phlekies.smarthome.ui;

import java.util.Map;

import javafx.scene.paint.Color;

import io.github.phlekies.smarthome.model.material.Material;
import io.github.phlekies.smarthome.model.material.Materials;

/** Drawing colour and stroke width of each wall material. */
final class MaterialPalette {

    private static final Map<Material, Color> COLORS = Map.of(
            Materials.DRYWALL, Color.web("#8a9aac"),
            Materials.BRICK, Color.web("#b5562c"),
            Materials.CONCRETE, Color.web("#4b5563"),
            Materials.GLASS, Color.web("#22a6e8"),
            Materials.WOOD, Color.web("#c08a3e"),
            Materials.METAL_DOOR, Color.web("#8b1c2c"));

    private MaterialPalette() {
    }

    static Color colorOf(Material material) {
        return COLORS.getOrDefault(material, Color.DARKGRAY);
    }

    /** Thicker walls are drawn with a wider stroke (2.6 px for 4 cm up to 5 px for 20 cm). */
    static double strokeWidth(double thicknessCm) {
        return 2.0 + Math.min(thicknessCm, 25.0) * 0.15;
    }
}
