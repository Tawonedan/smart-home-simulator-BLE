package core.sim;

public enum PathType {
    DIRECT("Directo"),
    REFLECTION("ReflexiÃ³n"),
    DIFFRACTION("DifracciÃ³n"),
    SCATTER("DispersiÃ³n");

    private final String label;

    PathType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
