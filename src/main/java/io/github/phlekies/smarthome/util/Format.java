package io.github.phlekies.smarthome.util;

import java.util.Locale;

/** Consistent, locale-independent formatting of physical quantities. */
public final class Format {

    private Format() {
    }

    /** Absolute power, e.g. {@code -62.50 dBm}. */
    public static String dbm(double value) {
        return String.format(Locale.US, "%.2f dBm", value);
    }

    /** Ratio or loss, e.g. {@code 12.30 dB}. */
    public static String db(double value) {
        return String.format(Locale.US, "%.2f dB", value);
    }

    /** Data rate, e.g. {@code 54.00 Mbps}. */
    public static String mbps(double value) {
        return String.format(Locale.US, "%.2f Mbps", value);
    }

    /** Bit error rate in scientific notation. */
    public static String ber(double value) {
        return String.format(Locale.US, "%.2e", value);
    }

    /** Distance, e.g. {@code 3.25 m}. */
    public static String meters(double value) {
        return String.format(Locale.US, "%.2f m", value);
    }

    /** Angle, e.g. {@code 45.0°}. */
    public static String degrees(double value) {
        return String.format(Locale.US, "%.1f°", value);
    }

    /** Plain number with a fixed number of decimals. */
    public static String number(double value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }

    /** Frequency with an automatic unit (Hz, kHz, MHz, GHz). */
    public static String hertz(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e9) return String.format(Locale.US, "%.2f GHz", value / 1e9);
        if (abs >= 1e6) return String.format(Locale.US, "%.2f MHz", value / 1e6);
        if (abs >= 1e3) return String.format(Locale.US, "%.2f kHz", value / 1e3);
        return String.format(Locale.US, "%.0f Hz", value);
    }

    /** Carrier frequency, e.g. {@code 2.40 GHz (2400 MHz)}. */
    public static String frequencyMHz(double freqMHz) {
        if (freqMHz >= 1000.0) {
            return String.format(Locale.US, "%.2f GHz (%.0f MHz)", freqMHz / 1000.0, freqMHz);
        }
        return String.format(Locale.US, "%.0f MHz", freqMHz);
    }

    /** Coordinates with one decimal, e.g. {@code (3.0, 4.5)}. */
    public static String point(double x, double y) {
        return String.format(Locale.US, "(%.1f, %.1f)", x, y);
    }
}
