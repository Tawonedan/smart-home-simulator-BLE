package io.github.phlekies.smarthome.model.material;

import java.util.Locale;
import java.util.Objects;

/**
 * Wall material with frequency-dependent penetration, reflection and scattering losses.
 *
 * <p>Values are empirical indoor figures for a typical ~10 cm wall at the 2.4 GHz and 5 GHz
 * Wi-Fi bands, optionally increased per extra centimetre of thickness.
 */
public final class Material {

    /** Frequencies below this threshold use the 2.4 GHz figures, above it the 5 GHz ones. */
    private static final double BAND_SPLIT_MHZ = 3000.0;

    private final String name;
    private final double lossDbAt24GHz;
    private final double lossDbAt5GHz;
    private final double lossDbPerCm;
    /** Lower values produce stronger specular reflections. */
    private final double reflectionBiasDb;
    /** Equivalent surface roughness: how much energy is scattered diffusely. */
    private final double roughnessDb;

    public Material(String name, double lossDbAt24GHz, double lossDbAt5GHz, double lossDbPerCm,
                    double reflectionBiasDb, double roughnessDb) {
        this.name = Objects.requireNonNull(name, "name");
        this.lossDbAt24GHz = lossDbAt24GHz;
        this.lossDbAt5GHz = lossDbAt5GHz;
        this.lossDbPerCm = lossDbPerCm;
        this.reflectionBiasDb = Math.max(1.0, reflectionBiasDb);
        this.roughnessDb = Math.max(0.0, roughnessDb);
    }

    public String getName() {
        return name;
    }

    /** Penetration loss for one crossing of a wall of the given thickness. */
    public double transmissionLossDb(double freqMHz, double thicknessCm) {
        double base = (freqMHz < BAND_SPLIT_MHZ) ? lossDbAt24GHz : lossDbAt5GHz;
        return base + Math.max(0.0, thicknessCm) * Math.max(0.0, lossDbPerCm);
    }

    /** Specular reflection loss; grazing incidence reflects more strongly than normal incidence. */
    public double reflectionLossDb(double freqMHz, double incidenceAngleDeg) {
        double freqFactor = (freqMHz < BAND_SPLIT_MHZ) ? 1.0 : 1.15;
        double grazingGain = 6.0 * Math.sin(Math.toRadians(clampIncidence(incidenceAngleDeg)));
        double roughnessPenalty = 0.25 * roughnessDb;
        return Math.max(1.5, reflectionBiasDb * freqFactor - grazingGain + roughnessPenalty);
    }

    /** Diffuse scattering loss; rougher surfaces and higher frequencies scatter less coherently. */
    public double scatteringLossDb(double freqMHz, double incidenceAngleDeg) {
        double freqPenalty = (freqMHz < BAND_SPLIT_MHZ) ? 1.0 : 3.0;
        double anglePenalty = 4.0 * Math.cos(Math.toRadians(clampIncidence(incidenceAngleDeg)));
        return Math.max(6.0, roughnessDb + freqPenalty + anglePenalty);
    }

    /** Field amplitude transmission coefficient |T| = 10^(-L/20). */
    public double transmissionCoefficient(double freqMHz, double thicknessCm) {
        return Math.pow(10.0, -transmissionLossDb(freqMHz, thicknessCm) / 20.0);
    }

    /** Field amplitude reflection coefficient |Γ| = 10^(-L/20). */
    public double reflectionCoefficient(double freqMHz, double incidenceAngleDeg) {
        return Math.pow(10.0, -reflectionLossDb(freqMHz, incidenceAngleDeg) / 20.0);
    }

    private static double clampIncidence(double incidenceAngleDeg) {
        return Math.max(0.0, Math.min(89.0, incidenceAngleDeg));
    }

    @Override
    public String toString() {
        return name;
    }

    public String describe() {
        return String.format(Locale.US, "%s (%.1f dB @ 2.4 GHz, %.1f dB @ 5 GHz)", name, lossDbAt24GHz, lossDbAt5GHz);
    }
}
