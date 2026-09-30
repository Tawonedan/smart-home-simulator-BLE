package io.github.phlekies.smarthome.ui;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;

/**
 * Scrollable, zoomable window onto the plan, with overlays (the legend) that stay fixed on
 * screen.
 *
 * <p>The zoom is applied to a wrapper group, never to the plan itself, so the plan keeps
 * working in its own coordinates and can be snapshotted at its natural size.
 * Ctrl + wheel zooms around the pointer; dragging with the middle button pans.
 */
final class PlanViewport extends StackPane {

    static final double MIN_ZOOM = 0.5;
    static final double MAX_ZOOM = 4.0;
    private static final double STEP = 1.25;

    private final Node content;
    private final Scale scale = new Scale(1, 1, 0, 0);
    private final ScrollPane scroll;
    private final ReadOnlyDoubleWrapper zoom = new ReadOnlyDoubleWrapper(1.0);

    private double panStartX;
    private double panStartY;
    private double panStartH;
    private double panStartV;

    /**
     * @param content the plan, laid out at its natural size
     * @param overlay a node pinned to the top-right corner of the viewport
     */
    PlanViewport(Node content, Node overlay) {
        this.content = content;
        Group zoomed = new Group(content);
        zoomed.getTransforms().add(scale);

        StackPane holder = new StackPane(new Group(zoomed));
        holder.getStyleClass().add("plan-area");
        scroll = new ScrollPane(holder);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("plan-scroll");

        StackPane.setAlignment(overlay, Pos.TOP_RIGHT);
        StackPane.setMargin(overlay, new Insets(24, 32, 0, 0));
        getChildren().addAll(scroll, overlay);

        scroll.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        scroll.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onPanStart);
        scroll.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onPan);
    }

    ReadOnlyDoubleProperty zoomProperty() {
        return zoom.getReadOnlyProperty();
    }

    void setPreferredViewport(double width, double height) {
        scroll.setPrefViewportWidth(width);
        scroll.setPrefViewportHeight(height);
    }

    void zoomIn() {
        zoomTo(zoom.get() * STEP, null);
    }

    void zoomOut() {
        zoomTo(zoom.get() / STEP, null);
    }

    void actualSize() {
        zoomTo(1.0, null);
    }

    /** Largest zoom at which the whole plan is visible. */
    void fit() {
        Bounds viewport = scroll.getViewportBounds();
        Bounds plan = content.getLayoutBounds();
        if (viewport.getWidth() <= 0 || plan.getWidth() <= 0) {
            return;
        }
        zoomTo(Math.min(viewport.getWidth() / plan.getWidth(), viewport.getHeight() / plan.getHeight()), null);
    }

    /**
     * Changes the zoom keeping the content point under {@code anchorScene} (or the viewport
     * centre) where it is on screen.
     */
    private void zoomTo(double requested, Point2D anchorScene) {
        double target = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requested));
        if (Math.abs(target - zoom.get()) < 1e-6) {
            return;
        }
        Point2D anchor = (anchorScene != null) ? anchorScene : viewportCentreInScene();
        Point2D pinned = content.sceneToLocal(anchor);

        scale.setX(target);
        scale.setY(target);
        zoom.set(target);
        scroll.applyCss();
        scroll.layout();

        Point2D moved = content.localToScene(pinned);
        scrollBy(moved.getX() - anchor.getX(), moved.getY() - anchor.getY());
    }

    private Point2D viewportCentreInScene() {
        Bounds viewport = scroll.localToScene(scroll.getBoundsInLocal());
        return new Point2D(viewport.getCenterX(), viewport.getCenterY());
    }

    /** Scrolls the content by a distance in pixels. */
    private void scrollBy(double dx, double dy) {
        Bounds viewport = scroll.getViewportBounds();
        Bounds total = scroll.getContent().getLayoutBounds();
        double extraWidth = total.getWidth() - viewport.getWidth();
        double extraHeight = total.getHeight() - viewport.getHeight();
        if (extraWidth > 0) {
            scroll.setHvalue(clamp(scroll.getHvalue() + dx / extraWidth));
        }
        if (extraHeight > 0) {
            scroll.setVvalue(clamp(scroll.getVvalue() + dy / extraHeight));
        }
    }

    private void onScroll(ScrollEvent event) {
        if (!event.isShortcutDown() || event.getDeltaY() == 0) {
            return;
        }
        double factor = event.getDeltaY() > 0 ? STEP : 1 / STEP;
        zoomTo(zoom.get() * factor, new Point2D(event.getSceneX(), event.getSceneY()));
        event.consume();
    }

    private void onPanStart(MouseEvent event) {
        if (event.getButton() != MouseButton.MIDDLE) {
            return;
        }
        panStartX = event.getSceneX();
        panStartY = event.getSceneY();
        panStartH = scroll.getHvalue();
        panStartV = scroll.getVvalue();
        event.consume();
    }

    private void onPan(MouseEvent event) {
        if (!event.isMiddleButtonDown()) {
            return;
        }
        scroll.setHvalue(panStartH);
        scroll.setVvalue(panStartV);
        scrollBy(panStartX - event.getSceneX(), panStartY - event.getSceneY());
        event.consume();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
