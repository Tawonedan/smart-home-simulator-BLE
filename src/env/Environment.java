package env;

import core.Obstacle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import core.Wall;
import static core.material.MaterialsDB.*;

/**
 * Environment: parámetros físicos y del escenario para la simulación.
 * 
 * Unidades:
 *  - Frecuencia en MHz (internamente se convierte a Hz cuando es necesario)
 *  - Distancia en metros
 *  - Potencia en dBm
 *  - Pérdidas en dB
 *  - Ancho de banda en Hz
 */
public class Environment {

    /* ==========================
     *  Constantes físicas/útiles
     * ========================== */
	
	private final List<Wall> walls;

    /** Velocidad de la luz en m/s */
    public static final double SPEED_OF_LIGHT_MS = 299_792_458.0;

    /** Frecuencias típicas (MHz) */
    public static final double WIFI_24_GHZ_MHZ = 2400.0;
    public static final double WIFI_5_GHZ_MHZ  = 5200.0;

    /* ==========================
     *  Parámetros de propagación
     * ========================== */

    /** Pérdida adicional por metro (dB/m) si mantienes el modelo simple de atenuación lineal */
    private double alphaDbPerMeter;

    /** Frecuencia central de operación (MHz) */
    private double freqMHz;

    /** Ganancia sistémica (márgenes varios opcionales) en dB; por defecto 0 */
    private double systemGainDb;

    /* ==========================
     *  Ruido y capa PHY
     * ========================== */

    /** Ancho de banda (Hz). Ej.: Wi-Fi 20 MHz => 20e6 */
    private double bandwidthHz;

    /** Figura de ruido del receptor (dB). Ej.: 7 dB */
    private double noiseFigureDb;

    /* ==========================
     *  Escenario
     * ========================== */

    /** Obstáculos/paredes del entorno (segmentos). 
     *  Más adelante puedes sustituir Obstacle por Wall con Material. */
    private final List<Obstacle> obstaculos;

    /* ==========================
     *  Constructores
     * ========================== */

    public Environment() {
        // Valores por defecto razonables
        this.alphaDbPerMeter = 0.5;         // dB/m opcional (puedes poner 0.0 si no lo usas)
        this.freqMHz         = WIFI_24_GHZ_MHZ;
        this.bandwidthHz     = 20e6;        // 20 MHz
        this.noiseFigureDb   = 7.0;         // dB
        this.systemGainDb    = 0.0;         // dB
        this.obstaculos      = new ArrayList<>();
        this.walls = new ArrayList<>();
    }

    public Environment(double freqMHz, double bandwidthHz, double noiseFigureDb) {
        this();
        this.freqMHz = freqMHz;
        this.bandwidthHz = bandwidthHz;
        this.noiseFigureDb = noiseFigureDb;
    }

    /* ==========================
     *  Utilidades físicas
     * ========================== */

    /**
     * Densidad de ruido térmico integrada + NF en dBm.
     * N = -174 dBm/Hz + 10·log10(BW) + NF
     */
    public double noiseFloorDbm() {
        return -174.0 + 10.0 * Math.log10(bandwidthHz) + noiseFigureDb;
    }

    /**
     * Longitud de onda (m): λ = c / f
     * con f en Hz (MHz * 1e6)
     */
    public double wavelengthMeters() {
        final double freqHz = this.freqMHz * 1e6;
        return SPEED_OF_LIGHT_MS / freqHz;
    }

    /**
     * Conversor MHz → Hz (comodidad)
     */
    public double getFreqHz() {
        return this.freqMHz * 1e6;
    }
    
    public static final List<Wall> WALLS_TEMPLATE_1_MAT = java.util.Arrays.asList(
    	    new Wall(0, 0, 0, 24, CONCRETE, 15),  // hormigón 15 cm
    	    new Wall(40, 0, 40, 24, CONCRETE, 15),
    	    new Wall(0, 24, 40, 24, BRICK, 12),
    	    new Wall(0, 0, 40, 0, BRICK, 12)
    	);

