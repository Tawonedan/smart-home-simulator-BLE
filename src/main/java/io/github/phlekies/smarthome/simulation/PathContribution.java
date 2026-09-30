package io.github.phlekies.smarthome.simulation;

import java.util.Locale;

import io.github.phlekies.smarthome.physics.ComplexNumber;
import io.github.phlekies.smarthome.physics.RadioMath;

/** One propagation path from a sensor to a receiver point, with its loss budget. */
public record PathContribution(
        String sensorId,
        PathType type,
        double distanceMeters,
        double txGainDb,
        double wallLossDb,
        double materialLossDb,
        double diffractionLossDb,
        double scatteringLossDb,
        double polarizationLossDb,
        double powerDbm,
        double phaseRad,
        int bounceCount) {

    public double powerMilliwatt() {
        return RadioMath.dbmToMilliwatt(powerDbm);
    }

    /** Field phasor with amplitude √P, used for coherent summation. */
    public ComplexNumber fieldPhasor() {
        return ComplexNumber.fromPolar(Math.sqrt(powerMilliwatt()), phaseRad);
    }

    /** All losses on top of the distance-based path loss. */
    public double extraLossDb() {
        return wallLossDb + materialLossDb + diffractionLossDb + scatteringLossDb + polarizationLossDb;
    }

    public String summary() {
        return String.format(Locale.US, "%s %s: %.1f dBm, %.2f m", sensorId, type, powerDbm, distanceMeters);
    }
}
