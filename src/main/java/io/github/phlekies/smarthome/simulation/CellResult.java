package io.github.phlekies.smarthome.simulation;

import java.util.List;

/**
 * Link metrics at one receiver point.
 *
 * <p>The strongest sensor is the useful signal; the rest of the sensors count as interference.
 */
public record CellResult(
        double xMeters,
        double yMeters,
        double totalPowerDbm,
        double signalPowerDbm,
        double interferencePowerDbm,
        double noiseDbm,
        double snrDb,
        double sinrDb,
        double linkMarginDb,
        double ber,
        double capacityMbps,
        double fieldPhaseRad,
        int pathCount,
        String dominantSensorId,
        List<PathContribution> contributions) {

    public static final String NO_SENSOR = "-";

    public CellResult {
        dominantSensorId = (dominantSensorId == null || dominantSensorId.isBlank()) ? NO_SENSOR : dominantSensorId;
        contributions = List.copyOf(contributions);
    }

    public double valueFor(MapMetric metric) {
        return switch (metric) {
            case POWER_DBM -> totalPowerDbm;
            case SINR_DB -> sinrDb;
            case BER -> ber;
            case CAPACITY_MBPS -> capacityMbps;
        };
    }

    public boolean hasEnergy() {
        return pathCount > 0;
    }
}
