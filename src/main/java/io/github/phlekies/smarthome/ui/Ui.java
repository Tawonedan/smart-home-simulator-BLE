package io.github.phlekies.smarthome.ui;

import java.util.Collection;

import atlantafx.base.theme.Styles;

import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Small factory for the recurring controls of the side panel. Visual styling lives in app.css. */
final class Ui {

    private Ui() {
    }

    static VBox section(String title, Node... content) {
        Label header = new Label(title);
        header.getStyleClass().add("section-title");
        VBox section = new VBox(12, header);
        section.getChildren().addAll(content);
        section.getStyleClass().add("section");
        section.setFillWidth(true);
        return section;
    }

    static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("form-grid");
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(120);
        labelColumn.setPrefWidth(130);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        return grid;
    }

    static void addRow(GridPane grid, String labelText, Node field) {
        Label label = new Label(labelText);
        label.setWrapText(true);
        label.getStyleClass().add("form-label");
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.addRow(grid.getRowCount(), label, field);
    }

    /** Two equal-width columns of buttons. */
    static GridPane buttonGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("button-grid");
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setFillWidth(true);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        return grid;
    }

    static Button primaryButton(String text, EventHandler<ActionEvent> onAction) {
        Button button = secondaryButton(text, onAction);
        button.getStyleClass().add(Styles.ACCENT);
        return button;
    }

    static Button secondaryButton(String text, EventHandler<ActionEvent> onAction) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(onAction);
        return button;
    }

    static TextField textField(String value) {
        TextField field = new TextField(value);
        field.getStyleClass().add("input");
        field.setMaxWidth(Double.MAX_VALUE);
        field.setPrefColumnCount(5);
        return field;
    }

    static <T> ComboBox<T> comboBox(Collection<T> items, T selected) {
        ComboBox<T> combo = new ComboBox<>();
        combo.getItems().setAll(items);
        combo.getStyleClass().add("input");
        combo.setMaxWidth(Double.MAX_VALUE);
        if (selected != null) {
            combo.getSelectionModel().select(selected);
        }
        return combo;
    }

    static CheckBox checkBox(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        return checkBox;
    }

    static HBox pair(Node first, Node second) {
        HBox row = new HBox(8, first, second);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(first, Priority.ALWAYS);
        HBox.setHgrow(second, Priority.ALWAYS);
        return row;
    }

    static Label hint(ObservableValue<String> text) {
        Label label = new Label();
        label.textProperty().bind(text);
        label.setWrapText(true);
        label.getStyleClass().add("hint");
        return label;
    }

    static Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("hint");
        return label;
    }

    static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
