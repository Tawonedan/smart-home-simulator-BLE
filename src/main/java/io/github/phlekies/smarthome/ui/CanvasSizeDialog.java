package io.github.phlekies.smarthome.ui;

import atlantafx.base.theme.Styles;
import io.github.phlekies.smarthome.app.SimulatorModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class CanvasSizeDialog {

    private CanvasSizeDialog() {
    }

    public static void show(Window owner, SimulatorModel model) {
        show(model, owner);
    }

    public static void show(SimulatorModel model, Window owner) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Canvas Size");
        stage.setResizable(false);

        Label header = new Label("Customise Canvas Size");
        header.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label desc = new Label("Set floor plan dimensions in metres (10 to 500 m).");
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");
        desc.setWrapText(true);

        TextField widthField = new TextField(Integer.toString(model.planWidthMeters()));
        TextField heightField = new TextField(Integer.toString(model.planHeightMeters()));
        widthField.setPrefWidth(90);
        heightField.setPrefWidth(90);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, new Label("Width (m):"), widthField);
        form.addRow(1, new Label("Height (m):"), heightField);

        Label presetsLabel = new Label("Presets:");
        presetsLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        HBox presetsRow1 = new HBox(6);
        Button p50x40 = new Button("50 × 40 m (Default)");
        p50x40.setOnAction(e -> { widthField.setText("50"); heightField.setText("40"); });
        Button p60x40 = new Button("60 × 40 m");
        p60x40.setOnAction(e -> { widthField.setText("60"); heightField.setText("40"); });
        Button p80x60 = new Button("80 × 60 m");
        p80x60.setOnAction(e -> { widthField.setText("80"); heightField.setText("60"); });
        presetsRow1.getChildren().addAll(p50x40, p60x40, p80x60);

        HBox presetsRow2 = new HBox(6);
        Button p100x80 = new Button("100 × 80 m");
        p100x80.setOnAction(e -> { widthField.setText("100"); heightField.setText("80"); });
        Button p120x90 = new Button("120 × 90 m");
        p120x90.setOnAction(e -> { widthField.setText("120"); heightField.setText("90"); });
        Button p150x100 = new Button("150 × 100 m");
        p150x100.setOnAction(e -> { widthField.setText("150"); heightField.setText("100"); });
        presetsRow2.getChildren().addAll(p100x80, p120x90, p150x100);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button apply = new Button("Apply");
        apply.getStyleClass().add(Styles.ACCENT);
        apply.setDefaultButton(true);
        apply.setOnAction(e -> {
            try {
                int w = Integer.parseInt(widthField.getText().trim());
                int h = Integer.parseInt(heightField.getText().trim());
                if (w < 10 || w > 500 || h < 10 || h > 500) {
                    errorLabel.setText("Width and height must be between 10 m and 500 m.");
                    errorLabel.setVisible(true);
                    errorLabel.setManaged(true);
                    return;
                }
                model.setPlanDimensions(w, h);
                stage.close();
            } catch (NumberFormatException ex) {
                errorLabel.setText("Please enter valid integer numbers.");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            }
        });

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());

        HBox actions = new HBox(10, cancel, apply);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, header, desc, form, presetsLabel, presetsRow1, presetsRow2, errorLabel, actions);
        root.setPadding(new Insets(18));
        root.setPrefWidth(350);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(SimulatorApp.class.getResource("app.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
