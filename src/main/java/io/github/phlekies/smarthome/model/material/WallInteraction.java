package io.github.phlekies.smarthome.model.material;

/**
 * Losses and coefficients of one wave-wall interaction.
 *
 * @param transmissionLossDb      penetration loss through the wall
 * @param reflectionLossDb        specular reflection loss
 * @param reflects                whether the wall produces a usable specular reflection
 * @param scatteringLossDb        diffuse scattering loss
 * @param transmissionCoefficient field amplitude transmission coefficient
 * @param reflectionCoefficient   field amplitude reflection coefficient
 */
public record WallInteraction(
        double transmissionLossDb,
        double reflectionLossDb,
        boolean reflects,
        double scatteringLossDb,
        double transmissionCoefficient,
        double reflectionCoefficient) {
}
