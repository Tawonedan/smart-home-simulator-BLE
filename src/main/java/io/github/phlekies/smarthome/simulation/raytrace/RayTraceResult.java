package io.github.phlekies.smarthome.simulation.raytrace;

import java.util.List;

/**
 * Output of {@link RayTracer#trace}.
 *
 * @param hubHits   rays that reached the hub, sorted by path length (arrival order)
 * @param truncated true if the segment budget was exhausted before every ray finished
 */
public record RayTraceResult(List<RaySegment> segments, List<HubHit> hubHits, boolean truncated) {

    public RayTraceResult {
        segments = List.copyOf(segments);
        hubHits = List.copyOf(hubHits);
    }

    public double maxPathLengthMeters() {
        return segments.stream().mapToDouble(RaySegment::endDistanceMeters).max().orElse(0.0);
    }
}
