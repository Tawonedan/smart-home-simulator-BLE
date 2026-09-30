package io.github.phlekies.smarthome.ui;

import java.util.Locale;
import java.util.function.Function;

import atlantafx.base.theme.Styles;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import io.github.phlekies.smarthome.app.LinkSummary;
import io.github.phlekies.smarthome.simulation.CoverageStats;

/** Side panel tab with the coverage figures, the sensor links and the hub optimiser. */
final class ResultsSection {

    private final UiState state;
    private final SimulationController simulation;
    private final ProjectController projects;

    ResultsSection(UiState state, SimulationController simulation, ProjectController projects) {
        this.state = state;
        this.simulation = simulation;
        this.projects = projects;
    }

    VBox build() {
        return new VBox(14, coverageSection(), linksSection(), hubSection(), reportSection());
    }

    private VBox coverageSection() {
        Label covered = kpiValue();
        Label median = kpiValue();
        Label p10 = kpiValue();
        Label sinr = kpiValue();
        FlowPane tiles = new FlowPane(10, 10,
                kpi("Usable signal", covered), kpi("Median signal", median),
                kpi("Signal in 90% of area", p10), kpi("Median SINR", sinr));
        Label hint = Ui.hint("");

        Runnable refresh = () -> {
            CoverageStats stats = state.coverage.get();
            boolean empty = stats == null || stats.isEmpty();
            covered.setText(empty ? "–" : String.format(Locale.US, "%.0f%%", stats.coveredFraction() * 100));
            median.setText(empty ? "–" : String.format(Locale.US, "%.0f dBm", stats.medianSignalDbm()));
            p10.setText(empty ? "–" : String.format(Locale.US, "≥ %.0f dBm", stats.signalP10Dbm()));
            sinr.setText(empty ? "–" : String.format(Locale.US, "%.1f dB", stats.medianSinrDb()));
            hint.setText(empty
                    ? "Turn on the heatmap to measure the coverage of the building."
                    : String.format(Locale.US, "Over %d m² of building footprint. Usable = strongest sensor above %.0f dBm. SINR counts the other sensors as interference.",
                            stats.cellCount(), stats.sensitivityDbm()));
        };
        state.coverage.addListener((obs, old, stats) -> refresh.run());
        refresh.run();
        return Ui.section("Coverage", tiles, hint);
    }

    private VBox linksSection() {
        TableView<LinkSummary> table = new TableView<>();
        table.getStyleClass().addAll(Styles.DENSE, "links-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("Place a hub and some sensors."));
        table.setFixedCellSize(30);
        table.getColumns().add(textColumn("Sensor", 120, link -> link.sensor().getName()));
        table.getColumns().add(textColumn("Dist.", 48, link -> String.format(Locale.US, "%.0f m", link.distanceMeters())));
        table.getColumns().add(textColumn("Rx", 66, link -> link.hasSignal()
                ? String.format(Locale.US, "%.0f dBm", link.receivedPowerDbm()) : "-"));

        TableColumn<LinkSummary, LinkSummary> margin = new TableColumn<>("Margin");
        margin.setPrefWidth(74);
        margin.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        margin.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LinkSummary link, boolean empty) {
                super.updateItem(link, empty);
                getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
                if (empty || link == null) {
                    setText(null);
                    return;
                }
                setText(link.hasSignal() ? String.format(Locale.US, "%+.0f dB", link.marginDb()) : "none");
                getStyleClass().add(link.connected() ? Styles.SUCCESS : Styles.DANGER);
            }
        });
        table.getColumns().add(margin);

        table.itemsProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                () -> javafx.collections.FXCollections.observableArrayList(state.links.get()), state.links));
        table.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> 48.0 + table.getFixedCellSize() * Math.max(2, state.links.get().size()), state.links));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, link) -> {
            if (link != null) {
                state.selectedDevice.set(link.sensor());
            }
        });

        Label summary = Ui.hint("");
        state.links.addListener((obs, old, links) -> summarise(summary));
        summarise(summary);
        return Ui.section("Sensor links", table, summary);
    }

    private void summarise(Label label) {
        var links = state.links.get();
        long connected = links.stream().filter(LinkSummary::connected).count();
        label.setText(links.isEmpty()
                ? "Each sensor's link to the hub is evaluated on its own. Green lines on the plan are links the hub can decode."
                : String.format(Locale.US, "%d of %d sensors reach the hub above the receiver sensitivity.",
                        connected, links.size()));
    }

    private VBox hubSection() {
        Label result = new Label();
        result.setWrapText(true);
        result.textProperty().bind(state.optimisation);
        result.visibleProperty().bind(state.optimisation.isNotEmpty());
        result.managedProperty().bind(result.visibleProperty());
        result.getStyleClass().add("result-box");

        var button = Ui.primaryButton("Find the best hub position", e -> simulation.optimiseHub());
        button.setGraphic(Icons.of(Icons.OPTIMISE));
        button.disableProperty().bind(simulation.optimisingProperty());
        return Ui.section("Hub placement",
                Ui.hint("Tries every position of the building and keeps the one where the weakest sensor link has "
                        + "the largest margin (max-min), evaluated without random fading."),
                button, result);
    }

    private VBox reportSection() {
        GridPane buttons = Ui.buttonGrid();
        var report = Ui.secondaryButton("Export report", e -> projects.exportReport());
        report.setGraphic(Icons.of(Icons.REPORT));
        var image = Ui.secondaryButton("Export image", e -> projects.exportImage());
        image.setGraphic(Icons.of(Icons.IMAGE));
        var summary = Ui.secondaryButton("Open simulation summary", e -> simulation.openSummary());
        summary.setGraphic(Icons.of(Icons.SUMMARY));
        buttons.add(report, 0, 0);
        buttons.add(image, 1, 0);
        buttons.add(summary, 0, 1, 2, 1);
        return Ui.section("Reports", buttons);
    }

    private static TableColumn<LinkSummary, String> textColumn(String title, double width,
                                                               Function<LinkSummary, String> value) {
        TableColumn<LinkSummary, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static Label kpiValue() {
        Label value = new Label("–");
        value.getStyleClass().add("kpi-value");
        return value;
    }

    private static VBox kpi(String title, Label value) {
        Label caption = new Label(title);
        caption.getStyleClass().add("kpi-caption");
        VBox tile = new VBox(4, caption, value);
        tile.getStyleClass().add("kpi");
        return tile;
    }
}
