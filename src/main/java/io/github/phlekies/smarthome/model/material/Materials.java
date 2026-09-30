package io.github.phlekies.smarthome.model.material;

import java.util.List;

/** Catalogue of built-in indoor wall materials. */
public final class Materials {

    public static final Material DRYWALL = new Material("Drywall", 3.0, 5.0, 0.0, 5.2, 9.0);
    public static final Material BRICK = new Material("Brick", 6.0, 9.0, 0.1, 4.2, 10.5);
    public static final Material CONCRETE = new Material("Concrete", 10.0, 15.0, 0.2, 3.0, 12.5);
    public static final Material GLASS = new Material("Glass", 2.0, 3.0, 0.0, 6.5, 6.0);
    public static final Material WOOD = new Material("Wood", 3.0, 5.0, 0.05, 5.4, 8.5);
    public static final Material METAL_DOOR = new Material("Metal door", 18.0, 25.0, 0.0, 1.5, 5.5);

    private static final List<Material> ALL = List.of(DRYWALL, BRICK, CONCRETE, GLASS, WOOD, METAL_DOOR);

    /** Reflection coefficients below this amplitude are treated as no specular reflection. */
    private static final double MIN_REFLECTION_COEFFICIENT = 0.02;

    private Materials() {
    }

    public static List<Material> all() {
        return ALL;
    }

    /** Evaluates how a wave interacts with a wall at the given frequency and incidence angle. */
    public static WallInteraction interact(Material material, double freqMHz, double thicknessCm,
                                           double incidenceAngleDeg) {
        Material m = (material == null) ? DRYWALL : material;
        double reflectionCoefficient = m.reflectionCoefficient(freqMHz, incidenceAngleDeg);
        return new WallInteraction(
                m.transmissionLossDb(freqMHz, thicknessCm),
                m.reflectionLossDb(freqMHz, incidenceAngleDeg),
                reflectionCoefficient > MIN_REFLECTION_COEFFICIENT,
                m.scatteringLossDb(freqMHz, incidenceAngleDeg),
                m.transmissionCoefficient(freqMHz, thicknessCm),
                reflectionCoefficient);
    }
}
