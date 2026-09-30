package io.github.phlekies.smarthome.app;

import java.util.List;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;
import io.github.phlekies.smarthome.simulation.SimulationSettings;

/**
 * Everything that makes up a project, detached from the live model: the walls live inside the
 * environment. Used to save, load and reset the simulator.
 *
 * @param template    the template the plan came from, or {@code null} for a custom plan
 * @param environment radio parameters and walls
 * @param settings    engine settings
 * @param sensors     the transmitting sensors
 * @param hub         the hub, or {@code null} if it has not been placed
 */
public record ProjectState(FloorPlanTemplate template, Environment environment, SimulationSettings settings,
                           List<Sensor> sensors, Hub hub) {

    public ProjectState {
        sensors = List.copyOf(sensors);
    }
}
