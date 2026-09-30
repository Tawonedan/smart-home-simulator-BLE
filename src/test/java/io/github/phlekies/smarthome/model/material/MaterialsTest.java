package io.github.phlekies.smarthome.model.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaterialsTest {

    @Test
    void penetrationLossDependsOnTheBand() {
        assertEquals(6.0, Materials.BRICK.transmissionLossDb(2400, 0), 1e-12);
        assertEquals(9.0, Materials.BRICK.transmissionLossDb(5200, 0), 1e-12);
    }

    @Test
    void thickerWallsAttenuateMore() {
        // Brick adds 0.1 dB per centimetre.
        assertEquals(6.0 + 1.2, Materials.BRICK.transmissionLossDb(2400, 12), 1e-12);
        assertEquals(Materials.GLASS.transmissionLossDb(2400, 1), Materials.GLASS.transmissionLossDb(2400, 20));
    }

    @Test
    void denserMaterialsBlockMore() {
        assertTrue(Materials.METAL_DOOR.transmissionLossDb(2400, 4) > Materials.CONCRETE.transmissionLossDb(2400, 4));
        assertTrue(Materials.CONCRETE.transmissionLossDb(2400, 4) > Materials.BRICK.transmissionLossDb(2400, 4));
        assertTrue(Materials.BRICK.transmissionLossDb(2400, 4) > Materials.GLASS.transmissionLossDb(2400, 4));
    }

    @Test
    void coefficientsAreConsistentWithTheLosses() {
        for (Material material : Materials.all()) {
            double loss = material.transmissionLossDb(2400, 10);
            assertEquals(Math.pow(10, -loss / 20), material.transmissionCoefficient(2400, 10), 1e-12);
            double reflection = material.reflectionCoefficient(2400, 30);
            assertTrue(reflection > 0 && reflection < 1, material + " reflection coefficient out of range");
        }
    }

    @Test
    void grazingIncidenceReflectsMoreThanNormalIncidence() {
        for (Material material : Materials.all()) {
            assertTrue(material.reflectionLossDb(2400, 80) <= material.reflectionLossDb(2400, 0), material.getName());
        }
    }

    @Test
    void metalIsTheBestReflector() {
        for (Material material : Materials.all()) {
            assertTrue(Materials.METAL_DOOR.reflectionLossDb(2400, 30) <= material.reflectionLossDb(2400, 30));
        }
    }

    @Test
    void interactionBundlesAllLosses() {
        WallInteraction interaction = Materials.interact(Materials.CONCRETE, 2400, 15, 20);
        assertEquals(Materials.CONCRETE.transmissionLossDb(2400, 15), interaction.transmissionLossDb(), 1e-12);
        assertEquals(Materials.CONCRETE.reflectionLossDb(2400, 20), interaction.reflectionLossDb(), 1e-12);
        assertEquals(Materials.CONCRETE.scatteringLossDb(2400, 20), interaction.scatteringLossDb(), 1e-12);
        assertTrue(interaction.reflects());
    }

    @Test
    void missingMaterialFallsBackToDrywall() {
        WallInteraction interaction = Materials.interact(null, 2400, 8, 0);
        assertEquals(Materials.DRYWALL.transmissionLossDb(2400, 8), interaction.transmissionLossDb(), 1e-12);
    }
}
