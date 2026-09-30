package io.github.phlekies.smarthome.ui;

import static io.github.phlekies.smarthome.ui.FxTestSupport.mouse;
import static io.github.phlekies.smarthome.ui.FxTestSupport.onFx;
import static io.github.phlekies.smarthome.ui.FxTestSupport.waitUntil;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.phlekies.smarthome.app.LinkSummary;
import io.github.phlekies.smarthome.model.Sensor;

/** End-to-end smoke tests of the real application window, run headless on Monocle. */
class SimulatorAppUiTest {

    private SimulatorApp app;

    @BeforeEach
    void launch() {
        app = FxTestSupport.launch();
    }

    @AfterEach
    void close() {
        FxTestSupport.close(app);
    }

    /** Must be called on the JavaFX thread. */
    private Button button(String text) {
        return app.stage.getScene().getRoot().lookupAll(".button").stream()
                .filter(node -> node instanceof Button b && text.equals(b.getText()))
                .map(Button.class::cast).findFirst().orElseThrow(() -> new AssertionError("No button " + text));
    }

    private PlanView planView() {
        return onFx(() -> app.stage.getScene().getRoot().lookupAll(".plan-view").stream()
                .map(PlanView.class::cast).findFirst().orElseThrow());
    }

    private Point2D sceneOf(Node node, double metersX, double metersY) {
        return node.localToScene(PlanCoordinates.toPixelX(metersX), PlanCoordinates.toPixelY(metersY));
    }

    @Test
    void startsWithTheDemoAndComputesTheCoverageInTheBackground() {
        assertEquals(6, onFx(() -> app.model.sensors().size()));
        assertEquals("Smart apartment - Smart Home Simulator", onFx(() -> app.stage.getTitle()));
        waitUntil("the heatmap coverage is known", () -> !app.state.coverage.get().isEmpty(), 30_000);
        assertEquals(6, onFx(() -> app.state.links.get().size()));
    }

    @Test
    void optimiseHubMovesTheHubAndKeepsEverySensorConnected() {
        int[] before = onFx(() -> new int[] { app.model.hub().orElseThrow().getX(), app.model.hub().orElseThrow().getY() });
        onFx(() -> button("Optimise hub").fire());
        waitUntil("the optimisation finishes", () -> !app.simulation.optimisingProperty().get()
                && !app.state.optimisation.get().isEmpty(), 30_000);

        int[] after = onFx(() -> new int[] { app.model.hub().orElseThrow().getX(), app.model.hub().orElseThrow().getY() });
        assertFalse(Arrays.equals(before, after), "the demo hub starts in a corner and must move");
        assertTrue(onFx(() -> app.state.links.get().stream().allMatch(LinkSummary::connected)));
    }

    @Test
    void draggingASensorMovesIt() {
        PlanView plan = planView();
        Sensor thermostat = onFx(() -> app.model.sensors().getFirst());
        Node dot = onFx(() -> plan.lookupAll(".device-label").stream()
                .filter(label -> ((Labeled) label).getText().equals(thermostat.getName()))
                .map(label -> label.getParent().getChildrenUnmodifiable().getFirst()).findFirst().orElseThrow());

        onFx(() -> {
            mouse(dot, MouseEvent.MOUSE_PRESSED, sceneOf(plan, thermostat.getX(), thermostat.getY()));
            for (int step = 1; step <= 5; step++) {
                mouse(dot, MouseEvent.MOUSE_DRAGGED, sceneOf(plan, 7 + step * 2, 12 + step));
            }
            mouse(dot, MouseEvent.MOUSE_RELEASED, sceneOf(plan, 17, 17));
        });

        assertEquals(17, onFx(thermostat::getX));
        assertEquals(17, onFx(thermostat::getY));
        assertEquals(thermostat, onFx(() -> app.state.selectedDevice.get()));
        assertTrue(onFx(() -> app.stage.getTitle().contains("*")), "the project is now modified");
    }

    @Test
    void drawingAWallWithTheMouseCanBeUndone() {
        PlanView plan = planView();
        int walls = onFx(() -> app.model.walls().size());
        onFx(() -> {
            app.state.tool.set(EditorTool.WALL);
            mouse(plan, MouseEvent.MOUSE_CLICKED, sceneOf(plan, 3, 35));
            mouse(plan, MouseEvent.MOUSE_CLICKED, sceneOf(plan, 30, 35));
        });
        assertEquals(walls + 1, onFx(() -> app.model.walls().size()));

        Button undo = onFx(() -> app.stage.getScene().getRoot().lookupAll(".button").stream()
                .filter(node -> node instanceof Button b && b.getTooltip() != null
                        && b.getTooltip().getText().startsWith("Undo"))
                .map(Button.class::cast).findFirst().orElseThrow());
        onFx(undo::fire);
        assertEquals(walls, onFx(() -> app.model.walls().size()));
    }

    @Test
    void zoomChangesTheScaleWithinItsLimits() {
        onFx(() -> app.viewport.actualSize());
        onFx(() -> app.viewport.zoomIn());
        assertEquals(1.25, onFx(() -> app.viewport.zoomProperty().get()), 1e-9);
        onFx(() -> {
            for (int i = 0; i < 20; i++) {
                app.viewport.zoomOut();
            }
        });
        assertEquals(PlanViewport.MIN_ZOOM, onFx(() -> app.viewport.zoomProperty().get()), 1e-9);
    }

    @Test
    void theThemeCanBeSwitched() {
        onFx(() -> app.state.darkTheme.set(false));
        assertTrue(onFx(Application::getUserAgentStylesheet).contains("primer-light"));
        onFx(() -> app.state.darkTheme.set(true));
        assertTrue(onFx(Application::getUserAgentStylesheet).contains("primer-dark"));
    }

    @Test
    void theSummaryWindowOpensAndClosesCleanly() {
        onFx(() -> app.simulation.openSummary());
        Window summary = onFx(() -> Window.getWindows().stream()
                .filter(window -> window instanceof Stage s && "Simulation summary".equals(s.getTitle()))
                .findFirst().orElseThrow());
        onFx(() -> app.model.moveDevice(app.model.sensors().getFirst(), 20, 20));
        onFx(summary::hide);
        assertFalse(onFx(summary::isShowing));
    }

    @Test
    void theExportedImageIsAPngOfThePlan() throws Exception {
        waitUntil("the heatmap coverage is known", () -> !app.state.coverage.get().isEmpty(), 30_000);
        Node plan = onFx(() -> app.stage.getScene().getRoot().lookup(".plan-stack"));
        Node legend = onFx(() -> app.stage.getScene().getRoot().lookup(".legend"));
        byte[] png = onFx(() -> Snapshots.planWithOverlay(plan, legend));

        byte[] signature = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
        assertArrayEquals(signature, Arrays.copyOf(png, 8));
        assertNotEquals(0, png.length);
    }
}
