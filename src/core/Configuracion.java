package core;

import java.util.*;
import java.util.function.Consumer;

/** Mantiene la lista de sensores definidos y notifica cambios a la UI. */
public class Configuracion {

    private final List<Sensor> sensores = new ArrayList<>();
    private final List<Consumer<Change>> listeners = new ArrayList<>();

    // === Sensores ===
    public List<Sensor> getSensores() {
        return Collections.unmodifiableList(sensores); // solo lectura
    }

    public void addSensor(Sensor s) {
        sensores.add(s);
        notifyListeners(new Change(Change.Type.ADDED, s));
    }

    public boolean removeSensor(Sensor s) {
        boolean removed = sensores.remove(s);
        if (removed) {
            notifyListeners(new Change(Change.Type.REMOVED, s));
        }
        return removed;
    }

    public boolean removeSensorById(String id) {
        Sensor target = sensores.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (target != null) {
            return removeSensor(target);
        }
        return false;
    }

    public void clear() {
        sensores.clear();
        notifyListeners(new Change(Change.Type.CLEARED, null));
    }

    // === Listeners ===
    public void addListener(Consumer<Change> l) { listeners.add(l); }
    public void removeListener(Consumer<Change> l) { listeners.remove(l); }

    private void notifyListeners(Change c) {
        for (var l : listeners) l.accept(c);
    }

    /** Evento simple para notificar cambios. */
    public static final class Change {
        public enum Type { ADDED, REMOVED, CLEARED }
        public final Type type;
        public final Sensor sensor; // útil en ADDED y REMOVED

        public Change(Type type, Sensor sensor) {
            this.type = type;
            this.sensor = sensor;
        }
    }

    // ======= Hub =======
    private Hub hub;

    public Hub getHub() { return hub; }
    public void setHub(Hub hub) { this.hub = hub; }
    public boolean hasHub() { return hub != null; }

    // ======= Obstáculos legacy =======
    private final List<Obstacle> obstaculos = new ArrayList<>();

    public void setObstaculos(List<Obstacle> walls) {
        obstaculos.clear();
        obstaculos.addAll(walls);
    }

    public List<Obstacle> getObstaculos() {
        return Collections.unmodifiableList(obstaculos);
    }
}
