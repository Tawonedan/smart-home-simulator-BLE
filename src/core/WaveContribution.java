package core;

public class WaveContribution {
    private double x;
    private double y;
    private double amplitude;
    private double phase;

    public WaveContribution(double x, double y, double amplitude, double phase) {
        this.x = x;
        this.y = y;
        this.amplitude = amplitude;
        this.phase = phase;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getAmplitude() { return amplitude; }
    public double getPhase() { return phase; }
}
