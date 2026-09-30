package io.github.phlekies.smarthome.model.template;

import static io.github.phlekies.smarthome.model.material.Materials.BRICK;
import static io.github.phlekies.smarthome.model.material.Materials.CONCRETE;
import static io.github.phlekies.smarthome.model.material.Materials.DRYWALL;
import static io.github.phlekies.smarthome.model.material.Materials.GLASS;
import static io.github.phlekies.smarthome.model.material.Materials.METAL_DOOR;
import static io.github.phlekies.smarthome.model.material.Materials.WOOD;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Material;

/** Built-in indoor floor plans. Coordinates are designed on a small grid and scaled to metres. */
public enum FloorPlanTemplate {

    COMPACT_STUDIO("Compact studio",
            "Small home with an open living area, bathroom and a partially separated bedroom.",
            plan -> {
                plan.rect(2, 2, 24, 18, CONCRETE, 15);
                plan.wall(18, 9, 18, 18, DRYWALL, 8);
                plan.wall(18, 9, 24, 9, DRYWALL, 8);
                plan.wall(10, 12, 18, 12, WOOD, 6);
                plan.wall(10, 12, 10, 18, WOOD, 6);
                plan.wall(5, 8, 15, 8, WOOD, 5);
                plan.wall(7, 2, 7, 8, WOOD, 5);
            }),

    TWO_BEDROOM_APARTMENT("Two-bedroom apartment",
            "City apartment with living room, kitchen, two bedrooms and a central bathroom core.",
            plan -> {
                plan.rect(1, 1, 32, 22, BRICK, 12);
                plan.wall(12, 1, 12, 15, DRYWALL, 8);
                plan.wall(22, 7, 22, 22, DRYWALL, 8);
                plan.wall(1, 15, 12, 15, DRYWALL, 8);
                plan.wall(12, 7, 22, 7, DRYWALL, 8);
                plan.wall(12, 15, 22, 15, DRYWALL, 8);
                plan.wall(17, 7, 17, 15, WOOD, 6);
                plan.wall(26, 1, 26, 7, WOOD, 6);
            }),

    HOUSE_WITH_CORRIDOR("House with corridor",
            "Rectangular family house with a main corridor and rooms on both sides.",
            plan -> {
                plan.rect(1, 1, 33, 27, CONCRETE, 16);
                plan.wall(1, 14, 33, 14, BRICK, 10);
                plan.wall(17, 14, 17, 27, BRICK, 10);
                plan.wall(9, 14, 9, 27, DRYWALL, 8);
                plan.wall(25, 14, 25, 27, DRYWALL, 8);
                plan.wall(11, 1, 11, 14, DRYWALL, 8);
                plan.wall(23, 1, 23, 14, DRYWALL, 8);
                plan.wall(11, 8, 23, 8, WOOD, 6);
            }),

    L_SHAPED_HOUSE("L-shaped house",
            "L-shaped plan with a social wing and a private wing; good for testing corners and shadow zones.",
            plan -> {
                plan.wall(2, 2, 27, 2, CONCRETE, 16);
                plan.wall(27, 2, 27, 12, CONCRETE, 16);
                plan.wall(27, 12, 33, 12, CONCRETE, 16);
                plan.wall(33, 12, 33, 28, CONCRETE, 16);
                plan.wall(12, 28, 33, 28, CONCRETE, 16);
                plan.wall(12, 22, 12, 28, CONCRETE, 16);
                plan.wall(2, 22, 12, 22, CONCRETE, 16);
                plan.wall(2, 2, 2, 22, CONCRETE, 16);
                plan.wall(12, 12, 27, 12, DRYWALL, 8);
                plan.wall(18, 2, 18, 12, DRYWALL, 8);
                plan.wall(8, 2, 8, 22, WOOD, 6);
                plan.wall(12, 18, 33, 18, DRYWALL, 8);
                plan.wall(22, 18, 22, 28, DRYWALL, 8);
            }),

    HOTEL_FLOOR("Hotel floor",
            "Central corridor with repeated rooms, useful to analyse linear coverage.",
            plan -> {
                plan.rect(1, 1, 33, 29, CONCRETE, 18);
                plan.wall(1, 12, 33, 12, DRYWALL, 8);
                plan.wall(1, 18, 33, 18, DRYWALL, 8);
                for (double x : new double[] { 7.0, 13.0, 19.0, 25.0 }) {
                    plan.wall(x, 18, x, 29, DRYWALL, 8);
                    plan.wall(x, 1, x, 12, DRYWALL, 8);
                }
                plan.wall(27, 18, 27, 29, GLASS, 4);
                plan.wall(27, 1, 27, 12, GLASS, 4);
            }),

    HOTEL_SUITE("Hotel suite",
            "Large suite with living room, bedroom, dressing room and bathroom mixing glass and drywall.",
            plan -> {
                plan.rect(3, 3, 31, 24, BRICK, 12);
                plan.wall(17, 3, 17, 24, DRYWALL, 8);
                plan.wall(17, 14, 31, 14, DRYWALL, 8);
                plan.wall(23, 14, 23, 24, GLASS, 5);
                plan.wall(8, 10, 17, 10, WOOD, 6);
                plan.wall(8, 3, 8, 10, WOOD, 6);
            }),

