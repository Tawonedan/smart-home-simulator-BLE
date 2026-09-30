package io.github.phlekies.smarthome.model.material;

/** Losses and coefficients of one wave-wall interaction. */
public record WallInteraction(
        double transmissionLossDb,
        double reflectionLossDb,
        boolean reflects,
        double scatteringLossDb,
        double transmissionCoefficient,
        double reflectionCoefficient) {
}
