package io.github.phlekies.smarthome.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.simulation.CellResult;
import io.github.phlekies.smarthome.simulation.LinkValidator;
import io.github.phlekies.smarthome.simulation.PathContribution;
import io.github.phlekies.smarthome.simulation.SimulationSettings;
import io.github.phlekies.smarthome.util.Format;

/** Read-only report of the scenario, the hub link, every sensor and the formula checks. */
final class SummaryWindow {

    private final SimulatorModel model;
    private final Environment env;
    private final SimulationSettings settings;

    SummaryWindow(SimulatorModel model) {
        this.model = model;
        this.env = model.environment();
        this.settings = model.settings();
    }

    void show(Window owner) {
        CellResult hubLink = model.hubLink().orElse(null);

        TabPane tabs = new TabPane(
                tab("Overview", overviewPage(hubLink)),
                tab("Hub", hubPage(hubLink)),
                tab("Sensors", sensorsPage()),
                tab("Validation", validationPage(hubLink)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Scene scene = new Scene(tabs, 920, 740);
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Simulation summary");
        stage.setScene(scene);
        stage.show();
    }

    // ---------------------------------------------------------------------------------------
    // Pages
    // ---------------------------------------------------------------------------------------

    private VBox overviewPage(CellResult hubLink) {
        VBox page = page("Simulation summary",
                "Scenario, propagation model and headline indicators.");

        GridPane scenario = grid();
        row(scenario, "Scenario", model.scenarioName());
        row(scenario, "Description", model.scenarioDescription());
        row(scenario, "Walls", Integer.toString(model.walls().size()));
        row(scenario, "Materials", materialBreakdown());
        row(scenario, "Sensors", Integer.toString(model.sensors().size()));
        row(scenario, "Hub", model.hub().map(h -> h.getName() + " at (" + h.getX() + ", " + h.getY() + ")")
                .orElse("Not placed"));
        page.getChildren().add(card("Scenario", "Current indoor floor plan.", scenario));

        FlowPane tiles = new FlowPane(12, 12,
                metric("Noise floor", Format.dbm(env.noiseFloorDbm()), "#0f766e"),
                metric("Rx sensitivity", Format.dbm(settings.getReceiverSensitivityDbm()), "#7c3aed"),
                metric("Wavelength", Format.meters(env.wavelengthMeters()), "#1d4ed8"));
        if (hubLink != null) {
            tiles.getChildren().addAll(
                    metric("SINR at hub", Format.db(hubLink.sinrDb()), "#2563eb"),
                    metric("Capacity at hub", Format.mbps(hubLink.capacityMbps()), "#b45309"));
        }
        page.getChildren().add(card("Key indicators", "", tiles));

        GridPane radio = grid();
        row(radio, "Carrier frequency", Format.frequencyMHz(env.getFreqMHz()));
        row(radio, "Wavelength", Format.meters(env.wavelengthMeters()));
        row(radio, "Bandwidth", Format.hertz(env.getBandwidthHz()));
        row(radio, "Noise figure", Format.db(env.getNoiseFigureDb()));
        row(radio, "Noise floor", Format.dbm(env.noiseFloorDbm()));
        row(radio, "Extra attenuation", Format.db(env.getAlphaDbPerMeter()) + "/m");
        row(radio, "System gain", Format.db(env.getSystemGainDb()));
        row(radio, "Rx sensitivity", Format.dbm(settings.getReceiverSensitivityDbm()));
        page.getChildren().add(card("Radio and noise", "Physical parameters behind every metric.", radio));

        page.getChildren().add(card("How the numbers are computed", "",
                text("Noise = −174 dBm/Hz + 10·log10(B) + NF.  SNR = S / N.  SINR = S / (N + I).  "
                        + "Capacity = B·log2(1 + SINR).  Link margin = S − receiver sensitivity.  "
                        + "S is the strongest sensor at the receiver; the other sensors add up as interference I.")));

        GridPane engine = grid();
        row(engine, "Propagation", settings.getPropagationMode().toString());
        row(engine, "Heatmap metric", settings.getMapMetric().toString());
        row(engine, "Fading", settings.getFadingModel().toString());
        row(engine, "Path-loss exponent", Format.number(settings.getLogDistanceExponent(), 2));
        row(engine, "Rician K-factor", Format.db(settings.getRicianKFactorDb()));
        row(engine, "Diffraction", settings.isDiffractionEnabled() ? "Enabled" : "Disabled");
        row(engine, "Diffuse scattering", settings.isScatteringEnabled() ? "Enabled" : "Disabled");
        row(engine, "Parallel computation", settings.isParallelComputation() ? "Yes" : "No");
        row(engine, "Path culling threshold", Format.dbm(settings.getCullingThresholdDbm()));
        row(engine, "Max. reflections", Integer.toString(settings.getMaxReflectionPaths()));
        row(engine, "Max. diffractions", Integer.toString(settings.getMaxDiffractionPaths()));
        row(engine, "Max. scattering paths", Integer.toString(settings.getMaxScatteringPaths()));
        page.getChildren().add(card("Propagation engine", "Settings used for the heatmap and the links.", engine));
        return page;
    }

    private VBox hubPage(CellResult hubLink) {
        VBox page = page("Hub link",
                "Combined signal at the receiver. With several sensors, the strongest one is the useful "
                        + "signal and the rest count as interference.");
        Hub hub = model.hub().orElse(null);
        if (hub == null) {
            page.getChildren().add(card("No hub", "Place a hub on the plan to analyse the combined link.",
                    text("Only the scenario and sensor summaries are available.")));
            return page;
        }

        GridPane receiver = grid();
        row(receiver, "Id", hub.getId());
        row(receiver, "Position", "(" + hub.getX() + ", " + hub.getY() + ")");
        row(receiver, "Rx gain", Format.db(hub.getReceiverGainDb()));
        row(receiver, "Polarization", Format.degrees(hub.getPolarizationDeg()));
        row(receiver, "Sensitivity", Format.dbm(settings.getReceiverSensitivityDbm()));
        page.getChildren().add(card("Receiver", "", receiver));

        if (model.sensors().isEmpty()) {
            page.getChildren().add(card("No link data", "There are no sensors transmitting.",
                    text("Add at least one sensor to see power, SINR and capacity at the hub.")));
            return page;
        }

        page.getChildren().add(card("At a glance", "", new FlowPane(12, 12,
                metric("Total power", Format.dbm(hubLink.totalPowerDbm()), "#1d4ed8"),
                metric("SINR", Format.db(hubLink.sinrDb()), "#0f766e"),
                metric("BER", Format.ber(hubLink.ber()), "#7c3aed"),
                metric("Capacity", Format.mbps(hubLink.capacityMbps()), "#b45309"),
                metric("Link margin", Format.db(hubLink.linkMarginDb()), "#be123c"))));

        GridPane details = grid();
        row(details, "Dominant sensor", hubLink.dominantSensorId());
        row(details, "Total power", Format.dbm(hubLink.totalPowerDbm()));
        row(details, "Useful signal", Format.dbm(hubLink.signalPowerDbm()));
        row(details, "Interference", Format.dbm(hubLink.interferencePowerDbm()));
        row(details, "Noise", Format.dbm(hubLink.noiseDbm()));
        row(details, "SNR", Format.db(hubLink.snrDb()));
        row(details, "SINR", Format.db(hubLink.sinrDb()));
        row(details, "BER", Format.ber(hubLink.ber()));
        row(details, "Capacity", Format.mbps(hubLink.capacityMbps()));
        row(details, "Link margin", Format.db(hubLink.linkMarginDb()));
        row(details, "Field phase", Format.degrees(Math.toDegrees(hubLink.fieldPhaseRad())));
        row(details, "Paths considered", Integer.toString(hubLink.pathCount()));
        page.getChildren().add(card("Combined link details", "", details));

        page.getChildren().add(card("Strongest paths",
                "Sorted by received power: shows whether line of sight, a reflection or diffraction dominates.",
                pathList(hubLink.contributions(), 8)));
        return page;
    }

    private VBox sensorsPage() {
        VBox page = page("Sensors", "Antenna configuration of every sensor and, with a hub, its individual link.");
        if (model.sensors().isEmpty()) {
            page.getChildren().add(card("No sensors", "There are no transmitters on the plan.",
                    text("Add a sensor to see its transmit power, antenna pattern and link metrics.")));
            return page;
        }

        for (Sensor sensor : model.sensors()) {
            VBox content = new VBox(12);
            GridPane config = grid();
            row(config, "Id", sensor.getId());
            row(config, "Position", "(" + sensor.getX() + ", " + sensor.getY() + ")");
            row(config, "Antenna", sensor.getAntennaType().toString());
            row(config, "Orientation", Format.degrees(sensor.getOrientationDeg()));
            row(config, "Beamwidth", Format.degrees(sensor.getBeamwidthDeg()));
            row(config, "Tx power", Format.dbm(sensor.getTxPowerDbm()));
            row(config, "Tx gain", Format.db(sensor.getTxGainDb()));
            row(config, "Pattern sharpness", Format.number(sensor.getPatternSharpness(), 2));
            row(config, "Polarization", Format.degrees(sensor.getPolarizationDeg()));
            content.getChildren().add(card("Configuration", "", config));

            CellResult link = model.sensorLink(sensor).orElse(null);
            if (link == null) {
                content.getChildren().add(card("Link to hub", "No hub placed, so only the transmitter is shown.",
                        text("Place a hub to see received power, BER, capacity and link margin.")));
            } else {
                content.getChildren().add(card("Link to hub", "", new FlowPane(12, 12,
                        metric("Rx power", Format.dbm(link.totalPowerDbm()), "#1d4ed8"),
                        metric("SNR", Format.db(link.snrDb()), "#0f766e"),
                        metric("Link margin", Format.db(link.linkMarginDb()), "#be123c"),
                        metric("Capacity", Format.mbps(link.capacityMbps()), "#b45309"))));

                GridPane details = grid();
                row(details, "Distance to hub", Format.meters(sensor.distanceTo(model.hub().orElseThrow())));
                row(details, "Received power", Format.dbm(link.totalPowerDbm()));
                row(details, "Noise", Format.dbm(link.noiseDbm()));
                row(details, "SNR", Format.db(link.snrDb()));
                row(details, "BER", Format.ber(link.ber()));
                row(details, "Capacity", Format.mbps(link.capacityMbps()));
                row(details, "Link margin", Format.db(link.linkMarginDb()));
                row(details, "Paths", Integer.toString(link.pathCount()));
                content.getChildren().add(card("Link details", "", details));
                content.getChildren().add(card("Paths from this sensor", "", pathList(link.contributions(), 6)));
            }
            page.getChildren().add(card(sensor.getId() + " - " + sensor.getName(), "", content));
        }
        return page;
    }

    private VBox validationPage(CellResult hubLink) {
        VBox page = page("Numerical validation",
                "Each value shown by the simulator is recomputed from its closed-form formula "
                        + "(thermal noise, SNR, SINR, BER, Shannon capacity, link margin). All OK means the "
                        + "numbers are self-consistent.");

        page.getChildren().add(checksCard("Environment", LinkValidator.environmentChecks(env)));
        if (hubLink != null && !model.sensors().isEmpty()) {
            page.getChildren().add(checksCard("Combined hub link",
                    LinkValidator.cellChecks(hubLink, env, model.receiverSettings())));
            List<CellResult> links = model.sensors().stream().map(s -> model.sensorLink(s).orElseThrow()).toList();
            List<String> ids = model.sensors().stream().map(Sensor::getId).toList();
            page.getChildren().add(checksCard("Sensor mix at the hub", LinkValidator.aggregateChecks(hubLink, ids, links)));
            for (int i = 0; i < links.size(); i++) {
                Sensor sensor = model.sensors().get(i);
                page.getChildren().add(checksCard(sensor.getId() + " - " + sensor.getName(),
                        LinkValidator.cellChecks(links.get(i), env, model.receiverSettings())));
            }
        } else {
            page.getChildren().add(card("Links not validated", "",
                    text("Place a hub and at least one sensor to validate the link formulas.")));
        }
        return page;
    }

    // ---------------------------------------------------------------------------------------
    // Building blocks
    // ---------------------------------------------------------------------------------------

    private String materialBreakdown() {
        if (model.walls().isEmpty()) {
            return "No walls";
        }
        Map<String, Long> counts = model.walls().stream().collect(Collectors.groupingBy(
                (Wall wall) -> wall.getMaterial().getName(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", "));
    }

    private static Tab tab(String title, Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("summary-scroll");
        return new Tab(title, scroll);
    }

    private static VBox page(String title, String subtitle) {
        Label header = new Label(title);
        header.getStyleClass().add("summary-title");
        VBox page = new VBox(header, text(subtitle));
        page.getStyleClass().add("summary-page");
        return page;
    }

    private static VBox card(String title, String subtitle, Node content) {
        Label header = new Label(title);
        header.getStyleClass().add("card-title");
        VBox card = new VBox(header);
        if (!subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.setWrapText(true);
            sub.getStyleClass().add("card-subtitle");
            card.getChildren().add(sub);
        }
        card.getChildren().add(content);
        card.getStyleClass().add("card");
        return card;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("summary-grid");
        ColumnConstraints labels = new ColumnConstraints(190);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        return grid;
    }

    private static void row(GridPane grid, String label, String value) {
        Label name = new Label(label);
        name.getStyleClass().add("form-label");
        Label content = new Label(value);
        content.setWrapText(true);
        content.getStyleClass().add("summary-value");
        grid.addRow(grid.getRowCount(), name, content);
    }

    private static VBox metric(String label, String value, String accent) {
        Label name = new Label(label);
        name.getStyleClass().add("metric-label");
        Label number = new Label(value);
        number.getStyleClass().add("metric-value");
        number.setStyle("-fx-text-fill: " + accent + ";");
        VBox tile = new VBox(name, number);
        tile.getStyleClass().add("metric-tile");
        return tile;
    }

    private static Label text(String value) {
        Label label = new Label(value);
        label.setWrapText(true);
        label.getStyleClass().add("hint");
        return label;
    }

    private static VBox pathList(List<PathContribution> paths, int limit) {
        VBox list = new VBox(8);
        if (paths.isEmpty()) {
            list.getChildren().add(text("No paths reach this point."));
            return list;
        }
        for (int i = 0; i < Math.min(limit, paths.size()); i++) {
            PathContribution path = paths.get(i);
            list.getChildren().add(text(String.format("%d. %s | %s | %s | antenna gain %s | extra losses %s",
                    i + 1, path.type(), Format.dbm(path.powerDbm()), Format.meters(path.distanceMeters()),
                    Format.db(path.txGainDb()), Format.db(path.extraLossDb()))));
        }
        return list;
    }

    private static VBox checksCard(String title, List<LinkValidator.Check> checks) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("summary-grid");
        grid.addRow(0, header("Status"), header("Parameter"), header("Shown"), header("Expected"));
        for (LinkValidator.Check check : checks) {
            Label badge = new Label(check.ok() ? "OK" : "CHECK");
            badge.getStyleClass().addAll("badge", check.ok() ? "badge-ok" : "badge-fail");
            grid.addRow(grid.getRowCount(), badge, new Label(check.parameter()), new Label(check.actual()),
                    new Label(check.expected()));
        }
        long passed = checks.stream().filter(LinkValidator.Check::ok).count();
        return card(title, passed + " of " + checks.size() + " checks passed.", grid);
    }

    private static Label header(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("table-header");
        return label;
    }
}
