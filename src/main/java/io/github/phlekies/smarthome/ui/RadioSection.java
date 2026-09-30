package io.github.phlekies.smarthome.ui;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.simulation.SimulationSettings;
import io.github.phlekies.smarthome.util.Format;

/** Side panel section with the radio channel and receiver parameters. */
final class RadioSection {

    private final SimulatorModel model;
    private final UiState state;

    RadioSection(SimulatorModel model, UiState state) {
        this.model = model;
        this.state = state;
    }

    VBox build() {
        Environment env = model.environment();
        SimulationSettings settings = model.settings();

        TextField exponentField = Ui.textField(Format.number(settings.getLogDistanceExponent(), 2));
        TextField kFactorField = Ui.textField(Format.number(settings.getRicianKFactorDb(), 1));
        TextField cullingField = Ui.textField(Format.number(settings.getCullingThresholdDbm(), 0));
        TextField sensitivityField = Ui.textField(Format.number(settings.getReceiverSensitivityDbm(), 0));
        TextField bandwidthField = Ui.textField(Format.number(env.getBandwidthHz() / 1e6, 1));
        TextField noiseFigureField = Ui.textField(Format.number(env.getNoiseFigureDb(), 1));

        GridPane form = Ui.formGrid();
        Ui.addRow(form, "Path-loss exponent n", exponentField);
        Ui.addRow(form, "Rician K-factor (dB)", kFactorField);
        Ui.addRow(form, "Path culling (dBm)", cullingField);
        Ui.addRow(form, "Rx sensitivity (dBm)", sensitivityField);
        Ui.addRow(form, "Bandwidth (MHz)", bandwidthField);
        Ui.addRow(form, "Noise figure (dB)", noiseFigureField);

        return Ui.section("Radio channel", form, Ui.primaryButton("Apply radio settings", e -> {
            settings.setLogDistanceExponent(Ui.parseDouble(exponentField.getText(), settings.getLogDistanceExponent()));
            settings.setRicianKFactorDb(Ui.parseDouble(kFactorField.getText(), settings.getRicianKFactorDb()));
            settings.setCullingThresholdDbm(Ui.parseDouble(cullingField.getText(), settings.getCullingThresholdDbm()));
            settings.setReceiverSensitivityDbm(
                    Ui.parseDouble(sensitivityField.getText(), settings.getReceiverSensitivityDbm()));
            double bandwidthMHz = Ui.parseDouble(bandwidthField.getText(), env.getBandwidthHz() / 1e6);
            if (bandwidthMHz > 0) {
                env.setBandwidthHz(bandwidthMHz * 1e6);
            }
            env.setNoiseFigureDb(Ui.parseDouble(noiseFigureField.getText(), env.getNoiseFigureDb()));

            exponentField.setText(Format.number(settings.getLogDistanceExponent(), 2));
            kFactorField.setText(Format.number(settings.getRicianKFactorDb(), 1));
            bandwidthField.setText(Format.number(env.getBandwidthHz() / 1e6, 1));
            noiseFigureField.setText(Format.number(env.getNoiseFigureDb(), 1));
            model.settingsChanged();
            model.radioChanged();
            state.status.set("Radio settings applied. Noise floor: " + Format.dbm(env.noiseFloorDbm()) + ".");
        }));
    }
}
