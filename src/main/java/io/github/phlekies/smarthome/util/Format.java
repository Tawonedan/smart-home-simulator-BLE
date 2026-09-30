package io.github.phlekies.smarthome.util;

import java.util.Locale;

/** Consistent, locale-independent formatting of physical quantities. */
public final class Format {

    private Format() {
    }

    public static String dbm(double value) {
        return String.format(Locale.US, "%.2f dBm", value);
    }

    public static String db(double value) {
        return String.format(Locale.US, "%.2f dB", value);
    }

    public static String mbps(double value) {
        return String.format(Locale.US, "%.2f Mbps", value);
    }

    public static String ber(double value) {
        return String.format(Locale.US, "%.2e", value);
    }

    public static String meters(double value) {
        return String.format(Locale.US, "%.2f m", value);
    }

    public static String degrees(double value) {
        return String.format(Locale.US, "%.1f°", value);
    }

    public static String number(double value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    public static String hertz(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e9) return String.format(Locale.US, "%.2f GHz", value / 1e9);
        if (abs >= 1e6) return String.format(Locale.US, "%.2f MHz", value / 1e6);
        if (abs >= 1e3) return String.format(Locale.US, "%.2f kHz", value / 1e3);
        return String.format(Locale.US, "%.0f Hz", value);
    }

    public static String frequencyMHz(double freqMHz) {
        if (freqMHz >= 1000.0) {
            return String.format(Locale.US, "%.2f GHz (%.0f MHz)", freqMHz / 1000.0, freqMHz);
        }
        return String.format(Locale.US, "%.0f MHz", freqMHz);
    }

    public static String point(double x, double y) {
        return String.format(Locale.US, "(%.1f, %.1f)", x, y);
    }
}
