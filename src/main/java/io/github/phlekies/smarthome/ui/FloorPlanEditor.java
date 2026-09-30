package io.github.phlekies.smarthome.ui;

import static io.github.phlekies.smarthome.ui.PlanCoordinates.HEIGHT_METERS;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.PIXELS_PER_METER;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.WIDTH_METERS;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toMetersX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toMetersY;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelY;

import java.util.List;
import java.util.Locale;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Material;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.physics.Geometry;
import io.github.phlekies.smarthome.util.Format;

/**
 * Mouse interaction on the plan: drawing walls and rooms, placing devices, selecting and
 * erasing walls, plus the live preview drawn on the plan overlay.
 */
final class FloorPlanEditor {

    private static final double WALL_PICK_TOLERANCE_PX = 6.0;
    private static final Color DRAFT = Color.web("#1f6feb");
    private static final Color SELECTED = Color.web("#f59e0b");
    private static final Color ERASE = Color.web("#dc2626");

    private final SimulatorModel model;
    private final UiState state;
    private final PlanView view;
    private final CellProbe probe;

    /** First click of a two-click wall or room, in metres (NaN when idle). */
    private double draftStartX = Double.NaN;
    private double draftStartY = Double.NaN;
    /** Current pointer position, in metres (NaN when outside the plan). */
    private double hoverX = Double.NaN;
    private double hoverY = Double.NaN;

    FloorPlanEditor(SimulatorModel model, UiState state, PlanView view, CellProbe probe) {
        this.model = model;
        this.state = state;
        this.view = view;
        this.probe = probe;

        view.setOnMouseMoved(this::onMouseMoved);
        view.setOnMouseExited(e -> {
            clearHover();
            probe.hide();
            state.pointer.set("");
        });
        view.setOnMouseClicked(this::onMouseClicked);

        state.tool.addListener((obs, old, tool) -> {
            cancelDraft();
            state.status.set(tool.help());
        });
        state.selectedWall.addListener((obs, old, wall) -> refreshOverlay());
        state.snapToGrid.addListener((obs, old, snap) -> refreshOverlay());
        model.addListener(change -> {
            if (change == SimulatorModel.Change.WALLS) {
                Wall selected = state.selectedWall.get();
                if (selected != null && model.walls().stream().noneMatch(wall -> wall == selected)) {
                    state.selectedWall.set(null);
                }
                refreshOverlay();
            }
        });
    }

    void cancelDraft() {
        draftStartX = Double.NaN;
        draftStartY = Double.NaN;
        refreshOverlay();
    }

    private boolean hasDraft() {
        return !Double.isNaN(draftStartX);
    }

    private void clearHover() {
        hoverX = Double.NaN;
        hoverY = Double.NaN;
        refreshOverlay();
    }

    // ---------------------------------------------------------------------------------------
    // Mouse handling
    // ---------------------------------------------------------------------------------------

    private void onMouseMoved(MouseEvent e) {
        if (!PlanCoordinates.isInsidePlan(e.getX(), e.getY())) {
            clearHover();
            probe.hide();
            state.pointer.set("");
            return;
        }
        state.pointer.set(String.format(Locale.US, "x %.1f m   y %.1f m", toMetersX(e.getX()), toMetersY(e.getY())));
        hoverX = snap(toMetersX(e.getX()), WIDTH_METERS);
        hoverY = snap(toMetersY(e.getY()), HEIGHT_METERS);
        refreshOverlay();
        probe.show(toMetersX(e.getX()), toMetersY(e.getY()), findWallNear(e.getX(), e.getY()));
    }

    private void onMouseClicked(MouseEvent e) {
        if (!PlanCoordinates.isInsidePlan(e.getX(), e.getY())) {
            return;
        }
        double x = snap(toMetersX(e.getX()), WIDTH_METERS);
        double y = snap(toMetersY(e.getY()), HEIGHT_METERS);

        if (e.getButton() == MouseButton.SECONDARY) {
            Wall wall = findWallNear(e.getX(), e.getY());
            if (wall != null) {
                state.selectedWall.set(wall);
                wallContextMenu(wall).show(view, e.getScreenX(), e.getScreenY());
            }
            return;
        }
        if (e.getButton() == MouseButton.PRIMARY) {
            onPrimaryClick(x, y, e.getX(), e.getY());
        }
    }

