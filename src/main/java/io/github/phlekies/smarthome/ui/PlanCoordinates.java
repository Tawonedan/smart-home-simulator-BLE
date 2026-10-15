package io.github.phlekies.smarthome.ui;

import io.github.phlekies.smarthome.app.SimulatorModel;

/** Conversion between plan metres (y pointing up) and view pixels (y pointing down). */
final class PlanCoordinates {

    static final int MARGIN_PX = 36;
    static final int PIXELS_PER_METER = 22;
    private static int widthMeters = SimulatorModel.PLAN_WIDTH_METERS;
    private static int heightMeters = SimulatorModel.PLAN_HEIGHT_METERS;

    private PlanCoordinates() {
    }

    static int widthMeters() {
        return widthMeters;
    }

    static int heightMeters() {
        return heightMeters;
    }

    static double planWidthPx() {
        return widthMeters * PIXELS_PER_METER;
    }

    static double planHeightPx() {
        return heightMeters * PIXELS_PER_METER;
    }

    static double viewWidthPx() {
        return planWidthPx() + 2 * MARGIN_PX;
    }

    static double viewHeightPx() {
        return planHeightPx() + 2 * MARGIN_PX;
    }

    static void setDimensions(int w, int h) {
        widthMeters = Math.max(10, Math.min(500, w));
        heightMeters = Math.max(10, Math.min(500, h));
    }

    static double toPixelX(double meters) {
        return MARGIN_PX + meters * PIXELS_PER_METER;
    }

    static double toPixelY(double meters) {
        return MARGIN_PX + (heightMeters - meters) * PIXELS_PER_METER;
    }

    static double toMetersX(double pixelX) {
        return (pixelX - MARGIN_PX) / PIXELS_PER_METER;
    }

    static double toMetersY(double pixelY) {
        return heightMeters - (pixelY - MARGIN_PX) / PIXELS_PER_METER;
    }

    static boolean isInsidePlan(double pixelX, double pixelY) {
        double x = toMetersX(pixelX);
        double y = toMetersY(pixelY);
        return x >= 0.0 && y >= 0.0 && x <= widthMeters && y <= heightMeters;
    }
}
