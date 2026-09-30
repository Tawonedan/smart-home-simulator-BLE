package io.github.phlekies.smarthome.simulation.raytrace;

/**
 * A straight piece of a traced ray, between two interactions.
 *
 * @param sensorId            emitting sensor id
 * @param x1                  start x in metres
 * @param y1                  start y in metres
 * @param x2                  end x in metres
 * @param y2                  end y in metres
 * @param startDistanceMeters path length travelled from the sensor when the segment starts
 * @param endDistanceMeters   path length travelled from the sensor when the segment ends
 * @param startPowerDbm       ray power at the start of the segment
 * @param endPowerDbm         ray power at the end of the segment
 * @param interactions        walls crossed or reflected on before this segment
 */
public record RaySegment(String sensorId,
                         double x1, double y1, double x2, double y2,
                         double startDistanceMeters, double endDistanceMeters,
                         double startPowerDbm, double endPowerDbm,
                         int interactions) {

    /** Length of the segment in metres. */
    public double lengthMeters() {
        return endDistanceMeters - startDistanceMeters;
    }
}
