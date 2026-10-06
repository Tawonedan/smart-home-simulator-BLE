package io.github.phlekies.smarthome.app;

import java.util.List;

import io.github.phlekies.smarthome.model.AntennaType;
import io.github.phlekies.smarthome.model.Beacon;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Scanner;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;
import io.github.phlekies.smarthome.simulation.SimulationSettings;

/** The smart apartment shown at start-up: typical home sensors and a hub in a so-so spot. */
public final class DemoScenario {

    /** Typical transmit power of a battery-powered smart-home sensor. */
    private static final double BATTERY_TX_DBM = 0.0;

    private DemoScenario() {
    }

    /** Two-bedroom apartment with six typical smart-home devices and a hub in a corner. */
    public static ProjectState smartApartment() {
        FloorPlanTemplate template = FloorPlanTemplate.TWO_BEDROOM_APARTMENT;
        Environment env = new Environment();
        env.setWalls(template.walls());
        env.setFreqMHz(Environment.BLE_2_4_GHZ_MHZ);
        env.setBandwidthHz(Environment.BLE_BANDWIDTH_HZ);

        // Mains-powered devices transmit more than the battery ones (Zigbee/BLE-class radios at ~0 dBm).
        Sensor camera = sensor("S6", "Security camera", 39, 26, 10.0);
        camera.setAntennaType(AntennaType.DIRECTIONAL);
        camera.setOrientationDeg(225);
        camera.setBeamwidthDeg(120);
        camera.setTxGainDb(3.0);

        List<Sensor> sensors = List.of(
                sensor("S1", "Thermostat", 7, 12, BATTERY_TX_DBM),
                sensor("S2", "Smoke detector", 24, 5, BATTERY_TX_DBM),
                sensor("S3", "Door sensor", 40, 3, BATTERY_TX_DBM),
                sensor("S4", "Window sensor", 4, 26, BATTERY_TX_DBM),
                sensor("S5", "Smart plug", 20, 25, 3.0),
                camera);

        return new ProjectState(template, env, new SimulationSettings(), sensors, new Scanner("H1", "Hub", 5, 4));
    }

    private static Sensor sensor(String id, String name, int x, int y, double txPowerDbm) {
        Beacon sensor = new Beacon(id, name, x, y);
        sensor.setTxPowerDbm(txPowerDbm);
        return sensor;
    }

    /** Open-plan office with occupancy sensors, meeting-room displays and access control. */
    public static ProjectState officeFloor() {
        FloorPlanTemplate template = FloorPlanTemplate.OPEN_PLAN_OFFICE;
        Environment env = new Environment();
        env.setWalls(template.walls());
        List<Sensor> sensors = List.of(
                sensor("S1", "Occupancy sensor A", 8, 6, BATTERY_TX_DBM),
                sensor("S2", "Occupancy sensor B", 30, 6, BATTERY_TX_DBM),
                sensor("S3", "Meeting room 1", 8, 25, BATTERY_TX_DBM),
                sensor("S4", "Meeting room 2", 20, 25, BATTERY_TX_DBM),
                sensor("S5", "Meeting room 3", 31, 25, BATTERY_TX_DBM),
                sensor("S6", "Air quality monitor", 20, 16, BATTERY_TX_DBM),
                sensor("S7", "Access control", 42, 4, BATTERY_TX_DBM));
        return new ProjectState(template, env, new SimulationSettings(), sensors, new Hub("H1", "Hub", 20, 8));
    }

    /** Warehouse whose metal shelving blocks the signal of the trackers; the hub sits in the office. */
    public static ProjectState warehouse() {
        FloorPlanTemplate template = FloorPlanTemplate.WAREHOUSE_WITH_AISLES;
        Environment env = new Environment();
        env.setWalls(template.walls());
        List<Sensor> sensors = List.of(
                sensor("S1", "Temperature logger 1", 15, 12, BATTERY_TX_DBM),
                sensor("S2", "Temperature logger 2", 27, 28, BATTERY_TX_DBM),
                sensor("S3", "Forklift beacon", 35, 20, BATTERY_TX_DBM),
                sensor("S4", "Dock door sensor", 43, 30, BATTERY_TX_DBM),
                sensor("S5", "Shelf weight sensor", 21, 8, BATTERY_TX_DBM));
        return new ProjectState(template, env, new SimulationSettings(), sensors, new Hub("H1", "Hub", 6, 6));
    }

    public static ProjectState bleDeployment() {
        FloorPlanTemplate template = FloorPlanTemplate.TWO_BEDROOM_APARTMENT;
        Environment env = new Environment();
        env.setWalls(template.walls());
        env.setFreqMHz(Environment.BLE_2_4_GHZ_MHZ);
        env.setBandwidthHz(Environment.BLE_BANDWIDTH_HZ);

        SimulationSettings settings = new SimulationSettings();
        settings.setReceiverSensitivityDbm(Scanner.DEFAULT_RX_SENSITIVITY_DBM);
        settings.setLogDistanceExponent(2.5);

        List<Sensor> beacons = List.of(
                beacon("B1", "Asset Beacon (Living)", 7, 12, -4.0),
                beacon("B2", "Temp Beacon (Kitchen)", 24, 5, -6.0),
                beacon("B3", "Door Beacon", 40, 3, -6.0),
                beacon("B4", "Window Beacon", 4, 26, -6.0),
                beacon("B5", "Wearable Tag", 20, 25, 0.0),
                beacon("B6", "Desk Beacon", 39, 26, -4.0));

        return new ProjectState(template, env, settings, beacons, new Scanner("S1", "BLE Scanner", 20, 15));
    }

    private static Beacon beacon(String id, String name, int x, int y, double txPowerDbm) {
        Beacon b = new Beacon(id, name, x, y);
        b.setTxPowerDbm(txPowerDbm);
        return b;
    }
}
