package core;

public class RayMetrics {
    // === Parámetros básicos ===
    private double distanceTraveled = 0.0;
    private int numBounces = 0;
    private double fsplDb = 0.0;
    private double prxDbm = 0.0;
    private double txDbm = 0.0;

    // === Parámetros derivados ===
    private double rssi = 0.0;
    private double snrDb = 0.0;
    private double linkMargin = 0.0;
    private double ber = 0.0;
    private double channelCapacityMbps = 0.0;

    // === Getters & Setters ===
    public double getDistanceTraveled() { return distanceTraveled; }
    public void setDistanceTraveled(double distanceTraveled) { this.distanceTraveled = distanceTraveled; }

    public int getNumBounces() { return numBounces; }
    public void setNumBounces(int numBounces) { this.numBounces = numBounces; }

    public double getFsplDb() { return fsplDb; }
    public void setFsplDb(double fsplDb) { this.fsplDb = fsplDb; }

    public double getPrxDbm() { return prxDbm; }
    public void setPrxDbm(double prxDbm) { this.prxDbm = prxDbm; }

    public double getTxDbm() { return txDbm; }
    public void setTxDbm(double txDbm) { this.txDbm = txDbm; }

    public double getRssi() { return rssi; }
    public void setRssi(double rssi) { this.rssi = rssi; }

    public double getSnrDb() { return snrDb; }
    public void setSnrDb(double snrDb) { this.snrDb = snrDb; }

    public double getLinkMargin() { return linkMargin; }
    public void setLinkMargin(double linkMargin) { this.linkMargin = linkMargin; }

    public double getBer() { return ber; }
    public void setBer(double ber) { this.ber = ber; }

    public double getChannelCapacityMbps() { return channelCapacityMbps; }
    public void setChannelCapacityMbps(double channelCapacityMbps) { this.channelCapacityMbps = channelCapacityMbps; }
}

