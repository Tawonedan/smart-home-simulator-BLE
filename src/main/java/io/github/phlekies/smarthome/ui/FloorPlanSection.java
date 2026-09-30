package io.github.phlekies.smarthome.ui;

import java.util.Locale;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Material;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.util.Format;

/** Side panel section with the floor plan editing tools. */
final class FloorPlanSection {

    private final SimulatorModel model;
    private final UiState state;
    private final FloorPlanEditor editor;

    private final ComboBox<Material> materialCombo = Ui.comboBox(Materials.all(), Materials.DRYWALL);
    private final TextField thicknessField = Ui.textField("8.0");
    private final Label selectionLabel = new Label();

    FloorPlanSection(SimulatorModel model, UiState state, FloorPlanEditor editor) {
        this.model = model;
        this.state = state;
        this.editor = editor;
    }

    VBox build() {
        materialCombo.valueProperty().bindBidirectional(state.drawMaterial);
        thicknessField.textProperty().addListener((obs, old, text) ->
                state.drawThicknessCm.set(Math.max(1.0, Ui.parseDouble(text, state.drawThicknessCm.get()))));
        CheckBox snap = Ui.checkBox("Snap to 1 m grid", state.snapToGrid.get());
        snap.selectedProperty().bindBidirectional(state.snapToGrid);

        GridPane form = Ui.formGrid();
        Ui.addRow(form, "Material", materialCombo);
        Ui.addRow(form, "Thickness (cm)", thicknessField);
        Ui.addRow(form, "Grid", snap);

        Button undo = Ui.secondaryButton("Undo", e -> {
            if (model.undo()) state.status.set("Last plan edit undone.");
        });
        Button redo = Ui.secondaryButton("Redo", e -> {
            if (model.redo()) state.status.set("Plan edit redone.");
        });
        GridPane buttons = Ui.buttonGrid();
        buttons.add(Ui.primaryButton("Apply to selected wall", e -> applyToSelection()), 0, 0, 2, 1);
        buttons.add(undo, 0, 1);
        buttons.add(redo, 1, 1);
        buttons.add(Ui.secondaryButton("Cancel drawing", e -> {
            editor.cancelDraft();
            state.status.set(state.tool.get().help());
        }), 0, 2);
        buttons.add(Ui.secondaryButton("Delete selected", e -> deleteSelection()), 1, 2);

        TextField roomX = Ui.textField("4");
        TextField roomY = Ui.textField("4");
        TextField roomWidth = Ui.textField("8");
        TextField roomHeight = Ui.textField("6");
        GridPane roomForm = Ui.formGrid();
        Ui.addRow(roomForm, "Origin x, y (m)", Ui.pair(roomX, roomY));
        Ui.addRow(roomForm, "Size w, h (m)", Ui.pair(roomWidth, roomHeight));
        VBox quickRoom = new VBox(10, roomForm, Ui.primaryButton("Create room", e -> {
            double x = Ui.parseDouble(roomX.getText(), 0.0);
            double y = Ui.parseDouble(roomY.getText(), 0.0);
            double width = Math.max(1.0, Ui.parseDouble(roomWidth.getText(), 4.0));
            double height = Math.max(1.0, Ui.parseDouble(roomHeight.getText(), 4.0));
            model.addRoom(x, y, x + width, y + height, state.drawMaterial.get(), state.drawThicknessCm.get());
            state.status.set(String.format(Locale.US, "Room of %.1f × %.1f m created.", width, height));
        }));
        quickRoom.getStyleClass().add("info-box");

        selectionLabel.setWrapText(true);
        VBox info = new VBox(selectionLabel);
        info.getStyleClass().add("info-box");

        state.selectedWall.addListener((obs, old, wall) -> showSelection());
        model.addListener(change -> {
            if (change == SimulatorModel.Change.WALLS) {
                showSelection();
                undo.setDisable(!model.canUndo());
                redo.setDisable(!model.canRedo());
            }
        });
        showSelection();
        undo.setDisable(!model.canUndo());
        redo.setDisable(!model.canRedo());

        return Ui.section("Walls", Ui.hint("Pick Draw wall, Draw room or Erase in the toolbar. New walls use this material."),
                form, info, buttons, quickRoom);
    }

    private void showSelection() {
        Wall wall = state.selectedWall.get();
        if (wall == null) {
            selectionLabel.setText("No wall selected. Use the Select tool and click a wall.");
            return;
        }
        materialCombo.getSelectionModel().select(wall.getMaterial());
        thicknessField.setText(Format.number(wall.getThicknessCm(), 1));
        selectionLabel.setText(String.format(Locale.US, "Selected wall: %s → %s, %.1f m, %s, %.0f cm",
                Format.point(wall.getX1(), wall.getY1()), Format.point(wall.getX2(), wall.getY2()),
                wall.length(), wall.getMaterial().getName(), wall.getThicknessCm()));
    }

    private void applyToSelection() {
        Wall wall = state.selectedWall.get();
        if (wall == null) {
            state.status.set("Select a wall before applying a material.");
            return;
        }
        model.updateWall(wall, state.drawMaterial.get(), state.drawThicknessCm.get());
        state.status.set("Material and thickness applied to the selected wall.");
    }

    private void deleteSelection() {
        Wall wall = state.selectedWall.get();
        if (wall == null) {
            state.status.set("There is no selected wall to delete.");
            return;
        }
        model.removeWall(wall);
        state.status.set("Wall deleted. Use Undo to bring it back.");
    }
}
