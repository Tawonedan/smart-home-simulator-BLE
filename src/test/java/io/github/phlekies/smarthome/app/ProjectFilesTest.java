package io.github.phlekies.smarthome.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.phlekies.smarthome.model.AntennaType;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.Wall;
import io.github.phlekies.smarthome.model.material.Materials;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;
import io.github.phlekies.smarthome.simulation.FadingModel;
import io.github.phlekies.smarthome.simulation.MapMetric;

class ProjectFilesTest {

    @Test
    void aProjectSurvivesARoundTrip() {
        ProjectState original = DemoScenario.smartApartment();
        original.settings().setFadingModel(FadingModel.RAYLEIGH);
        original.settings().setMapMetric(MapMetric.CAPACITY_MBPS);
        original.environment().setFreqMHz(5200);
        original.hub().setReceiverGainDb(3.5);

        ProjectState copy = ProjectFiles.fromJson(ProjectFiles.toJson(original));

        assertEquals(FloorPlanTemplate.TWO_BEDROOM_APARTMENT, copy.template());
        assertEquals(5200.0, copy.environment().getFreqMHz());
        assertEquals(FadingModel.RAYLEIGH, copy.settings().getFadingModel());
        assertEquals(MapMetric.CAPACITY_MBPS, copy.settings().getMapMetric());
        assertEquals(3.5, copy.hub().getReceiverGainDb());

        List<Wall> walls = copy.environment().getWalls();
        assertEquals(original.environment().getWalls().size(), walls.size());
        for (int i = 0; i < walls.size(); i++) {
            assertTrue(walls.get(i).isEquivalentTo(original.environment().getWalls().get(i)));
        }

        assertEquals(original.sensors().size(), copy.sensors().size());
        Sensor camera = copy.sensors().getLast();
        assertEquals("Security camera", camera.getName());
        assertEquals(AntennaType.DIRECTIONAL, camera.getAntennaType());
        assertEquals(225.0, camera.getOrientationDeg());
        assertEquals(10.0, camera.getTxPowerDbm());
    }

    @Test
    void theFileIsReadableJson() {
        String json = ProjectFiles.toJson(DemoScenario.smartApartment());
        assertTrue(json.contains("\"format\" : \"smart-home-simulator\""));
        assertTrue(json.contains("\"material\" : \"Brick\""));
        assertTrue(json.contains("\"name\" : \"Thermostat\""));
    }

    @Test
    void aProjectWithoutHubOrTemplateIsSupported() {
        ProjectState state = new ProjectState(null, DemoScenario.smartApartment().environment(),
                DemoScenario.smartApartment().settings(), List.of(), null);
        ProjectState copy = ProjectFiles.fromJson(ProjectFiles.toJson(state));
        assertNull(copy.hub());
        assertNull(copy.template());
        assertTrue(copy.sensors().isEmpty());
    }

    @Test
    void writesAndReadsFiles(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("office" + ProjectFiles.EXTENSION);
        ProjectFiles.write(DemoScenario.officeFloor(), file);
        assertEquals(7, ProjectFiles.read(file).sensors().size());
    }

    @Test
    void rejectsFilesThatAreNotProjects() {
        assertThrows(ProjectFiles.InvalidProjectException.class, () -> ProjectFiles.fromJson("not json"));
        assertThrows(ProjectFiles.InvalidProjectException.class, () -> ProjectFiles.fromJson("{\"hello\": 1}"));
    }

    @Test
    void rejectsNewerVersionsAndUnknownMaterials() {
        String json = ProjectFiles.toJson(DemoScenario.smartApartment());
        String newer = json.replace("\"version\" : 1", "\"version\" : 99");
        String unknownMaterial = json.replaceFirst("\"material\" : \"Brick\"", "\"material\" : \"Adamantium\"");

        var tooNew = assertThrows(ProjectFiles.InvalidProjectException.class, () -> ProjectFiles.fromJson(newer));
        assertTrue(tooNew.getMessage().contains("newer version"));
        var badMaterial = assertThrows(ProjectFiles.InvalidProjectException.class,
                () -> ProjectFiles.fromJson(unknownMaterial));
        assertTrue(badMaterial.getMessage().contains("Adamantium"));
    }

    @Test
    void materialsAreLookedUpByName() {
        assertSame(Materials.CONCRETE, Materials.byName("concrete").orElseThrow());
        assertTrue(Materials.byName("cheese").isEmpty());
    }

    @Test
    void theExampleProjectsInTheRepositoryLoad() throws IOException {
        Path examples = Path.of("examples");
        try (Stream<Path> files = Files.list(examples)) {
            List<Path> projects = files.filter(f -> f.toString().endsWith(ProjectFiles.EXTENSION)).toList();
            assertEquals(3, projects.size(), "expected the three example projects in " + examples.toAbsolutePath());
            for (Path project : projects) {
                ProjectState state = ProjectFiles.read(project);
                assertTrue(state.sensors().size() >= 5, project + " should have sensors");
                assertTrue(state.hub() != null, project + " should have a hub");
            }
        }
    }
}
