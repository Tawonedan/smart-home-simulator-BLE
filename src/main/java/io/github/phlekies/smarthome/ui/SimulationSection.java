package io.github.phlekies.smarthome.ui;

import java.util.List;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.simulation.FadingModel;
import io.github.phlekies.smarthome.simulation.MapMetric;
import io.github.phlekies.smarthome.simulation.PropagationMode;
import io.github.phlekies.smarthome.simulation.SimulationSettings;

/** Side panel section with the heatmap and propagation model options. */
final class SimulationSection {

    private enum Band {
        WIFI_2_4("2.4 GHz", Environment.WIFI_2_4_GHZ_MHZ),
        WIFI_5("5 GHz", Environment.WIFI_5_GHZ_MHZ);

        private final String label;
        private final double freqMHz;

        Band(String label, double freqMHz) {
            this.label = label;
            this.freqMHz = freqMHz;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final SimulatorModel model;
    private final SimulationController controller;

    SimulationSection(SimulatorModel model, SimulationController controller) {
        this.model = model;
        this.controller = controller;
    }

    VBox build() {
        SimulationSettings settings = model.settings();

        ComboBox<Band> bandCombo = Ui.comboBox(List.of(Band.values()), Band.WIFI_2_4);
        ComboBox<MapMetric> metricCombo = Ui.comboBox(List.of(MapMetric.values()), settings.getMapMetric());
        ComboBox<PropagationMode> modeCombo = Ui.comboBox(List.of(PropagationMode.values()),
                settings.getPropagationMode());
        ComboBox<FadingModel> fadingCombo = Ui.comboBox(List.of(FadingModel.values()), settings.getFadingModel());
        CheckBox diffraction = Ui.checkBox("Diffraction", settings.isDiffractionEnabled());
        CheckBox scattering = Ui.checkBox("Diffuse scattering", settings.isScatteringEnabled());
        CheckBox parallel = Ui.checkBox("Parallel computation", settings.isParallelComputation());

        bandCombo.setOnAction(e -> {
            model.environment().setFreqMHz(bandCombo.getValue().freqMHz);
            model.radioChanged();
        });
        metricCombo.setOnAction(e -> {
            settings.setMapMetric(metricCombo.getValue());
            model.displayChanged();
        });
        modeCombo.setOnAction(e -> {
            if (modeCombo.getValue() != settings.getPropagationMode()) {
                settings.setPropagationMode(modeCombo.getValue());
                model.settingsChanged();
            }
        });
        fadingCombo.setOnAction(e -> {
            settings.setFadingModel(fadingCombo.getValue());
            model.settingsChanged();
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

        // The ray and wave actions switch the propagation mode; keep the combo in sync.
        model.addListener(change -> {
            if (change == SimulatorModel.Change.SETTINGS) {
                modeCombo.getSelectionModel().select(settings.getPropagationMode());
            }
        });

        GridPane form = Ui.formGrid();
        Ui.addRow(form, "Frequency band", bandCombo);
        Ui.addRow(form, "Heatmap metric", metricCombo);
        Ui.addRow(form, "Propagation", modeCombo);
        Ui.addRow(form, "Fading", fadingCombo);
        Ui.addRow(form, "Mechanisms", new VBox(8, diffraction, scattering, parallel));

        GridPane buttons = Ui.buttonGrid();
        buttons.add(Ui.primaryButton("Show heatmap", e -> controller.showHeatmap()), 0, 0);
        buttons.add(Ui.secondaryButton("Hide heatmap", e -> controller.hideHeatmap()), 1, 0);

        return Ui.section("Simulation", form, buttons,
                Ui.hint("The heatmap is computed in the background and refreshes automatically after every change."));
    }
}
