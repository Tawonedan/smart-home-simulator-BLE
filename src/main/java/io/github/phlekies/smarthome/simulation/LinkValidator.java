package io.github.phlekies.smarthome.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleFunction;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.physics.RadioMath;
import io.github.phlekies.smarthome.util.Format;

/**
 * Recomputes the displayed link metrics from first principles and compares them with the
 * engine output. Every check passing means the numbers shown in the UI are self-consistent.
 */
public final class LinkValidator {

    private static final double MIN_LINEAR = 1e-15;

    /** One comparison between a value produced by the engine and its closed-form expectation. */
    public record Check(String parameter, String actual, String expected, boolean ok) {
    }

    private LinkValidator() {
    }

    /** Carrier and receiver noise parameters. */
    public static List<Check> environmentChecks(Environment env) {
        double expectedWavelength = RadioMath.SPEED_OF_LIGHT_MPS / (env.getFreqMHz() * 1e6);
        double expectedNoise = RadioMath.THERMAL_NOISE_DBM_PER_HZ
                + 10.0 * Math.log10(env.getBandwidthHz()) + env.getNoiseFigureDb();
        return List.of(
                numeric("Wavelength λ = c / f", env.wavelengthMeters(), expectedWavelength, 1e-9, Format::meters),
                numeric("Noise floor −174 + 10·log10(B) + NF", env.noiseFloorDbm(), expectedNoise, 0.01, Format::dbm));
    }

    /** Noise, SNR, SINR, BER, Shannon capacity and link margin of one receiver point. */
    public static List<Check> cellChecks(CellResult cell, Environment env, SimulationSettings settings) {
        double signalMw = RadioMath.dbmToMilliwatt(cell.signalPowerDbm());
        double noiseMw = RadioMath.dbmToMilliwatt(cell.noiseDbm());
        double interferenceMw = RadioMath.dbmToMilliwatt(cell.interferencePowerDbm());

        double expectedSnr = cell.signalPowerDbm() - cell.noiseDbm();
        double expectedSinr = RadioMath.linearToDb(signalMw / Math.max(noiseMw + interferenceMw, MIN_LINEAR));
        double expectedBer = RadioMath.bpskBer(cell.sinrDb());
        double expectedCapacity = RadioMath.shannonCapacityMbps(cell.sinrDb(), env.getBandwidthHz());
        double expectedMargin = cell.signalPowerDbm() - settings.getReceiverSensitivityDbm();

        return List.of(
                numeric("Noise floor", cell.noiseDbm(), env.noiseFloorDbm(), 0.01, Format::dbm),
                numeric("SNR = S / N", cell.snrDb(), expectedSnr, 0.01, Format::db),
                numeric("SINR = S / (N + I)", cell.sinrDb(), expectedSinr, 0.01, Format::db),
                numeric("BER (BPSK)", cell.ber(), expectedBer, 1e-12, Format::ber),
                numeric("Capacity = B·log2(1 + SINR)", cell.capacityMbps(), expectedCapacity, 0.01, Format::mbps),
                numeric("Link margin = S − sensitivity", cell.linkMarginDb(), expectedMargin, 0.01, Format::db));
    }

    /**
     * Checks that the combined hub cell picks the strongest sensor as signal and sums the
     * others as interference.
     *
     * @param perSensorLinks the single-sensor link of every sensor, in the same order as {@code sensorIds}
     */
    public static List<Check> aggregateChecks(CellResult hubCell, List<String> sensorIds,
                                              List<CellResult> perSensorLinks) {
        if (sensorIds.isEmpty()) {
            return List.of();
        }

        double dominantMw = 0.0;
        double interferenceMw = 0.0;
        String dominantId = CellResult.NO_SENSOR;
        for (int i = 0; i < sensorIds.size(); i++) {
            double powerMw = RadioMath.dbmToMilliwatt(perSensorLinks.get(i).totalPowerDbm());
            if (powerMw > dominantMw) {
                interferenceMw += dominantMw;
                dominantMw = powerMw;
                dominantId = sensorIds.get(i);
            } else {
                interferenceMw += powerMw;
            }
        }

        List<Check> checks = new ArrayList<>();
        checks.add(new Check("Dominant sensor", hubCell.dominantSensorId(), dominantId,
                Objects.equals(hubCell.dominantSensorId(), dominantId)));
        checks.add(numeric("Dominant signal", hubCell.signalPowerDbm(),
                RadioMath.milliwattToDbm(Math.max(dominantMw, MIN_LINEAR)), 0.01, Format::dbm));
        checks.add(numeric("Aggregated interference", hubCell.interferencePowerDbm(),
                RadioMath.milliwattToDbm(Math.max(interferenceMw, MIN_LINEAR)), 0.01, Format::dbm));
        return checks;
    }

    private static Check numeric(String parameter, double actual, double expected, double tolerance,
                                 DoubleFunction<String> format) {
        boolean ok = Math.abs(actual - expected) <= Math.max(tolerance, Math.abs(expected) * 1e-6);
        return new Check(parameter, format.apply(actual), format.apply(expected), ok);
    }
}
