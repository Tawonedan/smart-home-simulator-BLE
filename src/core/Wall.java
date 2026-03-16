package core;

import core.material.Material;

public class Wall {
    private double x1, y1, x2, y2;     // segmento (mismo sistema que Obstacle)
    private Material material;
    private double thicknessCm;        // grosor (opcional; 0 si no usado)

    public Wall(double x1, double y1, double x2, double y2, Material mat) {
        this(x1, y1, x2, y2, mat, 0.0);
    }
    public Wall(double x1, double y1, double x2, double y2, Material mat, double thicknessCm) {
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        this.material = mat; this.thicknessCm = Math.max(0.0, thicknessCm);
    }

    public double getX1(){ return x1; } public double getY1(){ return y1; }
    public double getX2(){ return x2; } public double getY2(){ return y2; }

    public void set(double nx1,double ny1,double nx2,double ny2){
        this.x1=nx1; this.y1=ny1; this.x2=nx2; this.y2=ny2;
    }

    public Material getMaterial(){ return material; }
    public void setMaterial(Material m){ this.material = m; }
    public double getThicknessCm(){ return thicknessCm; }
    public void setThicknessCm(double t){ this.thicknessCm = Math.max(0.0,t); }

    /** Pérdida por cruce a una frecuencia (MHz). */
    public double lossDb(double freqMHz){
        return (material == null) ? 0.0 : material.lossDb(freqMHz, thicknessCm);
    }

    public Wall copy() {
        return new Wall(x1, y1, x2, y2, material, thicknessCm);
    }
}
