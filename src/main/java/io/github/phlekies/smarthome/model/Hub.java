package io.github.phlekies.smarthome.model;

import io.github.phlekies.smarthome.physics.RadioMath;

/** The receiving gateway that collects sensor transmissions. */
public class Hub extends Device {

    private double receiverGainDb = 0.0;
    private double polarizationDeg = 0.0;

    /** Creates a hub with an isotropic 0 dB antenna. */
    public Hub(String id, String name, int x, int y) {
        super(id, name, x, y);
    }

    /** Deep copy. */
    public Hub copy() {
        Hub copy = new Hub(getId(), getName(), getX(), getY());
        copy.receiverGainDb = receiverGainDb;
        copy.polarizationDeg = polarizationDeg;
        return copy;
    }

    public double getReceiverGainDb() {
        return receiverGainDb;
    }

    public void setReceiverGainDb(double receiverGainDb) {
        this.receiverGainDb = receiverGainDb;
    }

    public double getPolarizationDeg() {
        return polarizationDeg;
    }

    public void setPolarizationDeg(double polarizationDeg) {
        this.polarizationDeg = RadioMath.normalizeAngleDeg(polarizationDeg);
    }
}
