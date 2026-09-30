package io.github.phlekies.smarthome.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * Minimal harness to drive a real {@link SimulatorApp} from JUnit. The build runs the tests on
 * the headless Monocle platform, so no display is needed.
 */
final class FxTestSupport {

    private static boolean started;

    private FxTestSupport() {
    }

    static synchronized void startPlatform() {
        if (!started) {
            Platform.setImplicitExit(false);
            Platform.startup(() -> { });
            started = true;
        }
    }

    /** Starts a fresh application window. */
    static SimulatorApp launch() {
        startPlatform();
        return onFx(() -> {
            SimulatorApp app = new SimulatorApp();
            app.start(new Stage());
            return app;
        });
    }

    static void close(SimulatorApp app) {
        onFx(() -> {
            app.stop();
            app.stage.close();
            return null;
        });
    }

    /** Runs code on the JavaFX thread and waits for its result. */
    static <T> T onFx(Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable ex) {
                result.completeExceptionally(ex);
            }
        });
        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new AssertionError("JavaFX action failed", ex);
        }
    }

    static void onFx(Runnable action) {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    /** Polls a condition (evaluated on the JavaFX thread) until it holds or the timeout expires. */
    static void waitUntil(String description, BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(condition::getAsBoolean)) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Timed out waiting until " + description);
    }

    /** Fires a primary-button mouse event at a point given in the target's scene coordinates. */
    static void mouse(Node target, EventType<MouseEvent> type, Point2D scenePoint) {
        Event.fireEvent(target, new MouseEvent(type, scenePoint.getX(), scenePoint.getY(), 0, 0,
                MouseButton.PRIMARY, 1, false, false, false, false,
                type != MouseEvent.MOUSE_RELEASED && type != MouseEvent.MOUSE_CLICKED, false, false,
                true, false, false, null));
    }
}
