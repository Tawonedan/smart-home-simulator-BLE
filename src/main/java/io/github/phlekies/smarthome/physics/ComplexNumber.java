package io.github.phlekies.smarthome.physics;

/** Immutable complex number, used to sum field phasors coherently. */
public record ComplexNumber(double real, double imaginary) {

    public static final ComplexNumber ZERO = new ComplexNumber(0.0, 0.0);

    public static ComplexNumber fromPolar(double magnitude, double phaseRad) {
        return new ComplexNumber(magnitude * Math.cos(phaseRad), magnitude * Math.sin(phaseRad));
    }

    public ComplexNumber add(ComplexNumber other) {
        return new ComplexNumber(real + other.real, imaginary + other.imaginary);
    }

    public ComplexNumber scale(double factor) {
        return new ComplexNumber(real * factor, imaginary * factor);
    }

    public double magnitude() {
        return Math.hypot(real, imaginary);
    }

    public double magnitudeSquared() {
        return real * real + imaginary * imaginary;
    }

    public double phaseRad() {
        return Math.atan2(imaginary, real);
    }
}
