package core.sim;

public enum PathType {
    DIRECT("Directo"),
    REFLECTION("Reflexión"),
    DIFFRACTION("Difracción"),
    SCATTER("Dispersión");

    private final String label;

    PathType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
