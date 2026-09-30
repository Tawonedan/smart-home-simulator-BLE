package io.github.phlekies.smarthome.simulation.raytrace;

/** A traced ray that reached the hub, with the link budget of that single path. */
public record HubHit(String sensorId,
                     String sensorName,
                     double pathLengthMeters,
                     int interactions,
                     double fsplDb,
                     double interactionLossDb,
                     double receivedPowerDbm,
                     double snrDb,
                     double ber,
                     double capacityMbps) {
}
