package core.sim;

import java.util.List;

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
        List<PathContribution> contributions
) {
    public CellResult {
        dominantSensorId = (dominantSensorId == null || dominantSensorId.isBlank()) ? "—" : dominantSensorId;
        contributions = List.copyOf(contributions);
    }

    public double valueFor(MapMetric metric) {
        return switch (metric) {
            case POWER_DBM -> totalPowerDbm;
            case SNR_DB -> sinrDb;
            case BER -> ber;
            case CAPACITY_MBPS -> capacityMbps;
        };
    }

    public boolean hasEnergy() {
        return pathCount > 0;
    }
}
