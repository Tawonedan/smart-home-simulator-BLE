package io.github.phlekies.smarthome.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.phlekies.smarthome.physics.RadioMath;

/**
 * Physical radio environment: carrier, receiver noise parameters and the walls of the floor plan.
 *
 * <p>Units: frequency in MHz, bandwidth in Hz, losses and gains in dB, distances in metres.
 */
public class Environment {

    public static final double WIFI_2_4_GHZ_MHZ = 2400.0;
    public static final double WIFI_5_GHZ_MHZ = 5200.0;

    private double freqMHz = WIFI_2_4_GHZ_MHZ;
    private double bandwidthHz = 20e6;
    private double noiseFigureDb = 7.0;
    /** Optional extra attenuation per metre of path (furniture, people...). */
    private double alphaDbPerMeter = 0.0;
    /** Optional lumped system gain/margin added to every link. */
    private double systemGainDb = 0.0;
    private final List<Wall> walls = new ArrayList<>();

    /** Deep copy (walls included), safe to hand to a background computation. */
    public Environment copy() {
        Environment copy = new Environment();
        copy.copyFrom(this);
        return copy;
    }

    /** Overwrites every parameter and wall of this environment with copies of another one's. */
    public void copyFrom(Environment other) {
        if (other == this) {
            return;
        }
        freqMHz = other.freqMHz;
        bandwidthHz = other.bandwidthHz;
        noiseFigureDb = other.noiseFigureDb;
        alphaDbPerMeter = other.alphaDbPerMeter;
        systemGainDb = other.systemGainDb;
        setWalls(other.walls);
    }

    public double noiseFloorDbm() {
        return RadioMath.noiseFloorDbm(bandwidthHz, noiseFigureDb);
    }

    public double wavelengthMeters() {
        return RadioMath.wavelengthMeters(freqMHz);
    }

    /** The live wall instances (read-only list). */
    public List<Wall> getWalls() {
        return Collections.unmodifiableList(walls);
    }

    /** Replaces all walls with copies of the given ones. */
    public void setWalls(List<Wall> newWalls) {
        walls.clear();
        if (newWalls != null) {
            newWalls.forEach(wall -> walls.add(wall.copy()));
        }
    }

    /** Adds the given wall instance (not a copy) so callers can keep a reference to it. */
    public void addWall(Wall wall) {
        walls.add(wall);
    }

    public boolean removeWall(Wall wall) {
        return walls.remove(wall);
    }

    public double getFreqMHz() {
        return freqMHz;
    }

    public void setFreqMHz(double freqMHz) {
        if (freqMHz <= 0) throw new IllegalArgumentException("freqMHz must be > 0");
        this.freqMHz = freqMHz;
    }

    public double getBandwidthHz() {
        return bandwidthHz;
    }

    public void setBandwidthHz(double bandwidthHz) {
        if (bandwidthHz <= 0) throw new IllegalArgumentException("bandwidthHz must be > 0");
        this.bandwidthHz = bandwidthHz;
    }

    public double getNoiseFigureDb() {
        return noiseFigureDb;
    }

    public void setNoiseFigureDb(double noiseFigureDb) {
        this.noiseFigureDb = Math.max(0.0, noiseFigureDb);
    }

    public double getAlphaDbPerMeter() {
        return alphaDbPerMeter;
    }

    public void setAlphaDbPerMeter(double alphaDbPerMeter) {
        this.alphaDbPerMeter = Math.max(0.0, alphaDbPerMeter);
    }

    public double getSystemGainDb() {
        return systemGainDb;
    }

    public void setSystemGainDb(double systemGainDb) {
        this.systemGainDb = systemGainDb;
    }
}
