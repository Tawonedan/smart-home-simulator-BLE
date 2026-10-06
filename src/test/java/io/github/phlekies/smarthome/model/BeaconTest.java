package io.github.phlekies.smarthome.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

import io.github.phlekies.smarthome.physics.RadioMath;

class BeaconTest {

    @Test
    void defaultValuesMatchBleStandards() {
        Beacon beacon = new Beacon("B1", "Test Beacon", 10, 20);
        assertEquals("B1", beacon.getId());
        assertEquals("Test Beacon", beacon.getName());
        assertEquals(10, beacon.getX());
        assertEquals(20, beacon.getY());
        assertEquals(Beacon.DEFAULT_BLE_TX_POWER_DBM, beacon.getTxPowerDbm());
        assertEquals(Beacon.DEFAULT_UUID, beacon.getUuid());
        assertEquals(Beacon.DEFAULT_MAC_ADDRESS, beacon.getMacAddress());
        assertEquals(Beacon.DEFAULT_ADVERTISING_INTERVAL_MS, beacon.getAdvertisingIntervalMs());
        assertEquals(RadioMath.BLE_CENTER_FREQ_MHZ, beacon.getFrequencyMHz());
        assertEquals(Beacon.DEFAULT_CALIBRATED_RSSI_AT_ONE_METER_DBM, beacon.getCalibratedRssiAtOneMeterDbm());
    }

    @Test
    void deepCopyCopiesAllBleProperties() {
        Beacon original = new Beacon("B1", "Beacon 1", 5, 8);
        original.setTxPowerDbm(4.0);
        original.setTxGainDb(1.5);
        original.setUuid("e2c56db5-dffb-48d2-b060-d0f5a71096e0");
        original.setMacAddress("AA:BB:CC:DD:EE:FF");
        original.setAdvertisingIntervalMs(200.0);
        original.setFrequencyMHz(2402.0);
        original.setCalibratedRssiAtOneMeterDbm(-55.0);

        Beacon copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getName(), copy.getName());
        assertEquals(original.getX(), copy.getX());
        assertEquals(original.getY(), copy.getY());
        assertEquals(4.0, copy.getTxPowerDbm());
        assertEquals(1.5, copy.getTxGainDb());
        assertEquals("e2c56db5-dffb-48d2-b060-d0f5a71096e0", copy.getUuid());
        assertEquals("AA:BB:CC:DD:EE:FF", copy.getMacAddress());
        assertEquals(200.0, copy.getAdvertisingIntervalMs());
        assertEquals(2402.0, copy.getFrequencyMHz());
        assertEquals(-55.0, copy.getCalibratedRssiAtOneMeterDbm());
    }
}
