package io.github.phlekies.smarthome.ui;

import io.github.phlekies.smarthome.app.SimulatorModel;

/** Conversion between plan metres (y pointing up) and view pixels (y pointing down). */
final class PlanCoordinates {

    static final int MARGIN_PX = 36;
    static final int PIXELS_PER_METER = 22;
    static final int WIDTH_METERS = SimulatorModel.PLAN_WIDTH_METERS;
    static final int HEIGHT_METERS = SimulatorModel.PLAN_HEIGHT_METERS;
    static final double PLAN_WIDTH_PX = WIDTH_METERS * PIXELS_PER_METER;
    static final double PLAN_HEIGHT_PX = HEIGHT_METERS * PIXELS_PER_METER;
    static final double VIEW_WIDTH_PX = PLAN_WIDTH_PX + 2 * MARGIN_PX;
    static final double VIEW_HEIGHT_PX = PLAN_HEIGHT_PX + 2 * MARGIN_PX;

    private PlanCoordinates() {
    }

    static double toPixelX(double meters) {
        return MARGIN_PX + meters * PIXELS_PER_METER;
    }

    static double toPixelY(double meters) {
        return MARGIN_PX + (HEIGHT_METERS - meters) * PIXELS_PER_METER;
    }

    static double toMetersX(double pixelX) {
        return (pixelX - MARGIN_PX) / PIXELS_PER_METER;
    }

    static double toMetersY(double pixelY) {
        return HEIGHT_METERS - (pixelY - MARGIN_PX) / PIXELS_PER_METER;
    }

    static boolean isInsidePlan(double pixelX, double pixelY) {
        double x = toMetersX(pixelX);
        double y = toMetersY(pixelY);
        return x >= 0.0 && y >= 0.0 && x <= WIDTH_METERS && y <= HEIGHT_METERS;
    }
}
