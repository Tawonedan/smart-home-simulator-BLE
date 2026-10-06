package io.github.phlekies.smarthome.ui;

import java.util.List;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.AntennaType;
import io.github.phlekies.smarthome.model.Beacon;
import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Scanner;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.util.Format;

/** Side panel tab to add devices and edit the selected sensor or hub. */
final class DevicesSection {

    private final SimulatorModel model;
    private final UiState state;

    private final TextField xField = Ui.textField("5");
    private final TextField yField = Ui.textField("5");
    private final ComboBox<Sensor> sensorCombo = Ui.comboBox(List.of(), null);

    private final Label selectionTitle = new Label();
    private final TextField nameField = Ui.textField("");
    private final ComboBox<AntennaType> antennaCombo = Ui.comboBox(List.of(AntennaType.values()),
            AntennaType.OMNIDIRECTIONAL);
    private final TextField txPowerField = Ui.textField("");
    private final TextField gainField = Ui.textField("");
    private final TextField orientationField = Ui.textField("");
    private final TextField beamwidthField = Ui.textField("");
    private final TextField sharpnessField = Ui.textField("");
    private final TextField polarizationField = Ui.textField("");
    private final TextField hubGainField = Ui.textField("");
    private final TextField hubPolarizationField = Ui.textField("");
    private final TextField uuidField = Ui.textField("");
    private final TextField macField = Ui.textField("");
    private final TextField intervalField = Ui.textField("");
    private final TextField rssiThresholdField = Ui.textField("");

    private final VBox sensorEditor = new VBox(12);
    private final VBox hubEditor = new VBox(12);
    private final Label noSelection = Ui.hint("Select a sensor or the hub on the plan (or in the list above) to edit it.");
    /** Guards against feedback loops while controls are refreshed programmatically. */
    private boolean syncing;

    DevicesSection(SimulatorModel model, UiState state) {
        this.model = model;
        this.state = state;
    }

    VBox build() {
        sensorCombo.setPromptText("Select a sensor");
        sensorCombo.valueProperty().addListener((obs, old, sensor) -> {
            if (!syncing && sensor != null) {
                state.selectedDevice.set(sensor);
            }
        });

        GridPane addForm = Ui.formGrid();
        Ui.addRow(addForm, "Position x, y (m)", Ui.pair(xField, yField));
        GridPane addButtons = Ui.buttonGrid();
        addButtons.add(Ui.primaryButton("Add sensor", e -> addSensorAtFields()), 0, 0);
        addButtons.add(Ui.secondaryButton("Place hub", e -> placeHubAtFields()), 1, 0);
        VBox addSection = Ui.section("Add a device", addForm, addButtons,
                Ui.hint("Or use the Sensor and Hub tools of the toolbar and click on the plan. Drag devices to move them."));

        GridPane sensorForm = Ui.formGrid();
        Ui.addRow(sensorForm, "Antenna", antennaCombo);
        Ui.addRow(sensorForm, "Tx power (dBm)", txPowerField);
        Ui.addRow(sensorForm, "Tx gain (dB)", gainField);
        Ui.addRow(sensorForm, "Orientation (°)", orientationField);
        Ui.addRow(sensorForm, "Beamwidth (°)", beamwidthField);
        Ui.addRow(sensorForm, "Pattern sharpness", sharpnessField);
        Ui.addRow(sensorForm, "Polarization (°)", polarizationField);
        Ui.addRow(sensorForm, "UUID", uuidField);
        Ui.addRow(sensorForm, "MAC address", macField);
        Ui.addRow(sensorForm, "Adv interval (ms)", intervalField);
        sensorEditor.getChildren().add(sensorForm);

        GridPane hubForm = Ui.formGrid();
        Ui.addRow(hubForm, "Rx gain (dB)", hubGainField);
        Ui.addRow(hubForm, "Polarization (°)", hubPolarizationField);
        Ui.addRow(hubForm, "RSSI threshold (dBm)", rssiThresholdField);
        hubEditor.getChildren().add(hubForm);

        GridPane nameForm = Ui.formGrid();
        Ui.addRow(nameForm, "Sensor", sensorCombo);
        Ui.addRow(nameForm, "Name", nameField);

        GridPane selectionButtons = Ui.buttonGrid();
        selectionButtons.add(Ui.primaryButton("Apply", e -> applyChanges()), 0, 0);
        selectionButtons.add(Ui.secondaryButton("Remove", e -> removeSelected()), 1, 0);

        selectionTitle.getStyleClass().add("subsection-title");
        VBox selectionSection = Ui.section("Selected device", nameForm, selectionTitle, noSelection, sensorEditor,
                hubEditor, selectionButtons);

        state.selectedDevice.addListener((obs, old, device) -> showDevice(device));
        model.addListener(change -> {
            if (change == SimulatorModel.Change.DEVICES) {
                refreshSensorList();
                showDevice(state.selectedDevice.get());
            }
        });
        refreshSensorList();
        showDevice(state.selectedDevice.get());

        return new VBox(14, selectionSection, addSection);
    }

    private void refreshSensorList() {
        syncing = true;
        Device selected = state.selectedDevice.get();
        sensorCombo.getItems().setAll(model.sensors());
        if (selected instanceof Sensor sensor) {
            sensorCombo.getSelectionModel().select(sensor);
        } else {
            sensorCombo.getSelectionModel().clearSelection();
        }
        syncing = false;
    }

