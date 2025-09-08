package core;

import java.util.*;
import java.util.function.Consumer;

/** Mantiene la lista de sensores definidos y notifica cambios a la UI. */
public class Configuracion {

    private final List<Sensor> sensores = new ArrayList<>();
    private final List<Consumer<Change>> listeners = new ArrayList<>();

    public List<Sensor> getSensores() {
        return Collections.unmodifiableList(sensores);
    }

    public void addSensor(Sensor s) {
        sensores.add(s);
        notifyListeners(new Change(Change.Type.ADDED, s));
    }

    public boolean removeSensorById(String id) {
        boolean removed = sensores.removeIf((Sensor s) -> s.getId().equals(id));
        if (removed) notifyListeners(new Change(Change.Type.REMOVED, null));
        return removed;
    }

    public void clear() {
        sensores.clear();
        notifyListeners(new Change(Change.Type.CLEARED, null));
    }

    public void addListener(Consumer<Change> l) { listeners.add(l); }
    public void removeListener(Consumer<Change> l) { listeners.remove(l); }

    private void notifyListeners(Change c) {
        for (var l : listeners) l.accept(c);
    }

    /** Evento simple para notificar cambios. */
    public static final class Change {
        public enum Type { ADDED, REMOVED, CLEARED }
        public final Type type;
        public final Sensor sensor; // solo en ADDED

        public Change(Type type, Sensor sensor) {
            this.type = type;
            this.sensor = sensor;
        }
    }
    //=======Añadimos los Hub=========
    private Hub hub;

    public Hub getHub() { return hub; }
    public void setHub(Hub hub) { this.hub = hub; }
    public boolean hasHub() { return hub != null; }
    
    // ==============Añadimos los obstaculos=================
    private List<Obstacle> obstaculos = new ArrayList<>();

    public void setObstaculos(List<Obstacle> walls) {
        obstaculos.clear();
        obstaculos.addAll(walls);
    }

    public List<Obstacle> getObstaculos() {
        return obstaculos;
    }



    
    
}
