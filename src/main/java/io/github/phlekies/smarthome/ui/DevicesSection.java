package io.github.phlekies.smarthome.ui;

import java.util.List;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.AntennaType;
import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.util.Format;

/** Side panel section to add, select and configure sensors and the hub. */
final class DevicesSection {

    private final SimulatorModel model;
    private final UiState state;

    private final TextField xField = Ui.textField("5");
    private final TextField yField = Ui.textField("5");
    private final ComboBox<Sensor> sensorCombo = Ui.comboBox(List.of(), null);
    private final ComboBox<AntennaType> antennaCombo = Ui.comboBox(List.of(AntennaType.values()),
            AntennaType.OMNIDIRECTIONAL);
    private final TextField txPowerField = Ui.textField(Format.number(Sensor.DEFAULT_TX_POWER_DBM, 1));
    private final TextField gainField = Ui.textField("0.0");
    private final TextField orientationField = Ui.textField("0.0");
    private final TextField beamwidthField = Ui.textField("90.0");
    private final TextField sharpnessField = Ui.textField("1.80");
    private final TextField polarizationField = Ui.textField("0.0");
    /** Guards against feedback loops while controls are refreshed programmatically. */
    private boolean syncing;

    DevicesSection(SimulatorModel model, UiState state) {
        this.model = model;
        this.state = state;
    }

    VBox build() {
        sensorCombo.setPromptText("Select a sensor");
        refreshSensorList();

        GridPane form = Ui.formGrid();
        Ui.addRow(form, "Position (m)", Ui.pair(xField, yField));
        Ui.addRow(form, "Sensor", sensorCombo);
        Ui.addRow(form, "Antenna", antennaCombo);
        Ui.addRow(form, "Tx power (dBm)", txPowerField);
        Ui.addRow(form, "Tx gain (dB)", gainField);
        Ui.addRow(form, "Orientation (°)", orientationField);
        Ui.addRow(form, "Beamwidth (°)", beamwidthField);
        Ui.addRow(form, "Pattern sharpness", sharpnessField);
        Ui.addRow(form, "Polarization (°)", polarizationField);

        GridPane buttons = Ui.buttonGrid();
        buttons.add(Ui.primaryButton("Add sensor", e -> addSensorAtFields()), 0, 0);
        buttons.add(Ui.secondaryButton("Add hub", e -> placeHubAtFields()), 1, 0);
        buttons.add(Ui.secondaryButton("Place sensor on plan", e -> state.tool.set(EditorTool.SENSOR)), 0, 1);
        buttons.add(Ui.secondaryButton("Place hub on plan", e -> state.tool.set(EditorTool.HUB)), 1, 1);
        buttons.add(Ui.secondaryButton("Remove selected", e -> removeSelected()), 0, 2, 2, 1);

        sensorCombo.valueProperty().addListener((obs, old, sensor) -> {
            if (!syncing && sensor != null) {
                state.selectedDevice.set(sensor);
            }
        });
        state.selectedDevice.addListener((obs, old, device) -> showDevice(device));
        model.addListener(change -> {
            if (change == SimulatorModel.Change.DEVICES) {
                refreshSensorList();
                showDevice(state.selectedDevice.get());
            }
        });

        return Ui.section("Devices",
                form,
                Ui.primaryButton("Apply antenna settings", e -> applyAntenna()),
                buttons,
                Ui.hint("Type coordinates in metres, or pick a placement tool and click on the plan. "
                        + "Right-click a sensor to switch its antenna type."));
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
        if (device == null) {
            return;
        }
        xField.setText(Integer.toString(device.getX()));
        yField.setText(Integer.toString(device.getY()));
        syncing = true;
        if (device instanceof Sensor sensor) {
            sensorCombo.getSelectionModel().select(sensor);
            antennaCombo.getSelectionModel().select(sensor.getAntennaType());
            txPowerField.setText(Format.number(sensor.getTxPowerDbm(), 1));
            gainField.setText(Format.number(sensor.getTxGainDb(), 1));
            orientationField.setText(Format.number(sensor.getOrientationDeg(), 1));
            beamwidthField.setText(Format.number(sensor.getBeamwidthDeg(), 1));
            sharpnessField.setText(Format.number(sensor.getPatternSharpness(), 2));
            polarizationField.setText(Format.number(sensor.getPolarizationDeg(), 1));
        } else if (device instanceof Hub) {
            sensorCombo.getSelectionModel().clearSelection();
        }
        syncing = false;
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

    private void applyAntenna() {
        if (!(state.selectedDevice.get() instanceof Sensor sensor)) {
            state.status.set("Select a sensor to configure its antenna.");
            return;
        }
        sensor.setAntennaType(antennaCombo.getValue());
        sensor.setTxPowerDbm(Ui.parseDouble(txPowerField.getText(), sensor.getTxPowerDbm()));
        sensor.setTxGainDb(Ui.parseDouble(gainField.getText(), sensor.getTxGainDb()));
        sensor.setOrientationDeg(Ui.parseDouble(orientationField.getText(), sensor.getOrientationDeg()));
        sensor.setBeamwidthDeg(Ui.parseDouble(beamwidthField.getText(), sensor.getBeamwidthDeg()));
        sensor.setPatternSharpness(Ui.parseDouble(sharpnessField.getText(), sensor.getPatternSharpness()));
        sensor.setPolarizationDeg(Ui.parseDouble(polarizationField.getText(), sensor.getPolarizationDeg()));
        model.devicesChanged();
        state.status.set("Antenna settings applied to " + sensor.getName() + ".");
    }
}
