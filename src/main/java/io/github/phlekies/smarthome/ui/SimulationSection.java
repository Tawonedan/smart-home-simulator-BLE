package io.github.phlekies.smarthome.ui;

import java.util.List;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.simulation.FadingModel;
import io.github.phlekies.smarthome.simulation.PropagationMode;
import io.github.phlekies.smarthome.simulation.SimulationSettings;

/** Side panel section with the propagation model options. */
final class SimulationSection {

    private enum Band {
        WIFI_2_4("2.4 GHz (WiFi)", Environment.WIFI_2_4_GHZ_MHZ),
        WIFI_5("5 GHz (WiFi)", Environment.WIFI_5_GHZ_MHZ),
        BLE_2_4("2.4 GHz (BLE)", Environment.BLE_2_4_GHZ_MHZ);

        private final String label;
        private final double freqMHz;

        Band(String label, double freqMHz) {
            this.label = label;
            this.freqMHz = freqMHz;
        }

        static Band of(double freqMHz) {
            if (Math.abs(freqMHz - Environment.BLE_2_4_GHZ_MHZ) < 5.0) return BLE_2_4;
            return freqMHz < 3000 ? WIFI_2_4 : WIFI_5;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final SimulatorModel model;

    SimulationSection(SimulatorModel model) {
        this.model = model;
    }

    VBox build() {
        SimulationSettings settings = model.settings();

        ComboBox<Band> bandCombo = Ui.comboBox(List.of(Band.values()), Band.of(model.environment().getFreqMHz()));
        ComboBox<PropagationMode> modeCombo = Ui.comboBox(List.of(PropagationMode.values()),
                settings.getPropagationMode());
        ComboBox<FadingModel> fadingCombo = Ui.comboBox(List.of(FadingModel.values()), settings.getFadingModel());
        CheckBox diffraction = Ui.checkBox("Diffraction", settings.isDiffractionEnabled());
        CheckBox scattering = Ui.checkBox("Diffuse scattering", settings.isScatteringEnabled());
        CheckBox parallel = Ui.checkBox("Parallel computation", settings.isParallelComputation());

        bandCombo.setOnAction(e -> {
            Band selected = bandCombo.getValue();
            if (selected != null && selected.freqMHz != model.environment().getFreqMHz()) {
                model.environment().setFreqMHz(selected.freqMHz);
                if (selected == Band.BLE_2_4) {
                    model.environment().setBandwidthHz(Environment.BLE_BANDWIDTH_HZ);
                } else if (model.environment().getBandwidthHz() == Environment.BLE_BANDWIDTH_HZ) {
                    model.environment().setBandwidthHz(20e6);
                }
                model.radioChanged();
            }
        });
        modeCombo.setOnAction(e -> {
            if (modeCombo.getValue() != settings.getPropagationMode()) {
                settings.setPropagationMode(modeCombo.getValue());
                model.settingsChanged();
            }
        });
        fadingCombo.setOnAction(e -> {
            if (fadingCombo.getValue() != settings.getFadingModel()) {
                settings.setFadingModel(fadingCombo.getValue());
                model.settingsChanged();
            }
        });
        diffraction.setOnAction(e -> {
            settings.setDiffractionEnabled(diffraction.isSelected());
            model.settingsChanged();
        });
        scattering.setOnAction(e -> {
            settings.setScatteringEnabled(scattering.isSelected());
            model.settingsChanged();
        });
        parallel.setOnAction(e -> {
            settings.setParallelComputation(parallel.isSelected());
            model.settingsChanged();
        });

        // Loading a project or launching rays/waves changes these settings; keep the controls in sync.
        model.addListener(change -> {
            bandCombo.getSelectionModel().select(Band.of(model.environment().getFreqMHz()));
            modeCombo.getSelectionModel().select(settings.getPropagationMode());
            fadingCombo.getSelectionModel().select(settings.getFadingModel());
            diffraction.setSelected(settings.isDiffractionEnabled());
            scattering.setSelected(settings.isScatteringEnabled());
            parallel.setSelected(settings.isParallelComputation());
        });

        GridPane form = Ui.formGrid();
        Ui.addRow(form, "Frequency band", bandCombo);
        Ui.addRow(form, "Propagation", modeCombo);
        Ui.addRow(form, "Fading", fadingCombo);
        Ui.addRow(form, "Mechanisms", new VBox(8, diffraction, scattering, parallel));

        return Ui.section("Propagation model", form,
                Ui.hint("Rays add path powers; waves add them as phasors, so paths can interfere. "
                        + "The heatmap refreshes in the background after every change."));
    }
}
