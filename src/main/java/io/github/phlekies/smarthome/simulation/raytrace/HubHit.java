package io.github.phlekies.smarthome.simulation.raytrace;

/**
 * A traced ray that reached the hub, with the link budget of that single path.
 *
 * @param sensorId          emitting sensor id
 * @param sensorName        emitting sensor name
 * @param pathLengthMeters  length travelled by the ray
 * @param interactions      walls crossed or reflected on
 * @param fsplDb            free-space loss over the path length
 * @param interactionLossDb summed wall and reflection losses
 * @param receivedPowerDbm  power at the hub
 * @param snrDb             signal-to-noise ratio at the hub
 * @param ber               BPSK bit error rate at that SNR
 * @param capacityMbps      Shannon capacity at that SNR
 */
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