    	public static final List<Wall> WALLS_TEMPLATE_2_MAT = java.util.Arrays.asList(
    	    new Wall(5, 0, 5, 12, BRICK, 10),
    	    new Wall(15, 0, 15, 12, BRICK, 10),
    	    new Wall(5, 0, 15, 0, DRYWALL, 10),
    	    new Wall(5, 12, 15, 12, DRYWALL, 10)
    	);

    	public static final List<Wall> WALLS_TEMPLATE_HOUSE_MAT = java.util.Arrays.asList(
    	    // Pasillo horizontal
    	    new Wall(0, 5, 20, 5, DRYWALL, 10),
    	    new Wall(0, 7, 20, 7, DRYWALL, 10),
    	    new Wall(0, 5, 0, 7, METAL_DOOR, 5),     // puerta metálica
    	    // Pasillo vertical
    	    new Wall(20, 5, 20, 18, BRICK, 12),
    	    new Wall(22, 5, 22, 18, BRICK, 12),
    	    new Wall(20, 18, 22, 18, GLASS, 8)
    	);
    
    
        
        
     // Obstáculos → Paredes
        public List<Wall> getWalls() { return Collections.unmodifiableList(walls); }
        public void clearWalls() { walls.clear(); }
        public void addWall(Wall w) { if (w != null) walls.add(w); }
        public void addWalls(List<Wall> list) { if (list != null) walls.addAll(list); }
        public void setWalls(List<Wall> list) {
            walls.clear();
            if (list != null) walls.addAll(list);
        }
        
        

    /* ==========================
     *  Getters / Setters
     * ========================== */

    public double getAlphaDbPerMeter() {
        return alphaDbPerMeter;
    }

    public void setAlphaDbPerMeter(double alphaDbPerMeter) {
        this.alphaDbPerMeter = Math.max(0.0, alphaDbPerMeter);
    }

    public double getFreqMHz() {
        return freqMHz;
    }

    public void setFreqMHz(double freqMHz) {
        if (freqMHz <= 0) throw new IllegalArgumentException("freqMHz debe ser > 0");
        this.freqMHz = freqMHz;
    }

    public double getBandwidthHz() {
        return bandwidthHz;
    }

    public void setBandwidthHz(double bandwidthHz) {
        if (bandwidthHz <= 0) throw new IllegalArgumentException("bandwidthHz debe ser > 0");
        this.bandwidthHz = bandwidthHz;
    }

    public double getNoiseFigureDb() {
        return noiseFigureDb;
    }

    public void setNoiseFigureDb(double noiseFigureDb) {
        this.noiseFigureDb = Math.max(0.0, noiseFigureDb);
    }

    public double getSystemGainDb() {
        return systemGainDb;
    }

    public void setSystemGainDb(double systemGainDb) {
        this.systemGainDb = systemGainDb;
    }

    /* ==========================
     *  Obstáculos
     * ========================== */

    public List<Obstacle> getObstaculos() {
        return Collections.unmodifiableList(obstaculos);
    }

    public void clearObstaculos() {
        obstaculos.clear();
    }

    public void addObstaculo(Obstacle o) {
        if (o != null) obstaculos.add(o);
    }

    public void addObstaculos(List<Obstacle> lista) {
        if (lista != null) obstaculos.addAll(lista);
    }

    public void setObstaculos(List<Obstacle> lista) {
        obstaculos.clear();
        if (lista != null) obstaculos.addAll(lista);
    }

    /* ==========================
     *  Helpers de formato (opcional)
     * ========================== */

    @Override
    public String toString() {
        return "Environment{" +
                "freqMHz=" + freqMHz +
                ", bandwidthHz=" + bandwidthHz +
                ", noiseFigureDb=" + noiseFigureDb +
                ", alphaDbPerMeter=" + alphaDbPerMeter +
                ", systemGainDb=" + systemGainDb +
                ", obstaculos=" + obstaculos.size() +
                '}';
    }
}

