package core.sim;

public enum PropagationMode {
    RAYS("Rayos"),
    WAVES("Ondas");

    private final String label;

    PropagationMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