    private void showDevice(Device device) {
        syncing = true;
        boolean isSensor = device instanceof Sensor;
        boolean isHub = device instanceof Hub;
        setShown(sensorEditor, isSensor);
        setShown(hubEditor, isHub);
        setShown(noSelection, device == null);
        setShown(selectionTitle, device != null);
        nameField.setDisable(device == null);

        if (device != null) {
            selectionTitle.setText((isHub ? "Hub" : "Sensor " + device.getId()) + " at (" + device.getX() + ", "
                    + device.getY() + ") m");
            nameField.setText(device.getName());
            xField.setText(Integer.toString(device.getX()));
            yField.setText(Integer.toString(device.getY()));
        } else {
            nameField.setText("");
        }
        if (device instanceof Sensor sensor) {
            sensorCombo.getSelectionModel().select(sensor);
            antennaCombo.getSelectionModel().select(sensor.getAntennaType());
            txPowerField.setText(Format.number(sensor.getTxPowerDbm(), 1));
            gainField.setText(Format.number(sensor.getTxGainDb(), 1));
            orientationField.setText(Format.number(sensor.getOrientationDeg(), 1));
            beamwidthField.setText(Format.number(sensor.getBeamwidthDeg(), 1));
            sharpnessField.setText(Format.number(sensor.getPatternSharpness(), 2));
            polarizationField.setText(Format.number(sensor.getPolarizationDeg(), 1));
            if (device instanceof Beacon beacon) {
                uuidField.setText(beacon.getUuid());
                macField.setText(beacon.getMacAddress());
                intervalField.setText(Format.number(beacon.getAdvertisingIntervalMs(), 0));
            } else {
                uuidField.setText("");
                macField.setText("");
                intervalField.setText("");
            }
        } else {
            sensorCombo.getSelectionModel().clearSelection();
        }
        if (device instanceof Hub hub) {
            hubGainField.setText(Format.number(hub.getReceiverGainDb(), 1));
            hubPolarizationField.setText(Format.number(hub.getPolarizationDeg(), 1));
            if (device instanceof Scanner scanner) {
                rssiThresholdField.setText(Format.number(scanner.getRssiThresholdDbm(), 0));
            } else {
                rssiThresholdField.setText("");
            }
        }
        syncing = false;
    }

    private static void setShown(javafx.scene.Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void addSensorAtFields() {
        Integer x = Ui.parseInt(xField.getText());
        Integer y = Ui.parseInt(yField.getText());
        if (x == null || y == null) {
            state.status.set("Invalid coordinates: use whole metres, e.g. 12 and 8.");
            return;
        }
        Sensor sensor = model.addSensor(x, y);
        state.selectedDevice.set(sensor);
        state.status.set(sensor.getName() + " added at (" + sensor.getX() + ", " + sensor.getY() + ").");
    }

    private void placeHubAtFields() {
        Integer x = Ui.parseInt(xField.getText());
        Integer y = Ui.parseInt(yField.getText());
        if (x == null || y == null) {
            state.status.set("Invalid coordinates: use whole metres, e.g. 12 and 8.");
            return;
        }
        Hub hub = model.placeHub(x, y);
        state.selectedDevice.set(hub);
        state.status.set("Hub placed at (" + hub.getX() + ", " + hub.getY() + ").");
    }

    private void removeSelected() {
        Device selected = state.selectedDevice.get();
        if (selected instanceof Sensor sensor) {
            model.removeSensor(sensor);
            state.status.set(sensor.getName() + " removed.");
        } else if (selected instanceof Hub) {
            model.removeHub();
            state.status.set("Hub removed.");
        } else {
            state.status.set("Select a sensor or the hub first.");
        }
    }

    private void applyChanges() {
        Device device = state.selectedDevice.get();
        if (device == null) {
            state.status.set("Select a sensor or the hub first.");
            return;
        }
        if (!nameField.getText().isBlank()) {
            device.setName(nameField.getText().strip());
        }
        if (device instanceof Sensor sensor) {
            sensor.setAntennaType(antennaCombo.getValue());
            sensor.setTxPowerDbm(Ui.parseDouble(txPowerField.getText(), sensor.getTxPowerDbm()));
            sensor.setTxGainDb(Ui.parseDouble(gainField.getText(), sensor.getTxGainDb()));
            sensor.setOrientationDeg(Ui.parseDouble(orientationField.getText(), sensor.getOrientationDeg()));
            sensor.setBeamwidthDeg(Ui.parseDouble(beamwidthField.getText(), sensor.getBeamwidthDeg()));
            sensor.setPatternSharpness(Ui.parseDouble(sharpnessField.getText(), sensor.getPatternSharpness()));
            sensor.setPolarizationDeg(Ui.parseDouble(polarizationField.getText(), sensor.getPolarizationDeg()));
            if (device instanceof Beacon beacon) {
                if (!uuidField.getText().isBlank()) {
                    beacon.setUuid(uuidField.getText().strip());
                }
                if (!macField.getText().isBlank()) {
                    beacon.setMacAddress(macField.getText().strip());
                }
                beacon.setAdvertisingIntervalMs(Ui.parseDouble(intervalField.getText(), beacon.getAdvertisingIntervalMs()));
            }
        } else if (device instanceof Hub hub) {
            hub.setReceiverGainDb(Ui.parseDouble(hubGainField.getText(), hub.getReceiverGainDb()));
            hub.setPolarizationDeg(Ui.parseDouble(hubPolarizationField.getText(), hub.getPolarizationDeg()));
            if (device instanceof Scanner scanner) {
                scanner.setRssiThresholdDbm(Ui.parseDouble(rssiThresholdField.getText(), scanner.getRssiThresholdDbm()));
            }
        }
        model.devicesChanged();
        state.status.set("Changes applied to " + device.getName() + ".");
    }
}
