package io.github.phlekies.smarthome.simulation;

import java.util.Locale;

import io.github.phlekies.smarthome.physics.ComplexNumber;
import io.github.phlekies.smarthome.physics.RadioMath;

/**
 * One propagation path from a sensor to a receiver point, with its loss budget.
 *
 * @param sensorId           transmitting sensor
 * @param type               propagation mechanism
 * @param distanceMeters     travelled path length
 * @param txGainDb           antenna gain towards the path's departure direction
 * @param wallLossDb         penetration losses of the walls crossed
 * @param materialLossDb     reflection loss at the reflecting wall
 * @param diffractionLossDb  knife-edge diffraction loss
 * @param scatteringLossDb   diffuse scattering loss
 * @param polarizationLossDb polarization mismatch loss
 * @param powerDbm           received power of this path
 * @param phaseRad           carrier phase at the receiver
 * @param bounceCount        number of interactions along the path
 */
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

    /** Received power of this path in milliwatts. */
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

    /** Compact description: sensor, mechanism, power and length. */
    public String summary() {
        return String.format(Locale.US, "%s %s: %.1f dBm, %.2f m", sensorId, type, powerDbm, distanceMeters);
    }
}
