package io.github.phlekies.smarthome.app;

import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.simulation.CellResult;

/** The link of one sensor to the hub. */
public record LinkSummary(Sensor sensor, double distanceMeters, CellResult link) {

    public double receivedPowerDbm() {
        return link.totalPowerDbm();
    }

    public double snrDb() {
        return link.snrDb();
    }

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
