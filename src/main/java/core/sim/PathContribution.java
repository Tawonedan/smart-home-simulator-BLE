package core.sim;

import java.util.Locale;

import core.Propagation;

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
        int bounceCount
) {
    public double powerMilliwatt() {
        return Propagation.dbmToMilliwatt(powerDbm);
    }

    public ComplexNumber fieldVector() {
        return ComplexNumber.fromPolar(Math.sqrt(powerMilliwatt()), phaseRad);
    }

    public double extraLossDb() {
        return wallLossDb + materialLossDb + diffractionLossDb + scatteringLossDb + polarizationLossDb;
    }

    public String summary() {
        return String.format(Locale.US, "%s %s: %.1f dBm, %.2f m",
                sensorId, type, powerDbm, distanceMeters);
    }
}
