package io.github.phlekies.smarthome.ui;

import static io.github.phlekies.smarthome.ui.PlanCoordinates.HEIGHT_METERS;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.MARGIN_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.PLAN_HEIGHT_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.PLAN_WIDTH_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.VIEW_HEIGHT_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.VIEW_WIDTH_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.WIDTH_METERS;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelY;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.AntennaType;
import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;

/**
 * Top-down view of the floor plan. Layers, bottom to top: grid, heatmap, walls, rays,
 * transient effects (waves, flashes), editor overlay and devices.
 */
final class PlanView extends Pane {

    private static final Color PLAN_BACKGROUND = Color.WHITE;
    private static final Color GRID_LINE = Color.web("#e6ebf0");
    private static final Color GRID_LINE_MAJOR = Color.web("#d2dae2");
    private static final Color AXIS_LABEL = Color.web("#8795a1");
    private static final Color SENSOR_FILL = Color.web("#1e88e5");
    private static final Color HUB_FILL = Color.web("#dc2626");
    private static final Color SELECTION = Color.web("#f59e0b");

    private final SimulatorModel model;
    private final UiState state;

    private final Canvas gridCanvas = new Canvas(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
    private final ImageView heatmapView = new ImageView();
    private final Canvas wallsCanvas = new Canvas(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
    private final Canvas raysCanvas = new Canvas(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
    private final Pane effectsLayer = new Pane();
    private final Group overlay = new Group();
    private final Group devicesLayer = new Group();

    private final Tooltip hubTooltip = new Tooltip();
    private Node hubNode;

    PlanView(SimulatorModel model, UiState state) {
        this.model = model;
        this.state = state;

        getStyleClass().add("plan-area");
        setMinSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
        setPrefSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
        setMaxSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);

        heatmapView.setLayoutX(MARGIN_PX);
        heatmapView.setLayoutY(MARGIN_PX);
        heatmapView.setFitWidth(PLAN_WIDTH_PX);
        heatmapView.setFitHeight(PLAN_HEIGHT_PX);
        heatmapView.setSmooth(false);
        heatmapView.setOpacity(0.75);
        heatmapView.setVisible(false);

        effectsLayer.setClip(new Rectangle(MARGIN_PX, MARGIN_PX, PLAN_WIDTH_PX, PLAN_HEIGHT_PX));
        effectsLayer.setPrefSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);

        for (Node passive : new Node[] { gridCanvas, heatmapView, wallsCanvas, raysCanvas, effectsLayer, overlay }) {
            passive.setMouseTransparent(true);
        }
        getChildren().addAll(gridCanvas, heatmapView, wallsCanvas, raysCanvas, effectsLayer, overlay, devicesLayer);

        drawGrid();
        redrawWalls();
        redrawDevices();
        state.selectedDevice.addListener((obs, old, selected) -> redrawDevices());
    }

    Group overlay() {
        return overlay;
    }

    Pane effectsLayer() {
        return effectsLayer;
    }

    Canvas raysCanvas() {
        return raysCanvas;
    }

    void showHeatmap(Image image) {
        heatmapView.setImage(image);
        heatmapView.setVisible(image != null);
    }

    void hideHeatmap() {
        heatmapView.setVisible(false);
    }

    void clearRays() {
        raysCanvas.getGraphicsContext2D().clearRect(0, 0, VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
    }

    // ---------------------------------------------------------------------------------------
    // Static layers
    // ---------------------------------------------------------------------------------------

    private void drawGrid() {
        GraphicsContext g = gridCanvas.getGraphicsContext2D();
        g.setFill(PLAN_BACKGROUND);
        g.fillRect(MARGIN_PX, MARGIN_PX, PLAN_WIDTH_PX, PLAN_HEIGHT_PX);

        g.setLineWidth(1.0);
        for (int x = 0; x <= WIDTH_METERS; x++) {
            g.setStroke(x % 5 == 0 ? GRID_LINE_MAJOR : GRID_LINE);
            double px = Math.round(toPixelX(x)) + 0.5;
            g.strokeLine(px, toPixelY(0), px, toPixelY(HEIGHT_METERS));
        }
        for (int y = 0; y <= HEIGHT_METERS; y++) {
            g.setStroke(y % 5 == 0 ? GRID_LINE_MAJOR : GRID_LINE);
            double py = Math.round(toPixelY(y)) + 0.5;
            g.strokeLine(toPixelX(0), py, toPixelX(WIDTH_METERS), py);
        }

        g.setFill(AXIS_LABEL);
        g.setFont(Font.font(11));
        g.setTextBaseline(VPos.TOP);
        g.setTextAlign(TextAlignment.CENTER);
        for (int x = 0; x <= WIDTH_METERS; x += 5) {
            g.fillText(Integer.toString(x), toPixelX(x), toPixelY(0) + 6);
        }
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.RIGHT);
        for (int y = 0; y <= HEIGHT_METERS; y += 5) {
            g.fillText(Integer.toString(y), toPixelX(0) - 6, toPixelY(y));
        }
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        g.fillText("x (m)", toPixelX(WIDTH_METERS) - 30, toPixelY(0) + 20);
        g.setTextBaseline(VPos.BOTTOM);
        g.fillText("y (m)", toPixelX(0) - 30, toPixelY(HEIGHT_METERS) - 8);
    }

    void redrawWalls() {
        GraphicsContext g = wallsCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
        g.setLineCap(StrokeLineCap.ROUND);
        for (Wall wall : model.walls()) {
            g.setStroke(MaterialPalette.colorOf(wall.getMaterial()));
            g.setLineWidth(MaterialPalette.strokeWidth(wall.getThicknessCm()));
            g.strokeLine(toPixelX(wall.getX1()), toPixelY(wall.getY1()), toPixelX(wall.getX2()), toPixelY(wall.getY2()));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Devices
    // ---------------------------------------------------------------------------------------

    void redrawDevices() {
        devicesLayer.getChildren().clear();
        hubNode = null;
        Device selected = state.selectedDevice.get();

        model.hub().ifPresent(hub -> {
            double cx = toPixelX(hub.getX());
            double cy = toPixelY(hub.getY());
            Rectangle box = new Rectangle(cx - 7, cy - 7, 14, 14);
            box.setArcWidth(4);
            box.setArcHeight(4);
            box.setFill(HUB_FILL);
            box.setStroke(hub == selected ? SELECTION : HUB_FILL.darker());
            box.setStrokeWidth(hub == selected ? 3 : 1.5);
            Label tag = deviceLabel(hub.getName(), cx, cy, HUB_FILL.darker());
            Group node = new Group(box, tag);
            node.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    selectDevice(hub);
                }
                e.consume();
            });
            node.setOnMousePressed(MouseEvent::consume);
            Tooltip.install(box, new Tooltip(String.format("%s at (%d, %d)%nRx gain %.1f dB, polarization %.0f°",
                    hub.getName(), hub.getX(), hub.getY(), hub.getReceiverGainDb(), hub.getPolarizationDeg())));
            hubNode = box;
            devicesLayer.getChildren().add(node);
        });

        for (Sensor sensor : model.sensors()) {
            double cx = toPixelX(sensor.getX());
            double cy = toPixelY(sensor.getY());
            Circle dot = new Circle(cx, cy, 6.5, SENSOR_FILL);
            dot.setStroke(sensor == selected ? SELECTION : SENSOR_FILL.darker());
            dot.setStrokeWidth(sensor == selected ? 3 : 1.5);
            Label tag = deviceLabel(sensor.getName(), cx, cy, SENSOR_FILL.darker());
            Group node = new Group(dot, tag);

            ContextMenu antennaMenu = antennaMenu(sensor);
            node.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.SECONDARY) {
                    antennaMenu.show(dot, e.getScreenX(), e.getScreenY());
                } else {
                    antennaMenu.hide();
                    selectDevice(sensor);
                }
                e.consume();
            });
            node.setOnMouseClicked(MouseEvent::consume);
            Tooltip.install(dot, new Tooltip(sensor.describe()));
            devicesLayer.getChildren().add(node);
        }
    }

    private void selectDevice(Device device) {
        state.selectedDevice.set(device);
        state.status.set(String.format("%s selected at (%d, %d).", device.getName(), device.getX(), device.getY()));
    }

    private ContextMenu antennaMenu(Sensor sensor) {
        ToggleGroup group = new ToggleGroup();
        ContextMenu menu = new ContextMenu();
        for (AntennaType type : AntennaType.values()) {
            RadioMenuItem item = new RadioMenuItem(type + " antenna");
            item.setToggleGroup(group);
            item.setSelected(sensor.getAntennaType() == type);
            item.setOnAction(e -> {
                sensor.setAntennaType(type);
                model.devicesChanged();
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    private static Label deviceLabel(String text, double cx, double cy, Color color) {
        Label label = new Label(text);
        label.setFont(Font.font(null, javafx.scene.text.FontWeight.BOLD, 12));
        label.setTextFill(color);
        label.setLayoutX(cx + 10);
        label.setLayoutY(cy - 20);
        label.setMouseTransparent(true);
        return label;
    }

    // ---------------------------------------------------------------------------------------
    // Hub feedback
    // ---------------------------------------------------------------------------------------

    /** Shows a message anchored at the hub for a few seconds. */
    void showHubMessage(String text) {
        if (hubNode == null || hubNode.getScene() == null) {
            return;
        }
        Bounds bounds = hubNode.localToScreen(hubNode.getBoundsInLocal());
        if (bounds == null) {
            return;
        }
        hubTooltip.setText(text);
        hubTooltip.show(hubNode, bounds.getMaxX() + 8, bounds.getMinY() - 8);
        PauseTransition hide = new PauseTransition(Duration.seconds(4));
        hide.setOnFinished(e -> hubTooltip.hide());
        hide.play();
    }

    /** Expanding ring at the hub, used when a ray or wave arrives. */
    void flashHub() {
        model.hub().ifPresent(this::flashAt);
    }

    private void flashAt(Hub hub) {
        Circle flash = new Circle(toPixelX(hub.getX()), toPixelY(hub.getY()), 6, Color.TRANSPARENT);
        flash.setStroke(HUB_FILL);
        flash.setStrokeWidth(3);
        effectsLayer.getChildren().add(flash);
        Timeline animation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(flash.radiusProperty(), 6), new KeyValue(flash.opacityProperty(), 0.9)),
                new KeyFrame(Duration.millis(450),
                        new KeyValue(flash.radiusProperty(), 26), new KeyValue(flash.opacityProperty(), 0.0)));
        animation.setOnFinished(e -> effectsLayer.getChildren().remove(flash));
        animation.play();
    }
}
