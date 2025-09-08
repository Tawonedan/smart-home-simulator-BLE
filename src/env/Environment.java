package env;

import java.util.Arrays;
import java.util.List;

import core.Obstacle;

/** 
 * Define los parámetros globales de propagación y entorno.
 * Más adelante aquí meterás paredes, materiales, rebotes, etc.
 */
public class Environment {
    // Pérdida por metro (dB/m) en el aire/interior
    private double alphaDbPerMeter;

    // Frecuencia central (MHz) – útil si luego aplicas FSPL
    private double freqMHz;
    
    public static final double WIFI_24_GHZ_MHZ = 2400.0;
    public static final double WIFI_5_GHZ_MHZ  = 5150.0;

    // Constructor con valores por defecto (ejemplo: IoT indoor)
    public Environment() {
        this.alphaDbPerMeter = 0.5;  // dB por metro
        this.freqMHz = WIFI_24_GHZ_MHZ;        // 2.4 GHz típico Wi-Fi/Zigbee
    }

    // Constructor parametrizable
    public Environment(double alphaDbPerMeter, double freqMHz) {
        this.alphaDbPerMeter = alphaDbPerMeter;
        this.freqMHz = freqMHz;
    }
    
    //Template_1 genera un rectangulo del tamaño de la plantilla
    public static final List<Obstacle> WALLS_TEMPLATE_1 = Arrays.asList(
            new Obstacle(0, 0, 0, 24),   // pared izquierda
            new Obstacle(40, 0, 40, 24),  //pared 
            new Obstacle(0, 24, 40, 24),
            new Obstacle(0, 0, 40, 0)
        );

        // Plantilla 2: una habitación cerrada
        public static final List<Obstacle> WALLS_TEMPLATE_2 = Arrays.asList(
            new Obstacle(5, 0, 5, 12),    // izquierda
            new Obstacle(15, 0, 15, 12),  // derecha
            new Obstacle(5, 0, 15, 0),    // abajo
            new Obstacle(5, 12, 15, 12)   // arriba
        );
        
        public static final List<Obstacle> WALLS_TEMPLATE_HOUSE = Arrays.asList(
        	    // Pasillo horizontal (y = 5, de x=0 a x=20, ancho=2)
        	    new Obstacle(0, 5, 20, 5),   // pared inferior
        	    new Obstacle(0, 7, 20, 7),   // pared superior

        	    // Cierre izquierda del pasillo horizontal
        	    new Obstacle(0, 5, 0, 7),

        	    // Pasillo vertical (x = 20, de y=5 a y=18, ancho=2)
        	    new Obstacle(20, 5, 20, 18), // pared izquierda
        	    new Obstacle(22, 5, 22, 18), // pared derecha

        	    // Cierre superior del pasillo vertical
        	    new Obstacle(20, 18, 22, 18)
        	);

        
        

    public double getAlphaDbPerMeter() {
        return alphaDbPerMeter;
    }

    public void setAlphaDbPerMeter(double alphaDbPerMeter) {
        this.alphaDbPerMeter = alphaDbPerMeter;
    }

    public double getFreqMHz() {
        return freqMHz;
    }

    public void setFreqMHz(double freqMHz) {
        this.freqMHz = freqMHz;
    }
}
