package io.github.phlekies.smarthome.simulation;

import java.util.Arrays;

/**
 * Summary of a heatmap over a region (normally the building footprint). "Signal" is the power of
 * the strongest sensor at each point, i.e. what a receiver placed there would lock on to.
 *
 * @param cellCount        number of one-metre cells analysed
 * @param coveredFraction  share of the cells where the signal is at or above the receiver sensitivity
 * @param medianSignalDbm  median signal level
 * @param signalP10Dbm     signal level exceeded in 90 % of the area (10th percentile)
 * @param medianSinrDb     median SINR
 * @param meanCapacityMbps mean Shannon capacity
 * @param sensitivityDbm   receiver sensitivity used as the coverage threshold
 */
public record CoverageStats(
        int cellCount,
        double coveredFraction,
        double medianSignalDbm,
        double signalP10Dbm,
        double medianSinrDb,
        double meanCapacityMbps,
        double sensitivityDbm) {

    public static final CoverageStats EMPTY =
            new CoverageStats(0, 0.0, Double.NaN, Double.NaN, Double.NaN, 0.0, Double.NaN);

    /** Analyses the cells of the heatmap whose centre lies inside the area. */
    public static CoverageStats of(HeatmapResult heatmap, Area area, double sensitivityDbm) {
        int total = heatmap.width() * heatmap.height();
        double[] signal = new double[total];
        double[] sinr = new double[total];
        int count = 0;
        int covered = 0;
        double capacitySum = 0.0;

        for (int x = 0; x < heatmap.width(); x++) {
            for (int y = 0; y < heatmap.height(); y++) {
                CellResult cell = heatmap.cellAt(x, y);
                if (cell == null || !area.contains(cell.xMeters(), cell.yMeters())) {
                    continue;
                }
                signal[count] = cell.signalPowerDbm();
                sinr[count] = cell.sinrDb();
                count++;
                capacitySum += cell.capacityMbps();
                if (cell.hasEnergy() && cell.signalPowerDbm() >= sensitivityDbm) {
                    covered++;
                }
            }
        }
        if (count == 0) {
            return EMPTY;
        }

        double[] sortedSignal = Arrays.copyOf(signal, count);
        double[] sortedSinr = Arrays.copyOf(sinr, count);
        Arrays.sort(sortedSignal);
        Arrays.sort(sortedSinr);
        return new CoverageStats(
                count,
                (double) covered / count,
                percentile(sortedSignal, 0.5),
                percentile(sortedSignal, 0.1),
                percentile(sortedSinr, 0.5),
                capacitySum / count,
                sensitivityDbm);
    }

    /** Linear-interpolated percentile of an ascending array. */
    static double percentile(double[] ascending, double fraction) {
        if (ascending.length == 1) {
            return ascending[0];
        }
        double position = fraction * (ascending.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(lower + 1, ascending.length - 1);
        double weight = position - lower;
        return ascending[lower] * (1.0 - weight) + ascending[upper] * weight;
    }

    public boolean isEmpty() {
        return cellCount == 0;
    }
}
