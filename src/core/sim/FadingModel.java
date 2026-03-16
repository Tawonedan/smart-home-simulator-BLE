package core.sim;

public enum FadingModel {
    NONE("Sin fading"),
    RAYLEIGH("Rayleigh"),
    RICIAN("Rician");

    private final String label;

    FadingModel(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
