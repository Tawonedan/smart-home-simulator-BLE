package io.github.phlekies.smarthome.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

class ScannerTest {

    @Test
    void defaultValuesMatchBleStandards() {
        Scanner scanner = new Scanner("S1", "BLE Scanner", 12, 14);
        assertEquals("S1", scanner.getId());
        assertEquals("BLE Scanner", scanner.getName());
        assertEquals(12, scanner.getX());
        assertEquals(14, scanner.getY());
        assertEquals(Scanner.DEFAULT_RX_SENSITIVITY_DBM, scanner.getRxSensitivityDbm());
        assertEquals(Scanner.DEFAULT_RECEIVER_GAIN_DB, scanner.getReceiverGainDb());
        assertEquals(Scanner.DEFAULT_RSSI_THRESHOLD_DBM, scanner.getRssiThresholdDbm());
    }

    @Test
    void deepCopyCopiesAllScannerProperties() {
        Scanner original = new Scanner("S1", "Scanner 1", 10, 20);
        original.setReceiverGainDb(2.0);
        original.setPolarizationDeg(45.0);
        original.setRxSensitivityDbm(-104.0);
        original.setRssiThresholdDbm(-110.0);

        Scanner copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getName(), copy.getName());
        assertEquals(original.getX(), copy.getX());
        assertEquals(original.getY(), copy.getY());
        assertEquals(2.0, copy.getReceiverGainDb());
        assertEquals(45.0, copy.getPolarizationDeg());
        assertEquals(-104.0, copy.getRxSensitivityDbm());
        assertEquals(-110.0, copy.getRssiThresholdDbm());
    }
}
