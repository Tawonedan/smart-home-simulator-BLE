package io.github.phlekies.smarthome.physics;

/**
 * Immutable complex number, used to sum field phasors coherently.
 *
 * @param real      real part
 * @param imaginary imaginary part
 */
public record ComplexNumber(double real, double imaginary) {

    /** The additive identity. */
    public static final ComplexNumber ZERO = new ComplexNumber(0.0, 0.0);

    /** Creates m·e^(iφ). */
    public static ComplexNumber fromPolar(double magnitude, double phaseRad) {
        return new ComplexNumber(magnitude * Math.cos(phaseRad), magnitude * Math.sin(phaseRad));
    }

    /** Sum of two complex numbers. */
    public ComplexNumber add(ComplexNumber other) {
        return new ComplexNumber(real + other.real, imaginary + other.imaginary);
    }

    /** Product with a real number. */
    public ComplexNumber scale(double factor) {
        return new ComplexNumber(real * factor, imaginary * factor);
    }

    /** Modulus |z|. */
    public double magnitude() {
        return Math.hypot(real, imaginary);
    }

    /** Squared modulus |z|², i.e. the power of a field phasor. */
    public double magnitudeSquared() {
        return real * real + imaginary * imaginary;
    }

    /** Argument of z in (−π, π]. */
    public double phaseRad() {
        return Math.atan2(imaginary, real);
    }
}
