package core.material;

public final class MaterialsDB {
    private MaterialsDB(){}

    // Presets típicos indoor (aprox; ajusta a tu gusto/proyecto)
    public static final Material DRYWALL      = new Material("Tabique (pladur)",
            3.0, 5.0, 0.0);
    public static final Material BRICK        = new Material("Ladrillo",
            6.0, 9.0, 0.1);   // +0.1 dB por cm
    public static final Material CONCRETE     = new Material("Hormigón",
            10.0, 15.0, 0.2);
    public static final Material GLASS        = new Material("Cristal",
            2.0, 3.0, 0.0);
    public static final Material WOOD         = new Material("Madera",
            3.0, 5.0, 0.05);
    public static final Material METAL_DOOR   = new Material("Puerta metálica",
            18.0, 25.0, 0.0);

    public static Material byName(String n){
        if (n == null) return DRYWALL;
        String s = n.toLowerCase();
        if (s.contains("horm")) return CONCRETE;
        if (s.contains("ladr")) return BRICK;
        if (s.contains("crist")) return GLASS;
        if (s.contains("made")) return WOOD;
        if (s.contains("metal") || s.contains("puerta")) return METAL_DOOR;
        return DRYWALL;
    }
}
