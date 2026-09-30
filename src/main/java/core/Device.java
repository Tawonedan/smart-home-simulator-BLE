package core;

public class Device {
    protected final String id;
    protected String nombre;
    protected int x;
    protected int y;

    public Device(String id, String nombre, int x, int y) {
        this.id = id;
        this.nombre = nombre;
        this.x = x;
        this.y = y;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
}
