package io.github.phlekies.smarthome;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** Enforces the layering: physics ← model ← simulation ← app ← ui. */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.phlekies.smarthome");

    @Test
    void layersOnlyDependDownwards() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("UI").definedBy("..smarthome.ui..")
                .layer("App").definedBy("..smarthome.app..")
                .layer("Simulation").definedBy("..smarthome.simulation..")
                .layer("Model").definedBy("..smarthome.model..")
                .layer("Physics").definedBy("..smarthome.physics..")
                .whereLayer("UI").mayNotBeAccessedByAnyLayer()
                .whereLayer("App").mayOnlyBeAccessedByLayers("UI")
                .whereLayer("Simulation").mayOnlyBeAccessedByLayers("App", "UI")
                .whereLayer("Model").mayOnlyBeAccessedByLayers("Simulation", "App", "UI")
                .whereLayer("Physics").mayOnlyBeAccessedByLayers("Model", "Simulation", "App", "UI")
                .check(CLASSES);
    }

    @Test
    void onlyTheUserInterfaceUsesJavaFx() {
        noClasses()
                .that().resideOutsideOfPackages("..smarthome.ui..")
                .and().doNotHaveFullyQualifiedName(Launcher.class.getName())
                .should().dependOnClassesThat().resideInAPackage("javafx..")
                .because("the physics, the model and the simulation must be testable without a display")
                .check(CLASSES);
    }
}
