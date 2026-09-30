package io.github.phlekies.smarthome.simulation;

import java.util.List;

/**
 * Link metrics at one receiver point.
 *
 * <p>The strongest sensor is the useful signal; the rest of the sensors count as interference.
 *
 * @param xMeters              x coordinate of the receiver point
 * @param yMeters              y coordinate of the receiver point
 * @param totalPowerDbm        power of every path of every sensor, combined as the propagation mode says
 * @param signalPowerDbm       power of the strongest sensor
 * @param interferencePowerDbm summed power of the other sensors
 * @param noiseDbm             receiver noise floor
 * @param snrDb                signal-to-noise ratio
 * @param sinrDb               signal-to-interference-plus-noise ratio
 * @param linkMarginDb         signal power above the receiver sensitivity
 * @param ber                  BPSK bit error rate at the SINR
 * @param capacityMbps         Shannon capacity at the SINR
 * @param fieldPhaseRad        phase of the coherent field sum
 * @param pathCount            number of paths above the culling threshold
 * @param dominantSensorId     id of the strongest sensor, or {@link #NO_SENSOR}
 * @param contributions        every path, strongest first
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

    /** Placeholder id when no sensor reaches the point. */
    public static final String NO_SENSOR = "-";

    /** Normalises the dominant sensor id and freezes the list of contributions. */
    public CellResult {
        dominantSensorId = (dominantSensorId == null || dominantSensorId.isBlank()) ? NO_SENSOR : dominantSensorId;
        contributions = List.copyOf(contributions);
    }

    /** @return the value of the given heatmap metric at this point */
    public double valueFor(MapMetric metric) {
        return switch (metric) {
            case POWER_DBM -> totalPowerDbm;
            case SINR_DB -> sinrDb;
            case BER -> ber;
            case CAPACITY_MBPS -> capacityMbps;
        };
    }

    /** @return whether at least one path reaches this point */
    public boolean hasEnergy() {
        return pathCount > 0;
    }
}