    OPEN_PLAN_OFFICE("Open-plan office",
            "Open space with glass meeting rooms and a service core on one side.",
            plan -> {
                plan.rect(1, 1, 34, 24, CONCRETE, 15);
                plan.rect(3, 15, 10, 23, GLASS, 4);
                plan.rect(12, 15, 19, 23, GLASS, 4);
                plan.rect(21, 15, 28, 23, GLASS, 4);
                plan.rect(29, 4, 33, 14, DRYWALL, 8);
                plan.wall(3, 10, 28, 10, WOOD, 5);
            }),

    CELLULAR_OFFICE("Cellular office",
            "Small offices on both sides of a central corridor, useful to compare partition losses.",
            plan -> {
                plan.rect(1, 1, 34, 26, CONCRETE, 15);
                plan.wall(15, 1, 15, 26, DRYWALL, 8);
                plan.wall(19, 1, 19, 26, DRYWALL, 8);
                for (double y : new double[] { 6.0, 11.0, 16.0, 21.0 }) {
                    plan.wall(1, y, 15, y, DRYWALL, 8);
                    plan.wall(19, y, 34, y, DRYWALL, 8);
                }
                plan.rect(15, 20, 19, 26, GLASS, 4);
            }),

    CLINIC_WING("Clinic wing",
            "Clinic wing with treatment bays and consulting rooms around a central corridor.",
            plan -> {
                plan.rect(1, 1, 33, 27, CONCRETE, 16);
                plan.wall(1, 13, 33, 13, DRYWALL, 8);
                plan.wall(1, 17, 33, 17, DRYWALL, 8);
                plan.wall(9, 1, 9, 13, DRYWALL, 8);
                plan.wall(17, 1, 17, 13, DRYWALL, 8);
                plan.wall(25, 1, 25, 13, DRYWALL, 8);
                plan.wall(9, 17, 9, 27, DRYWALL, 8);
                plan.wall(17, 17, 17, 27, DRYWALL, 8);
                plan.wall(25, 17, 25, 27, DRYWALL, 8);
                plan.rect(13, 13, 21, 17, GLASS, 4);
            }),

    CLASSROOM_AND_LAB("Classroom and lab",
            "Training centre with a classroom, a laboratory and a technical storeroom.",
            plan -> {
                plan.rect(2, 2, 32, 24, BRICK, 12);
                plan.wall(2, 14, 32, 14, DRYWALL, 8);
                plan.wall(10, 2, 10, 14, DRYWALL, 8);
                plan.wall(22, 14, 22, 24, DRYWALL, 8);
                plan.wall(26, 14, 26, 24, WOOD, 6);
                plan.wall(10, 8, 22, 8, GLASS, 4);
            }),

    WAREHOUSE_WITH_AISLES("Warehouse with aisles",
            "Warehouse with parallel metal shelving and an office block at one end.",
            plan -> {
                plan.rect(1, 1, 34, 29, CONCRETE, 20);
                plan.rect(2, 2, 9, 8, DRYWALL, 8);
                for (double x : new double[] { 10.0, 14.0, 18.0, 22.0, 26.0, 30.0 }) {
                    plan.wall(x, 5, x, 13, METAL_DOOR, 4);
                    plan.wall(x, 17, x, 26, METAL_DOOR, 4);
                }
                plan.wall(9, 10, 34, 10, WOOD, 5);
            }),

    FACTORY_HALL("Factory hall",
            "Industrial hall with machinery cells, technical rooms and a wide internal route.",
            plan -> {
                plan.rect(1, 1, 34, 29, CONCRETE, 20);
                plan.rect(2, 2, 9, 10, BRICK, 12);
                plan.rect(2, 12, 9, 20, BRICK, 12);
                plan.rect(12, 4, 18, 11, METAL_DOOR, 4);
                plan.rect(21, 4, 28, 11, METAL_DOOR, 4);
                plan.rect(12, 16, 18, 24, METAL_DOOR, 4);
                plan.rect(21, 16, 28, 24, METAL_DOOR, 4);
                plan.wall(9, 14, 34, 14, WOOD, 5);
            });

    /** Templates are drawn on a compact grid and enlarged to realistic room sizes. */
    private static final double SCALE = 1.30;

    private final String displayName;
    private final String description;
    private final Consumer<PlanBuilder> layout;

    FloorPlanTemplate(String displayName, String description, Consumer<PlanBuilder> layout) {
        this.displayName = displayName;
        this.description = description;
        this.layout = layout;
    }

    /** The template loaded in a new project. */
    public static FloorPlanTemplate defaultTemplate() {
        return TWO_BEDROOM_APARTMENT;
    }

    /** Name shown in the UI. */
    public String displayName() {
        return displayName;
    }

    /** One-sentence description of the layout. */
    public String description() {
        return description;
    }

    /** A fresh list of new wall instances, in metres. */
    public List<Wall> walls() {
        PlanBuilder builder = new PlanBuilder();
        layout.accept(builder);
        return builder.walls.stream().map(wall -> wall.scaled(SCALE)).toList();
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static final class PlanBuilder {
        private final List<Wall> walls = new ArrayList<>();

        void wall(double x1, double y1, double x2, double y2, Material material, double thicknessCm) {
            walls.add(new Wall(x1, y1, x2, y2, material, thicknessCm));
        }

        void rect(double x1, double y1, double x2, double y2, Material material, double thicknessCm) {
            wall(x1, y1, x2, y1, material, thicknessCm);
            wall(x2, y1, x2, y2, material, thicknessCm);
            wall(x2, y2, x1, y2, material, thicknessCm);
            wall(x1, y2, x1, y1, material, thicknessCm);
        }
    }
}
