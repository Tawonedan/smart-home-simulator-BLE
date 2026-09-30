package io.github.phlekies.smarthome.ui;

import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelX;
import static io.github.phlekies.smarthome.ui.PlanCoordinates.toPixelY;

import java.util.List;
import java.util.function.Consumer;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

import io.github.phlekies.smarthome.simulation.raytrace.HubHit;
import io.github.phlekies.smarthome.simulation.raytrace.RaySegment;
import io.github.phlekies.smarthome.simulation.raytrace.RayTraceResult;

/**
 * Animates a traced ray tree: every ray front advances at the same speed, so reflections and
 * transmissions appear in the order they physically happen. Segments are drawn incrementally
 * on a canvas and fade with the remaining power.
 */
final class RayAnimator {

    /** Front speed in metres per second of animation (one metre every 30 ms). */
    private static final double SPEED_M_PER_S = 33.0;
    private static final double STRONG_DBM = -40.0;
    private static final double WEAK_DBM = -100.0;
    private static final Color RAY_COLOR = Color.web("#ff8f00");

    private final PlanView view;
    private AnimationTimer timer;

    RayAnimator(PlanView view) {
        this.view = view;
    }

    /**
     * Plays the animation from scratch.
     *
     * @param onHubHit called once per ray reaching the hub, in arrival order
     */
    void play(RayTraceResult result, Consumer<HubHit> onHubHit) {
        stop();
        view.clearRays();
        List<RaySegment> segments = result.segments();
        List<HubHit> hits = result.hubHits();
        double totalLength = result.maxPathLengthMeters();
        GraphicsContext g = view.raysCanvas().getGraphicsContext2D();
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineWidth(1.4);

        timer = new AnimationTimer() {
            private long startNanos = -1;
            private double drawnMeters = 0.0;
            private int nextHit = 0;

            @Override
            public void handle(long now) {
                if (startNanos < 0) {
                    startNanos = now;
                }
                double front = Math.min(totalLength, (now - startNanos) / 1e9 * SPEED_M_PER_S);
                for (RaySegment segment : segments) {
                    drawPortion(g, segment, drawnMeters, front);
                }
                drawnMeters = front;
                while (nextHit < hits.size() && hits.get(nextHit).pathLengthMeters() <= front) {
                    onHubHit.accept(hits.get(nextHit++));
                }
                if (front >= totalLength) {
                    stop();
                }
            }
        };
        timer.start();
    }

    void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    void clear() {
        stop();
        view.clearRays();
    }

    /** Draws the part of a segment travelled between two path lengths. */
    private static void drawPortion(GraphicsContext g, RaySegment segment, double fromMeters, double toMeters) {
        double from = Math.max(fromMeters, segment.startDistanceMeters());
        double to = Math.min(toMeters, segment.endDistanceMeters());
        double length = segment.lengthMeters();
        if (to <= from || length <= 0) {
            return;
        }
        double f0 = (from - segment.startDistanceMeters()) / length;
        double f1 = (to - segment.startDistanceMeters()) / length;
        double power = segment.startPowerDbm() + (segment.endPowerDbm() - segment.startPowerDbm()) * f1;
        double strength = Math.max(0.0, Math.min(1.0, (power - WEAK_DBM) / (STRONG_DBM - WEAK_DBM)));

        g.setStroke(RAY_COLOR.deriveColor(0, 1, 1, 0.15 + 0.8 * strength));
        g.strokeLine(
                toPixelX(segment.x1() + (segment.x2() - segment.x1()) * f0),
                toPixelY(segment.y1() + (segment.y2() - segment.y1()) * f0),
                toPixelX(segment.x1() + (segment.x2() - segment.x1()) * f1),
                toPixelY(segment.y1() + (segment.y2() - segment.y1()) * f1));
    }
}
