package io.github.phlekies.smarthome.ui;

import java.util.List;
import java.util.Locale;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;

/** Side panel section to load or combine the built-in floor plans. */
final class TemplatesSection {

    private final SimulatorModel model;
    private final UiState state;

    TemplatesSection(SimulatorModel model, UiState state) {
        this.model = model;
        this.state = state;
    }

    VBox build() {
        ComboBox<FloorPlanTemplate> templateCombo = Ui.comboBox(List.of(FloorPlanTemplate.values()),
                model.activeTemplate().orElse(FloorPlanTemplate.defaultTemplate()));
        Label description = new Label();
        description.setWrapText(true);
        VBox descriptionBox = new VBox(description);
        descriptionBox.getStyleClass().add("info-box");
        templateCombo.valueProperty().addListener((obs, old, template) -> describe(description, template));
        describe(description, templateCombo.getValue());

        TextField offsetX = Ui.textField("0");
        TextField offsetY = Ui.textField("0");

        GridPane form = Ui.formGrid();
        Ui.addRow(form, "Template", templateCombo);
        Ui.addRow(form, "Offset x, y (m)", Ui.pair(offsetX, offsetY));

        GridPane buttons = Ui.buttonGrid();
        buttons.add(Ui.primaryButton("Load template", e -> {
            FloorPlanTemplate template = templateCombo.getValue();
            model.loadTemplate(template);
            state.status.set(template.displayName() + " loaded. Edit it with the floor plan tools.");
        }), 0, 0);
        buttons.add(Ui.secondaryButton("Clear plan", e -> {
            model.clearWalls();
            state.status.set("Plan cleared. Draw walls or add a template.");
        }), 1, 0);
        buttons.add(Ui.secondaryButton("Add to current plan", e -> {
            FloorPlanTemplate template = templateCombo.getValue();
            double dx = Ui.parseDouble(offsetX.getText(), 0.0);
            double dy = Ui.parseDouble(offsetY.getText(), 0.0);
            model.appendTemplate(template, dx, dy);
            state.status.set(String.format(Locale.US, "%s added with offset (%.1f, %.1f).",
                    template.displayName(), dx, dy));
        }), 0, 1, 2, 1);

        return Ui.section("Templates", form, descriptionBox, buttons);
    }

    private static void describe(Label label, FloorPlanTemplate template) {
        label.setText(template.description() + "\nWalls: " + template.walls().size() + " segments");
    }
}
