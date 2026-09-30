package io.github.phlekies.smarthome.simulation;

import io.github.phlekies.smarthome.physics.RadioMath;

/** Tunable parameters of the propagation engine. Mutable; engines work on a {@link #copy()}. */
public class SimulationSettings {

    private PropagationMode propagationMode = PropagationMode.RAYS;
    private MapMetric mapMetric = MapMetric.POWER_DBM;
    private FadingModel fadingModel = FadingModel.NONE;
    private boolean diffractionEnabled = true;
    private boolean scatteringEnabled = true;
    private boolean parallelComputation = true;
    private double logDistanceExponent = 2.1;
    private double ricianKFactorDb = 6.0;
    /** Paths weaker than this are discarded to save work. */
    private double cullingThresholdDbm = -115.0;
    private double receiverGainDb = 0.0;
    private double receiverPolarizationDeg = 0.0;
    private double receiverSensitivityDbm = -92.0;
    private int maxReflectionPaths = 2;
    private int maxDiffractionPaths = 2;
    private int maxScatteringPaths = 1;

    public SimulationSettings() {
    }

    public SimulationSettings(SimulationSettings other) {
        copyFrom(other);
    }

    public SimulationSettings copy() {
        return new SimulationSettings(this);
    }

    /** Overwrites every parameter with another instance's values. */
    public final void copyFrom(SimulationSettings other) {
        this.propagationMode = other.propagationMode;
        this.mapMetric = other.mapMetric;
        this.fadingModel = other.fadingModel;
        this.diffractionEnabled = other.diffractionEnabled;
        this.scatteringEnabled = other.scatteringEnabled;
        this.parallelComputation = other.parallelComputation;
        this.logDistanceExponent = other.logDistanceExponent;
        this.ricianKFactorDb = other.ricianKFactorDb;
        this.cullingThresholdDbm = other.cullingThresholdDbm;
        this.receiverGainDb = other.receiverGainDb;
        this.receiverPolarizationDeg = other.receiverPolarizationDeg;
        this.receiverSensitivityDbm = other.receiverSensitivityDbm;
        this.maxReflectionPaths = other.maxReflectionPaths;
        this.maxDiffractionPaths = other.maxDiffractionPaths;
        this.maxScatteringPaths = other.maxScatteringPaths;
    }

    public PropagationMode getPropagationMode() {
        return propagationMode;
    }

    public void setPropagationMode(PropagationMode propagationMode) {
        this.propagationMode = (propagationMode == null) ? PropagationMode.RAYS : propagationMode;
    }

    public MapMetric getMapMetric() {
        return mapMetric;
    }

    public void setMapMetric(MapMetric mapMetric) {
        this.mapMetric = (mapMetric == null) ? MapMetric.POWER_DBM : mapMetric;
    }

    public FadingModel getFadingModel() {
        return fadingModel;
    }

    public void setFadingModel(FadingModel fadingModel) {
        this.fadingModel = (fadingModel == null) ? FadingModel.NONE : fadingModel;
    }

    public boolean isDiffractionEnabled() {
        return diffractionEnabled;
    }

    public void setDiffractionEnabled(boolean diffractionEnabled) {
        this.diffractionEnabled = diffractionEnabled;
    }

    public boolean isScatteringEnabled() {
        return scatteringEnabled;
    }

    public void setScatteringEnabled(boolean scatteringEnabled) {
        this.scatteringEnabled = scatteringEnabled;
    }

    public boolean isParallelComputation() {
        return parallelComputation;
    }

    public void setParallelComputation(boolean parallelComputation) {
        this.parallelComputation = parallelComputation;
    }

    public double getLogDistanceExponent() {
        return logDistanceExponent;
    }

    public void setLogDistanceExponent(double logDistanceExponent) {
        this.logDistanceExponent = Math.max(1.0, logDistanceExponent);
    }

    public double getRicianKFactorDb() {
        return ricianKFactorDb;
    }

    public void setRicianKFactorDb(double ricianKFactorDb) {
        this.ricianKFactorDb = Math.max(0.0, ricianKFactorDb);
    }

    public double getCullingThresholdDbm() {
        return cullingThresholdDbm;
    }

    public void setCullingThresholdDbm(double cullingThresholdDbm) {
        this.cullingThresholdDbm = cullingThresholdDbm;
    }

    public double getReceiverGainDb() {
        return receiverGainDb;
    }

    public void setReceiverGainDb(double receiverGainDb) {
        this.receiverGainDb = receiverGainDb;
    }

    public double getReceiverPolarizationDeg() {
        return receiverPolarizationDeg;
    }

    public void setReceiverPolarizationDeg(double receiverPolarizationDeg) {
        this.receiverPolarizationDeg = RadioMath.normalizeAngleDeg(receiverPolarizationDeg);
    }

    public double getReceiverSensitivityDbm() {
        return receiverSensitivityDbm;
    }

    public void setReceiverSensitivityDbm(double receiverSensitivityDbm) {
        this.receiverSensitivityDbm = receiverSensitivityDbm;
    }

    public int getMaxReflectionPaths() {
        return maxReflectionPaths;
    }

    public void setMaxReflectionPaths(int maxReflectionPaths) {
        this.maxReflectionPaths = Math.max(0, maxReflectionPaths);
    }

    public int getMaxDiffractionPaths() {
        return maxDiffractionPaths;
    }

    public void setMaxDiffractionPaths(int maxDiffractionPaths) {
        this.maxDiffractionPaths = Math.max(0, maxDiffractionPaths);
    }

    public int getMaxScatteringPaths() {
        return maxScatteringPaths;
    }

    public void setMaxScatteringPaths(int maxScatteringPaths) {
        this.maxScatteringPaths = Math.max(0, maxScatteringPaths);
    }

    /** Search radius step used to decide which wall edges and surfaces are worth evaluating. */
    public double adaptiveStepMeters(double distanceMeters) {
        if (distanceMeters < 4.0) return 0.25;
        if (distanceMeters < 12.0) return 0.5;
        if (distanceMeters < 24.0) return 0.75;
        return 1.25;
    }
}
