package io.github.phlekies.smarthome.app;

import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.simulation.CellResult;

/**
 * The link of one sensor to the hub.
 *
 * @param sensor         the transmitting sensor
 * @param distanceMeters straight-line distance between the sensor and the hub
 * @param link           link metrics at the hub, computed with this sensor alone
 */
public record LinkSummary(Sensor sensor, double distanceMeters, CellResult link) {

    /** Power received at the hub from this sensor. */
    public double receivedPowerDbm() {
        return link.totalPowerDbm();
    }

    /** Signal-to-noise ratio of the link. */
    public double snrDb() {
        return link.snrDb();
    }

    /** Received power above the receiver sensitivity. */
    public double marginDb() {
        return link.linkMarginDb();
    }

    /** The hub can decode the sensor (received power at or above the receiver sensitivity). */
    public boolean connected() {
        return hasSignal() && link.linkMarginDb() >= 0.0;
    }

    /** At least one propagation path reaches the hub above the engine's culling threshold. */
    public boolean hasSignal() {
        return link.hasEnergy();
    }
}