    private void onPrimaryClick(double x, double y, double pixelX, double pixelY) {
        switch (state.tool.get()) {
            case SELECT -> {
                Wall wall = findWallNear(pixelX, pixelY);
                state.selectedWall.set(wall);
                state.status.set(wall == null
                        ? "No wall there. Click closer to a wall segment."
                        : "Wall selected. Edit it in the panel or right-click it.");
            }
            case WALL -> {
                if (!hasDraft()) {
                    startDraft(x, y, "Start point set at %s. Click the end point of the wall.");
                } else if (Math.hypot(x - draftStartX, y - draftStartY) < 1e-6) {
                    state.status.set("The end point must be different from the start point.");
                } else {
                    model.addWalls(List.of(new Wall(draftStartX, draftStartY, x, y,
                            state.drawMaterial.get(), state.drawThicknessCm.get())));
                    cancelDraft();
                    state.status.set("Wall added. Keep clicking to draw more.");
                }
            }
            case ROOM -> {
                if (!hasDraft()) {
                    startDraft(x, y, "First corner set at %s. Click the opposite corner.");
                } else {
                    boolean added = model.addRoom(draftStartX, draftStartY, x, y,
                            state.drawMaterial.get(), state.drawThicknessCm.get());
                    cancelDraft();
                    state.status.set(added ? "Room added." : "A room needs a non-zero width and height.");
                }
            }
            case SENSOR -> {
                Sensor sensor = model.addSensor((int) Math.round(x), (int) Math.round(y));
                state.selectedDevice.set(sensor);
                state.status.set(String.format("%s placed at (%d, %d). Click again to add another one.",
                        sensor.getName(), sensor.getX(), sensor.getY()));
            }
            case HUB -> {
                var hub = model.placeHub((int) Math.round(x), (int) Math.round(y));
                state.selectedDevice.set(hub);
                state.status.set(String.format("Hub placed at (%d, %d).", hub.getX(), hub.getY()));
            }
            case DELETE -> {
                Wall wall = findWallNear(pixelX, pixelY);
                if (wall == null) {
                    state.status.set("No wall there to erase.");
                } else {
                    model.removeWall(wall);
                    state.status.set("Wall erased. Use Undo to bring it back.");
                }
            }
        }
    }

    private void startDraft(double x, double y, String message) {
        draftStartX = x;
        draftStartY = y;
        state.selectedWall.set(null);
        state.status.set(String.format(message, Format.point(x, y)));
        refreshOverlay();
    }

    private double snap(double meters, double max) {
        double clamped = Math.max(0.0, Math.min(max, meters));
        return state.snapToGrid.get() ? Math.rint(clamped) : clamped;
    }

    /** The wall closest to a pixel position, within a few pixels, or null. */
    Wall findWallNear(double pixelX, double pixelY) {
        Wall closest = null;
        double best = WALL_PICK_TOLERANCE_PX;
        for (Wall wall : model.walls()) {
            double distance = Geometry.distancePointToSegment(pixelX, pixelY,
                    toPixelX(wall.getX1()), toPixelY(wall.getY1()), toPixelX(wall.getX2()), toPixelY(wall.getY2()));
            if (distance <= best) {
                best = distance;
                closest = wall;
            }
        }
        return closest;
    }

