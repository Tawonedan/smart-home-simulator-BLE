package io.github.phlekies.smarthome.ui;

import static io.github.phlekies.smarthome.ui.PlanCoordinates.HEIGHT_METERS;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.MARGIN_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.PLAN_HEIGHT_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.PLAN_WIDTH_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.VIEW_HEIGHT_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.VIEW_WIDTH_PX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.WIDTH_METERS;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toMetersX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toMetersY;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelY;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
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
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import io.github.phlekies.smarthome.app.LinkSummary;
import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.AntennaType;
import io.github.phlekies.smarthome.model.Device;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Scanner;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;

/**
 * Top-down view of the floor plan. Layers, bottom to top: grid, heatmap, walls, sensor-hub
 * links, rays, transient effects (waves, flashes), editor overlay and devices. Devices can be
 * dragged; the rest of the interaction is handled by {@link FloorPlanEditor}.
 */
final class PlanView extends Pane {

    private final SimulatorModel model;
    private final UiState state;

    private final Canvas gridCanvas = new Canvas(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
    private final ImageView heatmapView = new ImageView();
    private final Canvas wallsCanvas = new Canvas(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
    private final Canvas linksCanvas = new Canvas(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
    private final Canvas raysCanvas = new Canvas(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
    private final Pane effectsLayer = new Pane();
    private final Group overlay = new Group();
    private final Group devicesLayer = new Group();

    private final Tooltip hubTooltip = new Tooltip();
    private Node hubNode;
    private PlanPalette palette = PlanPalette.DARK;

    /** Device being dragged; while set, device nodes are moved instead of rebuilt. */
    private Device dragged;
    private Node draggedNode;

    PlanView(SimulatorModel model, UiState state) {
        this.model = model;
        this.state = state;

        getStyleClass().add("plan-view");
        setMinSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
        setPrefSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
        setMaxSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);

        heatmapView.setLayoutX(MARGIN_PX);
        heatmapView.setLayoutY(MARGIN_PX);
        heatmapView.setFitWidth(PLAN_WIDTH_PX);
        heatmapView.setFitHeight(PLAN_HEIGHT_PX);
        heatmapView.setSmooth(true); // bilinear upscaling of the 1 px-per-metre image
        heatmapView.setOpacity(0.78);
        heatmapView.setVisible(false);

        effectsLayer.setClip(new Rectangle(MARGIN_PX, MARGIN_PX, PLAN_WIDTH_PX, PLAN_HEIGHT_PX));
        effectsLayer.setPrefSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);

        for (Node passive : new Node[] { gridCanvas, heatmapView, wallsCanvas, linksCanvas, raysCanvas, effectsLayer, overlay }) {
            passive.setMouseTransparent(true);
        }
        getChildren().addAll(gridCanvas, heatmapView, wallsCanvas, linksCanvas, raysCanvas, effectsLayer, overlay,
                devicesLayer);

        state.selectedDevice.addListener((obs, old, selected) -> redrawDevices());
        state.links.addListener((obs, old, links) -> redrawLinks());
        state.linksVisible.addListener((obs, old, visible) -> redrawLinks());
        state.darkTheme.addListener((obs, old, dark) -> applyPalette(PlanPalette.of(dark)));
        applyPalette(PlanPalette.of(state.darkTheme.get()));
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

    PlanPalette palette() {
        return palette;
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

    private void applyPalette(PlanPalette newPalette) {
        palette = newPalette;
        drawGrid();
        redrawWalls();
        redrawLinks();
        redrawDevices();
    }

    // ---------------------------------------------------------------------------------------
    // Static layers
    // ---------------------------------------------------------------------------------------

    private void drawGrid() {
        GraphicsContext g = gridCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
        g.setFill(palette.background());
        g.fillRect(MARGIN_PX, MARGIN_PX, PLAN_WIDTH_PX, PLAN_HEIGHT_PX);

        g.setLineWidth(1.0);
        for (int x = 0; x <= WIDTH_METERS; x++) {
            g.setStroke(x % 5 == 0 ? palette.gridLineMajor() : palette.gridLine());
            double px = Math.round(toPixelX(x)) + 0.5;
            g.strokeLine(px, toPixelY(0), px, toPixelY(HEIGHT_METERS));
        }
        for (int y = 0; y <= HEIGHT_METERS; y++) {
            g.setStroke(y % 5 == 0 ? palette.gridLineMajor() : palette.gridLine());
            double py = Math.round(toPixelY(y)) + 0.5;
            g.strokeLine(toPixelX(0), py, toPixelX(WIDTH_METERS), py);
        }

        g.setFill(palette.axisLabel());
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
            g.setStroke(palette.materialColor(wall.getMaterial()));
            g.setLineWidth(PlanPalette.wallStroke(wall.getThicknessCm()));
            g.strokeLine(toPixelX(wall.getX1()), toPixelY(wall.getY1()), toPixelX(wall.getX2()), toPixelY(wall.getY2()));
        }
    }

    /** Dashed line from every sensor to the hub: green if the hub can decode it, red otherwise. */
    private void redrawLinks() {
        GraphicsContext g = linksCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
        Hub hub = model.hub().orElse(null);
        List<LinkSummary> links = state.links.get();
        if (!state.linksVisible.get() || hub == null) {
            return;
        }
        g.setLineWidth(1.6);
        g.setLineDashes(6, 5);
        for (LinkSummary link : links) {
            Color color = link.connected() ? palette.linkOk() : palette.linkBad();
            g.setStroke(color.deriveColor(0, 1, 1, 0.85));
            g.strokeLine(toPixelX(link.sensor().getX()), toPixelY(link.sensor().getY()),
                    toPixelX(hub.getX()), toPixelY(hub.getY()));
        }
        g.setLineDashes();
    }

    // ---------------------------------------------------------------------------------------
    // Devices
    // ---------------------------------------------------------------------------------------

    void redrawDevices() {
        if (dragged != null) {
            // Rebuilding the nodes would cancel the drag gesture; just follow the device.
            draggedNode.setTranslateX(toPixelX(dragged.getX()) - (double) draggedNode.getProperties().get("originX"));
            draggedNode.setTranslateY(toPixelY(dragged.getY()) - (double) draggedNode.getProperties().get("originY"));
            redrawLinks();
            return;
        }
        devicesLayer.getChildren().clear();
        hubNode = null;
        Device selected = state.selectedDevice.get();

        model.hub().ifPresent(hub -> {
            double cx = toPixelX(hub.getX());
            double cy = toPixelY(hub.getY());
            Rectangle box = new Rectangle(cx - 8, cy - 8, 16, 16);
            box.setArcWidth(5);
            box.setArcHeight(5);
            box.setFill(palette.hub());
            box.setStroke(hub == selected ? palette.selection() : palette.hub().darker());
            box.setStrokeWidth(hub == selected ? 3 : 1.5);
            Circle core = new Circle(cx, cy, 3, Color.WHITE);
            String hubInfo = hub instanceof Scanner scanner
                    ? String.format("%s [BLE Scanner] at (%d, %d)%nRx gain %.1f dB, Sens %.0f dBm, Thresh %.0f dBm%nDrag to move",
                            scanner.getName(), scanner.getX(), scanner.getY(), scanner.getReceiverGainDb(),
                            scanner.getRxSensitivityDbm(), scanner.getRssiThresholdDbm())
                    : String.format("%s at (%d, %d)%nRx gain %.1f dB, polarization %.0f°%nDrag to move",
                            hub.getName(), hub.getX(), hub.getY(), hub.getReceiverGainDb(), hub.getPolarizationDeg());
            Group node = new Group(box, core, deviceLabel(hub.getName(), cx, cy));
            Tooltip.install(box, new Tooltip(hubInfo));
            makeInteractive(node, hub, null);
            hubNode = box;
            devicesLayer.getChildren().add(node);
        });

        for (Sensor sensor : model.sensors()) {
            double cx = toPixelX(sensor.getX());
            double cy = toPixelY(sensor.getY());
            Group node = new Group();
            if (sensor.getAntennaType() == AntennaType.DIRECTIONAL && sensor.getBeamwidthDeg() < 360) {
                Arc beam = new Arc(cx, cy, 30, 30, sensor.getOrientationDeg() - sensor.getBeamwidthDeg() / 2,
                        sensor.getBeamwidthDeg());
                beam.setType(ArcType.ROUND);
                beam.setFill(palette.sensor().deriveColor(0, 1, 1, 0.22));
                beam.setStroke(palette.sensor().deriveColor(0, 1, 1, 0.6));
                node.getChildren().add(beam);
            }
            Circle dot = new Circle(cx, cy, 7, palette.sensor());
            dot.setStroke(sensor == selected ? palette.selection() : palette.sensor().darker());
            dot.setStrokeWidth(sensor == selected ? 3 : 1.5);
            node.getChildren().addAll(dot, deviceLabel(sensor.getName(), cx, cy));
            Tooltip.install(dot, new Tooltip(sensor.describe() + "\nDrag to move, right-click to change the antenna"));
            makeInteractive(node, sensor, antennaMenu(sensor));
            devicesLayer.getChildren().add(node);
        }
    }

    /** Selection, dragging and (for sensors) the antenna context menu. */
    private void makeInteractive(Group node, Device device, ContextMenu contextMenu) {
        node.setCursor(Cursor.HAND);
        node.getProperties().put("originX", toPixelX(device.getX()));
        node.getProperties().put("originY", toPixelY(device.getY()));

        node.setOnMousePressed(e -> {
            e.consume();
            if (e.getButton() == MouseButton.SECONDARY && contextMenu != null) {
                contextMenu.show(node, e.getScreenX(), e.getScreenY());
                return;
            }
            if (contextMenu != null) {
                contextMenu.hide();
            }
            if (e.getButton() == MouseButton.PRIMARY) {
                // Mark the drag first: selecting redraws the devices, which must not rebuild this node.
                dragged = device;
                draggedNode = node;
                state.selectedDevice.set(device);
                state.status.set(String.format("%s selected at (%d, %d). Drag it to move it.",
                        device.getName(), device.getX(), device.getY()));
            }
        });
        node.setOnMouseDragged(e -> {
            e.consume();
            if (dragged != device) {
                return;
            }
            node.setCursor(Cursor.CLOSED_HAND);
            Point2D local = sceneToLocal(e.getSceneX(), e.getSceneY());
            int x = (int) Math.round(toMetersX(local.getX()));
            int y = (int) Math.round(toMetersY(local.getY()));
            if (model.moveDevice(device, x, y)) {
                state.status.set(String.format("Moving %s to (%d, %d)...", device.getName(), device.getX(), device.getY()));
            }
        });
        node.setOnMouseReleased(e -> {
            e.consume();
            if (dragged == device) {
                dragged = null;
                draggedNode = null;
                redrawDevices();
            }
        });
        node.setOnMouseClicked(MouseEvent::consume);
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
                if (type == AntennaType.DIRECTIONAL && sensor.getBeamwidthDeg() >= 360) {
                    sensor.setBeamwidthDeg(90);
                }
                model.devicesChanged();
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    private Label deviceLabel(String text, double cx, double cy) {
        Label label = new Label(text);
        label.setFont(Font.font(null, FontWeight.BOLD, 12));
        label.setTextFill(palette.deviceLabel());
        label.getStyleClass().add("device-label");
        label.setLayoutX(cx + 11);
        label.setLayoutY(cy - 22);
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

    /** Expanding ring at the hub, used when a ray or wave arrives or the hub is relocated. */
    void flashHub() {
        model.hub().ifPresent(this::flashAt);
    }

    private void flashAt(Hub hub) {
        Circle flash = new Circle(toPixelX(hub.getX()), toPixelY(hub.getY()), 6, Color.TRANSPARENT);
        flash.setStroke(palette.hub());
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
