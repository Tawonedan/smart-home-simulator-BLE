package io.github.phlekies.smarthome.physics;

/**
 * Closed-form radio formulas shared by the propagation engine, the ray tracer and the UI.
 *
 * <p>Units are part of every method name: distances in metres, frequencies in MHz,
 * powers in dBm, gains and losses in dB.
 */
public final class RadioMath {

    /** Speed of light in vacuum, m/s. */
    public static final double SPEED_OF_LIGHT_MPS = 299_792_458.0;

    /** Thermal noise power spectral density at 290 K, dBm/Hz. */
    public static final double THERMAL_NOISE_DBM_PER_HZ = -174.0;

    private static final double MIN_LINEAR = 1e-15;

    private RadioMath() {
    }

    /** Free-space path loss (Friis): 32.45 + 20·log10(f[MHz]) + 20·log10(d[km]). */
    public static double fsplDb(double distanceMeters, double freqMHz) {
        if (distanceMeters <= 0) return 0.0;
        double distanceKm = distanceMeters / 1000.0;
        return 32.45 + 20.0 * Math.log10(freqMHz) + 20.0 * Math.log10(distanceKm);
    }

    /**
     * Indoor log-distance path loss anchored at a 1 m free-space reference:
     * PL(d) = FSPL(1 m) + 10·n·log10(d). With {@code n = 2} it matches free space.
     */
    public static double logDistanceLossDb(double distanceMeters, double freqMHz, double pathLossExponent) {
        if (distanceMeters <= 0) return 0.0;
        if (distanceMeters <= 1.0) return fsplDb(distanceMeters, freqMHz);
        double n = Math.max(1.0, pathLossExponent);
        return fsplDb(1.0, freqMHz) + 10.0 * n * Math.log10(distanceMeters);
    }

    /** Receiver noise floor: N = -174 dBm/Hz + 10·log10(B) + NF. */
    public static double noiseFloorDbm(double bandwidthHz, double noiseFigureDb) {
        return THERMAL_NOISE_DBM_PER_HZ + 10.0 * Math.log10(bandwidthHz) + noiseFigureDb;
    }

    /** Wavelength λ = c / f. */
    public static double wavelengthMeters(double freqMHz) {
        return SPEED_OF_LIGHT_MPS / (freqMHz * 1e6);
    }

    /** Converts dBm to milliwatts. */
    public static double dbmToMilliwatt(double dbm) {
        return Math.pow(10.0, dbm / 10.0);
    }

    /** Converts milliwatts to dBm (clamped to avoid log of zero). */
    public static double milliwattToDbm(double milliwatt) {
        return 10.0 * Math.log10(Math.max(milliwatt, MIN_LINEAR));
    }

    /** Converts a power ratio from dB to linear. */
    public static double dbToLinear(double db) {
        return Math.pow(10.0, db / 10.0);
    }

    /** Converts a linear power ratio to dB (clamped to avoid log of zero). */
    public static double linearToDb(double ratio) {
        return 10.0 * Math.log10(Math.max(ratio, MIN_LINEAR));
    }

    /** Carrier phase accumulated over a path: 2π·d/λ. */
    public static double phaseRad(double distanceMeters, double wavelengthMeters) {
        if (distanceMeters <= 0 || wavelengthMeters <= 0) return 0.0;
        return 2.0 * Math.PI * distanceMeters / wavelengthMeters;
    }

    /** Shannon capacity C = B·log2(1 + SNR), in Mbit/s. */
    public static double shannonCapacityMbps(double snrDb, double bandwidthHz) {
        double snrLinear = dbToLinear(snrDb);
        return bandwidthHz * (Math.log(1.0 + snrLinear) / Math.log(2.0)) / 1e6;
    }

    /** Bit error rate of BPSK over AWGN: Q(√(2·Eb/N0)) = ½·erfc(√(Eb/N0)). */
    public static double bpskBer(double snrDb) {
        double snrLinear = dbToLinear(snrDb);
        return 0.5 * erfc(Math.sqrt(snrLinear));
    }

    /** Single knife-edge diffraction loss J(v) from ITU-R P.526 (approximation for v > -0.78). */
    public static double knifeEdgeLossDb(double v) {
        if (v <= -0.78) return 0.0;
        double term = Math.sqrt((v - 0.1) * (v - 0.1) + 1.0) + v - 0.1;
        return 6.9 + 20.0 * Math.log10(Math.max(term, MIN_LINEAR));
    }

    /** Smallest absolute difference between two angles, in [0, 180] degrees. */
    public static double angularDistanceDeg(double aDeg, double bDeg) {
        double diff = Math.abs(normalizeAngleDeg(aDeg) - normalizeAngleDeg(bDeg));
        return (diff > 180.0) ? 360.0 - diff : diff;
    }

    /** Maps any angle to [0, 360) degrees. */
    public static double normalizeAngleDeg(double angleDeg) {
        double normalized = angleDeg % 360.0;
        return (normalized < 0) ? normalized + 360.0 : normalized;
    }

    /** Complementary error function (Abramowitz & Stegun 7.1.26, |error| < 1.5e-7). */
    static double erfc(double x) {
        double sign = (x < 0) ? -1.0 : 1.0;
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * ax);
        double poly = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t
                + 0.254829592) * t;
        double erf = 1.0 - poly * Math.exp(-ax * ax);
        return 1.0 - sign * erf;
    }
}
