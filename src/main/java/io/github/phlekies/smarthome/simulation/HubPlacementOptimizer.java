package io.github.phlekies.smarthome.simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Sensor;

/**
 * Finds where to put the hub so that every sensor reaches it with the largest possible margin.
 *
 * <p>Every whole-metre position inside the area is a candidate. For each candidate the link of
 * every sensor is evaluated on its own (sensors take turns on the channel, so they do not
 * interfere with their own uplink). Candidates are ranked by:
 * <ol>
 *   <li>fewest sensors with no usable signal at all,</li>
 *   <li>largest worst-case link margin (max-min fairness),</li>
 *   <li>largest mean link margin.</li>
 * </ol>
 * Small-scale fading is disabled during the search, so the choice reflects the average channel
 * rather than a lucky fade.
 */
public final class HubPlacementOptimizer {

    /**
     * Score of one candidate hub position.
     *
     * @param x                  candidate x position in metres
     * @param y                  candidate y position in metres
     * @param unreachableSensors sensors whose signal does not reach the candidate at all
     * @param worstMarginDb      smallest link margin among the sensors that do reach it
     * @param meanMarginDb       mean link margin among the sensors that do reach it
     * @param weakestSensorId    an unreachable sensor if any, otherwise the one with the worst margin
     */
    public record Candidate(int x, int y, int unreachableSensors, double worstMarginDb, double meanMarginDb,
                            String weakestSensorId) {
    }

    /**
     * Outcome of a search.
     *
     * @param best       the recommended hub position
     * @param candidates every evaluated position, best first
     */
    public record Result(Candidate best, List<Candidate> candidates) {

        public Result {
            candidates = List.copyOf(candidates);
        }
    }

    static final Comparator<Candidate> BEST_FIRST = Comparator
            .comparingInt(Candidate::unreachableSensors)
            .thenComparing(Comparator.comparingDouble(Candidate::worstMarginDb).reversed())
            .thenComparing(Comparator.comparingDouble(Candidate::meanMarginDb).reversed());

    private HubPlacementOptimizer() {
    }

    /** Same as the monitored overload, without progress reporting or cancellation. */
    public static Result optimize(Environment env, List<Sensor> sensors, SimulationSettings settings, Area area) {
        return optimize(env, sensors, settings, area, ComputationMonitor.NONE);
    }

    /**
     * @throws IllegalArgumentException if there are no sensors or no candidate inside the area
     * @throws CancellationException    if the monitor reports cancellation
     */
    public static Result optimize(Environment env, List<Sensor> sensors, SimulationSettings settings,
                                  Area area, ComputationMonitor monitor) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(area, "area");
        if (sensors.isEmpty()) {
            throw new IllegalArgumentException("At least one sensor is needed to place the hub");
        }

        SimulationSettings averageChannel = averageChannel(settings);
        List<int[]> positions = new ArrayList<>();
        for (int x = (int) Math.ceil(area.minX()); x <= Math.floor(area.maxX()); x++) {
            for (int y = (int) Math.ceil(area.minY()); y <= Math.floor(area.maxY()); y++) {
                positions.add(new int[] { x, y });
            }
        }
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("The area contains no whole-metre position");
        }

        AtomicLong done = new AtomicLong();
        long total = positions.size();
        List<Candidate> candidates = positions.parallelStream()
                .map(position -> {
                    if (monitor.isCancelled()) {
                        throw new CancellationException("Hub placement cancelled");
                    }
                    Candidate candidate = score(env, sensors, averageChannel, position[0], position[1]);
                    monitor.progress(done.incrementAndGet(), total);
                    return candidate;
                })
                .sorted(BEST_FIRST)
                .toList();

        return new Result(candidates.getFirst(), candidates);
    }

    /**
     * Scores a hub at {@code (x, y)} exactly as during the search (without small-scale fading).
     */
    public static Candidate evaluate(Environment env, List<Sensor> sensors, SimulationSettings settings, int x, int y) {
        return score(env, sensors, averageChannel(settings), x, y);
    }

    private static SimulationSettings averageChannel(SimulationSettings settings) {
        SimulationSettings copy = settings.copy();
        copy.setFadingModel(FadingModel.NONE);
        copy.setParallelComputation(false);
        return copy;
    }

    private static Candidate score(Environment env, List<Sensor> sensors, SimulationSettings settings, int x, int y) {
        int unreachable = 0;
        String firstUnreachable = null;
        int reached = 0;
        double worst = Double.POSITIVE_INFINITY;
        double sum = 0.0;
        String weakestReached = null;
        for (Sensor sensor : sensors) {
            CellResult link = PropagationEngine.computeCell(env, List.of(sensor), x, y, settings);
            if (!link.hasEnergy()) {
                unreachable++;
                if (firstUnreachable == null) {
                    firstUnreachable = sensor.getId();
                }
                continue;
            }
            reached++;
            sum += link.linkMarginDb();
            if (link.linkMarginDb() < worst) {
                worst = link.linkMarginDb();
                weakestReached = sensor.getId();
            }
        }
        if (reached == 0) {
            return new Candidate(x, y, unreachable, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, firstUnreachable);
        }
        String weakest = (firstUnreachable != null) ? firstUnreachable : weakestReached;
        return new Candidate(x, y, unreachable, worst, sum / reached, weakest);
    }
}
