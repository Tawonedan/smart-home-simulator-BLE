package io.github.phlekies.smarthome.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SensorTest {

    private static Sensor directional(double orientationDeg, double beamwidthDeg) {
        Sensor sensor = new Sensor("S1", "Sensor 1", 0, 0);
        sensor.setAntennaType(AntennaType.DIRECTIONAL);
        sensor.setOrientationDeg(orientationDeg);
        sensor.setBeamwidthDeg(beamwidthDeg);
        return sensor;
    }

    @Test
    void omnidirectionalGainIsTheSameInEveryDirection() {
        Sensor sensor = new Sensor("S1", "Sensor 1", 0, 0);
        sensor.setTxGainDb(2.5);
        for (double angle = 0; angle < 360; angle += 15) {
            assertEquals(2.5, sensor.gainTowardsDb(angle), 1e-12);
        }
    }

    @Test
    void directionalGainPeaksOnBoresight() {
        Sensor sensor = directional(90, 60);
        sensor.setTxGainDb(6.0);
        assertEquals(6.0, sensor.gainTowardsDb(90), 1e-12);
        assertTrue(sensor.gainTowardsDb(100) < 6.0);
        assertEquals(sensor.gainTowardsDb(80), sensor.gainTowardsDb(100), 1e-12, "pattern must be symmetric");
    }

    @Test
    void directionalPatternIsContinuousAtTheBeamEdge() {
        // Regression: the main lobe used to fall to -108 dB at the edge, below the -18 dB side lobes.
        Sensor sensor = directional(0, 90);
        double justInside = sensor.gainTowardsDb(44.99);
        double justOutside = sensor.gainTowardsDb(45.01);
        assertEquals(justInside, justOutside, 0.05);
        for (double angle = 0; angle <= 180; angle += 0.5) {
            assertTrue(sensor.gainTowardsDb(angle) >= -sensor.getSideLobeAttenuationDb() - 6.0 - 1e-9);
        }
    }

    @Test
    void backLobeIsTheWeakestDirection() {
        Sensor sensor = directional(0, 90);
        assertEquals(-18.0 - 6.0, sensor.gainTowardsDb(180), 1e-9);
    }

    @Test
    void omnidirectionalSensorsEmitAFullFan() {
        assertEquals(36, new Sensor("S1", "Sensor 1", 0, 0).emissionAnglesDeg(10).size());
    }

    @Test
    void directionalSensorsOnlyEmitInsideTheBeam() {
        List<Double> angles = directional(90, 90).emissionAnglesDeg(10);
        assertEquals(9, angles.size());
        assertTrue(angles.stream().allMatch(a -> a >= 45 && a < 135));
    }

    @Test
    void beamCrossingZeroDegreesWrapsAround() {
        List<Double> angles = directional(0, 90).emissionAnglesDeg(10);
        assertEquals(10, angles.size());
        assertTrue(angles.contains(315.0) && angles.contains(0.0) && angles.contains(40.0));
    }

    @Test
    void polarizationMismatchLoss() {
        Sensor sensor = new Sensor("S1", "Sensor 1", 0, 0);
        assertEquals(0.0, sensor.polarizationMismatchLossDb(0.0), 1e-12);
        assertEquals(20.0 * Math.log10(2.0), sensor.polarizationMismatchLossDb(60.0), 1e-9); // cos 60° = 1/2
        assertEquals(-20.0 * Math.log10(0.05), sensor.polarizationMismatchLossDb(90.0), 1e-9); // capped
    }

    @Test
    void settersKeepValuesInRange() {
        Sensor sensor = new Sensor("S1", "Sensor 1", 0, 0);
        sensor.setBeamwidthDeg(500);
        sensor.setOrientationDeg(-90);
        sensor.setPatternSharpness(0.1);
        assertEquals(360.0, sensor.getBeamwidthDeg());
        assertEquals(270.0, sensor.getOrientationDeg());
        assertEquals(0.5, sensor.getPatternSharpness());
    }

    @Test
    void copyIsIndependent() {
        Sensor original = directional(30, 60);
        Sensor copy = original.copy();
        original.setOrientationDeg(200);
        original.moveTo(9, 9);
        assertNotSame(original, copy);
        assertEquals(30.0, copy.getOrientationDeg());
        assertEquals(0, copy.getX());
        assertEquals(AntennaType.DIRECTIONAL, copy.getAntennaType());
    }
}