    private ContextMenu wallContextMenu(Wall wall) {
        Menu materialMenu = new Menu("Material");
        for (Material material : Materials.all()) {
            MenuItem item = new MenuItem(material.getName());
            item.setOnAction(e -> {
                model.updateWall(wall, material, wall.getThicknessCm());
                state.status.set("Wall material changed to " + material.getName() + ".");
            });
            materialMenu.getItems().add(item);
        }

        Menu thicknessMenu = new Menu("Thickness");
        for (double thickness : new double[] { 4.0, 8.0, 12.0, 20.0 }) {
            MenuItem item = new MenuItem(String.format(Locale.US, "%.0f cm", thickness));
            item.setOnAction(e -> {
                model.updateWall(wall, wall.getMaterial(), thickness);
                state.status.set(String.format(Locale.US, "Wall thickness changed to %.0f cm.", thickness));
            });
            thicknessMenu.getItems().add(item);
        }

        MenuItem delete = new MenuItem("Delete wall");
        delete.setOnAction(e -> {
            model.removeWall(wall);
            state.status.set("Wall erased. Use Undo to bring it back.");
        });

        return new ContextMenu(materialMenu, thicknessMenu, new SeparatorMenuItem(), delete);
    }

    // ---------------------------------------------------------------------------------------
    // Overlay
    // ---------------------------------------------------------------------------------------

    private void refreshOverlay() {
        ObservableList<Node> overlay = view.overlay().getChildren();
        overlay.clear();

        Wall selected = state.selectedWall.get();
        if (selected != null) {
            overlay.add(highlight(selected, SELECTED, 0.75));
        }

        boolean hovering = !Double.isNaN(hoverX);
        EditorTool tool = state.tool.get();

        if (tool == EditorTool.DELETE && hovering) {
            Wall target = findWallNear(toPixelX(hoverX), toPixelY(hoverY));
            if (target != null && target != selected) {
                overlay.add(highlight(target, ERASE, 0.6));
            }
        }

        if (tool == EditorTool.SENSOR && hovering) {
            Circle ghost = anchor(hoverX, hoverY, Color.web("#1e88e5"));
            ghost.setRadius(6.5);
            ghost.setOpacity(0.6);
            overlay.add(ghost);
        } else if (tool == EditorTool.HUB && hovering) {
            Rectangle ghost = new Rectangle(toPixelX(hoverX) - 8, toPixelY(hoverY) - 8, 16, 16);
            ghost.setFill(Color.web("#dc2626", 0.2));
            ghost.setStroke(ERASE);
            ghost.setStrokeWidth(2.5);
            overlay.add(ghost);
        }

        if (!hasDraft() || !hovering) {
            return;
        }
        if (tool == EditorTool.WALL) {
            Line preview = new Line(toPixelX(draftStartX), toPixelY(draftStartY), toPixelX(hoverX), toPixelY(hoverY));
            preview.setStroke(DRAFT);
            preview.setStrokeWidth(4.0);
            preview.setOpacity(0.75);
            preview.getStrokeDashArray().addAll(10.0, 6.0);
            overlay.addAll(preview, anchor(draftStartX, draftStartY, DRAFT), anchor(hoverX, hoverY, DRAFT));
        } else if (tool == EditorTool.ROOM) {
            double minX = Math.min(draftStartX, hoverX);
            double maxX = Math.max(draftStartX, hoverX);
            double minY = Math.min(draftStartY, hoverY);
            double maxY = Math.max(draftStartY, hoverY);
            Rectangle preview = new Rectangle(toPixelX(minX), toPixelY(maxY),
                    (maxX - minX) * PIXELS_PER_METER, (maxY - minY) * PIXELS_PER_METER);
            preview.setFill(Color.web("#1f6feb", 0.12));
            preview.setStroke(DRAFT);
            preview.setStrokeWidth(3.0);
            preview.getStrokeDashArray().addAll(10.0, 6.0);
            overlay.addAll(preview, anchor(minX, minY, DRAFT), anchor(maxX, maxY, DRAFT));
        }
    }

    private static Line highlight(Wall wall, Color color, double opacity) {
        Line line = new Line(toPixelX(wall.getX1()), toPixelY(wall.getY1()), toPixelX(wall.getX2()), toPixelY(wall.getY2()));
        line.setStroke(color);
        line.setStrokeWidth(8.0);
        line.setOpacity(opacity);
        return line;
    }

    private static Circle anchor(double x, double y, Color color) {
        Circle anchor = new Circle(toPixelX(x), toPixelY(y), 4.5, color);
        anchor.setStroke(Color.WHITE);
        anchor.setStrokeWidth(1.5);
        return anchor;
    }
}
