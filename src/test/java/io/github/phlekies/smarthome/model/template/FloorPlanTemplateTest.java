package io.github.phlekies.smarthome.model.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Wall;

class FloorPlanTemplateTest {

    @ParameterizedTest
    @EnumSource(FloorPlanTemplate.class)
    void everyTemplateFitsInsideThePlan(FloorPlanTemplate template) {
        List<Wall> walls = template.walls();
        assertFalse(walls.isEmpty());
        for (Wall wall : walls) {
            for (double x : new double[] { wall.getX1(), wall.getX2() }) {
                assertTrue(x >= 0 && x <= SimulatorModel.PLAN_WIDTH_METERS, template + " x out of bounds: " + x);
            }
            for (double y : new double[] { wall.getY1(), wall.getY2() }) {
                assertTrue(y >= 0 && y <= SimulatorModel.PLAN_HEIGHT_METERS, template + " y out of bounds: " + y);
            }
            assertTrue(wall.length() > 0, template + " has a zero-length wall");
        }
    }

    @ParameterizedTest
    @EnumSource(FloorPlanTemplate.class)
    void everyTemplateIsDocumented(FloorPlanTemplate template) {
        assertFalse(template.displayName().isBlank());
        assertTrue(template.description().length() > 20);
    }

    @Test
    void namesAreUnique() {
        long distinct = Arrays.stream(FloorPlanTemplate.values()).map(FloorPlanTemplate::displayName).distinct().count();
        assertEquals(FloorPlanTemplate.values().length, distinct);
    }

    @Test
    void wallsAreFreshInstancesEveryTime() {
        Wall first = FloorPlanTemplate.HOTEL_FLOOR.walls().getFirst();
        Wall second = FloorPlanTemplate.HOTEL_FLOOR.walls().getFirst();
        assertNotSame(first, second);
        assertTrue(first.isEquivalentTo(second));
    }

    @Test
    void templatesAreScaledToRealisticSizes() {
        // The apartment is drawn 31 units wide and scaled by 1.3 → 40.3 m.
        Wall bottom = FloorPlanTemplate.TWO_BEDROOM_APARTMENT.walls().getFirst();
        assertEquals(40.3, bottom.length(), 1e-9);
    }
}
