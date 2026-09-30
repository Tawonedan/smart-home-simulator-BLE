package core;

public class RayMetrics {
    // === Parámetros básicos ===
    private double txDbm = 0.0;           // Potencia Tx
    private double gtDb = 0.0;            // Ganancia Tx
    private double grDb = 0.0;            // Ganancia Rx
    private double distanceTraveled = 0.0;
    private int numBounces = 0;
    private double fsplDb = 0.0;          // Free-space path loss
    private double wallLossDb = 0.0;      // Pérdida por paredes
    private double prxDbm = 0.0;          // Potencia recibida

    // === Ruido y SNR ===
    private double noiseDbm = 0.0;        // Nivel de ruido
    private double snrDb = 0.0;           // SNR

    // === Parámetros derivados ===
    private double rssi = 0.0;
    private double linkMargin = 0.0;
    private double ber = 0.0;
    private double channelCapacityMbps = 0.0;

    // === Getters & Setters ===
    public double getTxDbm() { return txDbm; }
    public void setTxDbm(double txDbm) { this.txDbm = txDbm; }

    public double getGtDb() { return gtDb; }
    public void setGtDb(double gtDb) { this.gtDb = gtDb; }

    public double getGrDb() { return grDb; }
    public void setGrDb(double grDb) { this.grDb = grDb; }

    public double getDistanceTraveled() { return distanceTraveled; }
    public void setDistanceTraveled(double distanceTraveled) { this.distanceTraveled = distanceTraveled; }

    public int getNumBounces() { return numBounces; }
    public void setNumBounces(int numBounces) { this.numBounces = numBounces; }

    public double getFsplDb() { return fsplDb; }
    public void setFsplDb(double fsplDb) { this.fsplDb = fsplDb; }

    public double getWallLossDb() { return wallLossDb; }
    public void setWallLossDb(double wallLossDb) { this.wallLossDb = wallLossDb; }

    public double getPrxDbm() { return prxDbm; }
    public void setPrxDbm(double prxDbm) { this.prxDbm = prxDbm; }

    public double getNoiseDbm() { return noiseDbm; }
    public void setNoiseDbm(double noiseDbm) { this.noiseDbm = noiseDbm; }

    public double getSnrDb() { return snrDb; }
    public void setSnrDb(double snrDb) { this.snrDb = snrDb; }

    public double getRssi() { return rssi; }
    public void setRssi(double rssi) { this.rssi = rssi; }

    public double getLinkMargin() { return linkMargin; }
    public void setLinkMargin(double linkMargin) { this.linkMargin = linkMargin; }

    public double getBer() { return ber; }
    public void setBer(double ber) { this.ber = ber; }

    public double getChannelCapacityMbps() { return channelCapacityMbps; }
    public void setChannelCapacityMbps(double channelCapacityMbps) { this.channelCapacityMbps = channelCapacityMbps; }
}

