package io.github.phlekies.smarthome.simulation.raytrace;

/**
 * A straight piece of a traced ray, between two interactions.
 *
 * @param startDistanceMeters path length travelled from the sensor when the segment starts
 * @param endDistanceMeters   path length travelled from the sensor when the segment ends
 * @param interactions        walls crossed or reflected on before this segment
 */
public record RaySegment(String sensorId,
                         double x1, double y1, double x2, double y2,
                         double startDistanceMeters, double endDistanceMeters,
                         double startPowerDbm, double endPowerDbm,
                         int interactions) {

    public double lengthMeters() {
        return endDistanceMeters - startDistanceMeters;
    }
}
