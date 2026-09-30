package env;

import java.util.ArrayList;
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
    
    	public static final String TEMPLATE_STUDIO_COMPACT = "Estudio compacto";
    	public static final String TEMPLATE_APARTMENT_TWO_BEDROOM = "Apartamento 2 dormitorios";
    	public static final String TEMPLATE_HOUSE_WITH_CORRIDOR = "Casa con pasillo";
    	public static final String TEMPLATE_HOUSE_L_SHAPED = "Casa en L";
    	public static final String TEMPLATE_HOTEL_FLOOR = "Planta hotel";
    	public static final String TEMPLATE_HOTEL_SUITE = "Suite de hotel";
    	public static final String TEMPLATE_OFFICE_OPEN = "Oficina abierta";
    	public static final String TEMPLATE_OFFICE_CELLULAR = "Oficina compartimentada";
    	public static final String TEMPLATE_CLINIC_WING = "Clinica";
    	public static final String TEMPLATE_CLASSROOM_LAB = "Aula y laboratorio";
    	public static final String TEMPLATE_WAREHOUSE_AISLES = "Almacen con pasillos";
    	public static final String TEMPLATE_FACTORY_HALL = "Nave industrial";
    	private static final double TEMPLATE_SCALE = 1.30;

    	public static List<String> builtInTemplateNames() {
    		return List.of(
    				TEMPLATE_STUDIO_COMPACT,
    				TEMPLATE_APARTMENT_TWO_BEDROOM,
    				TEMPLATE_HOUSE_WITH_CORRIDOR,
    				TEMPLATE_HOUSE_L_SHAPED,
    				TEMPLATE_HOTEL_FLOOR,
    				TEMPLATE_HOTEL_SUITE,
    				TEMPLATE_OFFICE_OPEN,
    				TEMPLATE_OFFICE_CELLULAR,
    				TEMPLATE_CLINIC_WING,
    				TEMPLATE_CLASSROOM_LAB,
    				TEMPLATE_WAREHOUSE_AISLES,
    				TEMPLATE_FACTORY_HALL
    		);
    	}

    	public static String defaultTemplateName() {
    		return TEMPLATE_APARTMENT_TWO_BEDROOM;
    	}

    	public static String templateDescription(String templateName) {
    		if (templateName == null) return "Selecciona una plantilla para cargar un escenario interior.";
    		return switch (templateName) {
    			case TEMPLATE_STUDIO_COMPACT -> "Vivienda pequena con zona abierta, bano y dormitorio parcialmente separado.";
    			case TEMPLATE_APARTMENT_TWO_BEDROOM -> "Piso urbano con salon, cocina, dos dormitorios y nucleo de bano central.";
    			case TEMPLATE_HOUSE_WITH_CORRIDOR -> "Vivienda familiar rectangular con pasillo principal y habitaciones a ambos lados.";
    			case TEMPLATE_HOUSE_L_SHAPED -> "Planta en L con ala social y ala privada, util para probar giros y zonas de sombra.";
    			case TEMPLATE_HOTEL_FLOOR -> "Pasillo central con habitaciones repetidas, interesante para analizar cobertura lineal.";
    			case TEMPLATE_HOTEL_SUITE -> "Suite grande con salon, dormitorio, vestidor y bano, con mezcla de vidrio y tabique.";
    			case TEMPLATE_OFFICE_OPEN -> "Open space con salas de reunion acristaladas y nucleo de servicios lateral.";
    			case TEMPLATE_OFFICE_CELLULAR -> "Despachos pequenos a ambos lados de un corredor central, util para comparar perdidas por tabiques.";
    			case TEMPLATE_CLINIC_WING -> "Ala de clinica con boxes y consultas alrededor de un corredor central.";
    			case TEMPLATE_CLASSROOM_LAB -> "Centro formativo con aula, laboratorio y almacen tecnico.";
    			case TEMPLATE_WAREHOUSE_AISLES -> "Almacen con estanterias metalicas paralelas y bloque de oficinas en un extremo.";
    			case TEMPLATE_FACTORY_HALL -> "Nave con celdas de maquinaria, cuartos tecnicos y recorrido interior amplio.";
    			default -> "Escenario interior personalizado.";
    		};
    	}

    	public static List<Wall> wallsForTemplate(String templateName) {
    		List<Wall> template = (templateName == null) ? apartmentTwoBedroomTemplate() : switch (templateName) {
    			case TEMPLATE_STUDIO_COMPACT -> studioCompactTemplate();
    			case TEMPLATE_APARTMENT_TWO_BEDROOM -> apartmentTwoBedroomTemplate();
    			case TEMPLATE_HOUSE_WITH_CORRIDOR -> houseWithCorridorTemplate();
    			case TEMPLATE_HOUSE_L_SHAPED -> houseLShapedTemplate();
    			case TEMPLATE_HOTEL_FLOOR -> hotelFloorTemplate();
    			case TEMPLATE_HOTEL_SUITE -> hotelSuiteTemplate();
    			case TEMPLATE_OFFICE_OPEN -> officeOpenTemplate();
    			case TEMPLATE_OFFICE_CELLULAR -> officeCellularTemplate();
    			case TEMPLATE_CLINIC_WING -> clinicWingTemplate();
    			case TEMPLATE_CLASSROOM_LAB -> classroomLabTemplate();
    			case TEMPLATE_WAREHOUSE_AISLES -> warehouseAislesTemplate();
    			case TEMPLATE_FACTORY_HALL -> factoryHallTemplate();
    			default -> apartmentTwoBedroomTemplate();
    		};
    		return scaleTemplate(template, TEMPLATE_SCALE);
    	}

    	private static List<Wall> scaleTemplate(List<Wall> template, double scale) {
    		List<Wall> scaled = new ArrayList<>();
    		for (Wall wall : template) {
    			scaled.add(new Wall(
    					wall.getX1() * scale,
    					wall.getY1() * scale,
    					wall.getX2() * scale,
    					wall.getY2() * scale,
    					wall.getMaterial(),
    					wall.getThicknessCm()));
    		}
    		return scaled;
    	}

    	private static List<Wall> studioCompactTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 2, 2, 24, 18, CONCRETE, 15);
    		addWall(template, 18, 9, 18, 18, DRYWALL, 8);
    		addWall(template, 18, 9, 24, 9, DRYWALL, 8);
    		addWall(template, 10, 12, 18, 12, WOOD, 6);
    		addWall(template, 10, 12, 10, 18, WOOD, 6);
    		addWall(template, 5, 8, 15, 8, WOOD, 5);
    		addWall(template, 7, 2, 7, 8, WOOD, 5);
    		return template;
    	}

    	private static List<Wall> apartmentTwoBedroomTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 1, 1, 32, 22, BRICK, 12);
    		addWall(template, 12, 1, 12, 15, DRYWALL, 8);
    		addWall(template, 22, 7, 22, 22, DRYWALL, 8);
    		addWall(template, 1, 15, 12, 15, DRYWALL, 8);
    		addWall(template, 12, 7, 22, 7, DRYWALL, 8);
    		addWall(template, 12, 15, 22, 15, DRYWALL, 8);
    		addWall(template, 17, 7, 17, 15, WOOD, 6);
    		addWall(template, 26, 1, 26, 7, WOOD, 6);
    		return template;
    	}

    	private static List<Wall> houseWithCorridorTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 1, 1, 33, 27, CONCRETE, 16);
    		addWall(template, 1, 14, 33, 14, BRICK, 10);
    		addWall(template, 17, 14, 17, 27, BRICK, 10);
    		addWall(template, 9, 14, 9, 27, DRYWALL, 8);
    		addWall(template, 25, 14, 25, 27, DRYWALL, 8);
    		addWall(template, 11, 1, 11, 14, DRYWALL, 8);
    		addWall(template, 23, 1, 23, 14, DRYWALL, 8);
    		addWall(template, 11, 8, 23, 8, WOOD, 6);
    		return template;
    	}

    	private static List<Wall> houseLShapedTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addWall(template, 2, 2, 27, 2, CONCRETE, 16);
    		addWall(template, 27, 2, 27, 12, CONCRETE, 16);
    		addWall(template, 27, 12, 33, 12, CONCRETE, 16);
    		addWall(template, 33, 12, 33, 28, CONCRETE, 16);
    		addWall(template, 12, 28, 33, 28, CONCRETE, 16);
    		addWall(template, 12, 22, 12, 28, CONCRETE, 16);
    		addWall(template, 2, 22, 12, 22, CONCRETE, 16);
    		addWall(template, 2, 2, 2, 22, CONCRETE, 16);
    		addWall(template, 12, 12, 27, 12, DRYWALL, 8);
    		addWall(template, 18, 2, 18, 12, DRYWALL, 8);
    		addWall(template, 8, 2, 8, 22, WOOD, 6);
    		addWall(template, 12, 18, 33, 18, DRYWALL, 8);
    		addWall(template, 22, 18, 22, 28, DRYWALL, 8);
    		return template;
    	}

    	private static List<Wall> hotelFloorTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 1, 1, 33, 29, CONCRETE, 18);
    		addWall(template, 1, 12, 33, 12, DRYWALL, 8);
    		addWall(template, 1, 18, 33, 18, DRYWALL, 8);
    		for (double x : new double[] { 7.0, 13.0, 19.0, 25.0 }) {
    			addWall(template, x, 18, x, 29, DRYWALL, 8);
    			addWall(template, x, 1, x, 12, DRYWALL, 8);
    		}
    		addWall(template, 27, 18, 27, 29, GLASS, 4);
    		addWall(template, 27, 1, 27, 12, GLASS, 4);
    		return template;
    	}

    	private static List<Wall> hotelSuiteTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 3, 3, 31, 24, BRICK, 12);
    		addWall(template, 17, 3, 17, 24, DRYWALL, 8);
    		addWall(template, 17, 14, 31, 14, DRYWALL, 8);
    		addWall(template, 23, 14, 23, 24, GLASS, 5);
    		addWall(template, 8, 10, 17, 10, WOOD, 6);
    		addWall(template, 8, 3, 8, 10, WOOD, 6);
    		return template;
    	}

    	private static List<Wall> officeOpenTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 1, 1, 34, 24, CONCRETE, 15);
    		addRect(template, 3, 15, 10, 23, GLASS, 4);
    		addRect(template, 12, 15, 19, 23, GLASS, 4);
    		addRect(template, 21, 15, 28, 23, GLASS, 4);
    		addRect(template, 29, 4, 33, 14, DRYWALL, 8);
    		addWall(template, 3, 10, 28, 10, WOOD, 5);
    		return template;
    	}

    	private static List<Wall> officeCellularTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 1, 1, 34, 26, CONCRETE, 15);
    		addWall(template, 15, 1, 15, 26, DRYWALL, 8);
    		addWall(template, 19, 1, 19, 26, DRYWALL, 8);
    		for (double y : new double[] { 6.0, 11.0, 16.0, 21.0 }) {
    			addWall(template, 1, y, 15, y, DRYWALL, 8);
    			addWall(template, 19, y, 34, y, DRYWALL, 8);
    		}
    		addRect(template, 15, 20, 19, 26, GLASS, 4);
    		return template;
    	}

    	private static List<Wall> clinicWingTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 1, 1, 33, 27, CONCRETE, 16);
    		addWall(template, 1, 13, 33, 13, DRYWALL, 8);
    		addWall(template, 1, 17, 33, 17, DRYWALL, 8);
    		addWall(template, 9, 1, 9, 13, DRYWALL, 8);
    		addWall(template, 17, 1, 17, 13, DRYWALL, 8);
    		addWall(template, 25, 1, 25, 13, DRYWALL, 8);
    		addWall(template, 9, 17, 9, 27, DRYWALL, 8);
    		addWall(template, 17, 17, 17, 27, DRYWALL, 8);
    		addWall(template, 25, 17, 25, 27, DRYWALL, 8);
    		addRect(template, 13, 13, 21, 17, GLASS, 4);
    		return template;
    	}

    	private static List<Wall> classroomLabTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 2, 2, 32, 24, BRICK, 12);
    		addWall(template, 2, 14, 32, 14, DRYWALL, 8);
    		addWall(template, 10, 2, 10, 14, DRYWALL, 8);
    		addWall(template, 22, 14, 22, 24, DRYWALL, 8);
    		addWall(template, 26, 14, 26, 24, WOOD, 6);
    		addWall(template, 10, 8, 22, 8, GLASS, 4);
    		return template;
    	}

    	private static List<Wall> warehouseAislesTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 1, 1, 34, 29, CONCRETE, 20);
    		addRect(template, 2, 2, 9, 8, DRYWALL, 8);
    		for (double x : new double[] { 10.0, 14.0, 18.0, 22.0, 26.0, 30.0 }) {
    			addWall(template, x, 5, x, 13, METAL_DOOR, 4);
    			addWall(template, x, 17, x, 26, METAL_DOOR, 4);
    		}
    		addWall(template, 9, 10, 34, 10, WOOD, 5);
    		return template;
    	}

    	private static List<Wall> factoryHallTemplate() {
    		List<Wall> template = new ArrayList<>();
    		addRect(template, 1, 1, 34, 29, CONCRETE, 20);
    		addRect(template, 2, 2, 9, 10, BRICK, 12);
    		addRect(template, 2, 12, 9, 20, BRICK, 12);
    		addRect(template, 12, 4, 18, 11, METAL_DOOR, 4);
    		addRect(template, 21, 4, 28, 11, METAL_DOOR, 4);
    		addRect(template, 12, 16, 18, 24, METAL_DOOR, 4);
    		addRect(template, 21, 16, 28, 24, METAL_DOOR, 4);
    		addWall(template, 9, 14, 34, 14, WOOD, 5);
    		return template;
    	}

    	private static void addRect(List<Wall> walls, double x1, double y1, double x2, double y2,
    			core.material.Material material, double thicknessCm) {
    		addWall(walls, x1, y1, x2, y1, material, thicknessCm);
    		addWall(walls, x2, y1, x2, y2, material, thicknessCm);
    		addWall(walls, x2, y2, x1, y2, material, thicknessCm);
    		addWall(walls, x1, y2, x1, y1, material, thicknessCm);
    	}

    	private static void addWall(List<Wall> walls, double x1, double y1, double x2, double y2,
    			core.material.Material material, double thicknessCm) {
    		walls.add(new Wall(x1, y1, x2, y2, material, thicknessCm));
    	}

     // Obstáculos → Paredes
        public List<Wall> getWalls() { return Collections.unmodifiableList(walls); }
        public void clearWalls() { walls.clear(); }
        public void addWall(Wall w) {
            if (w != null) walls.add(w.copy());
        }
        public void addWalls(List<Wall> list) {
            if (list == null) return;
            for (Wall wall : list) addWall(wall);
        }
        public void setWalls(List<Wall> list) {
            walls.clear();
            addWalls(list);
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
                ", walls=" + walls.size() +
                '}';
    }
}
