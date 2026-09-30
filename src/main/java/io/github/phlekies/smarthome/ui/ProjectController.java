package io.github.phlekies.smarthome.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javafx.application.HostServices;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import io.github.phlekies.smarthome.app.CoverageReport;
import io.github.phlekies.smarthome.app.ProjectFiles;
import io.github.phlekies.smarthome.app.ProjectState;
import io.github.phlekies.smarthome.app.SimulatorModel;
import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.template.FloorPlanTemplate;
import io.github.phlekies.smarthome.simulation.SimulationSettings;

/** File handling: new/open/save projects, examples, and image and report export. */
final class ProjectController {

    private static final String APP_TITLE = "Smart Home Simulator";

    private final SimulatorModel model;
    private final UiState state;
    private final Stage stage;
    private final HostServices hostServices;
    private final PlanImage planImage;

    private Path currentFile;
    private String projectName = "Untitled";
    private boolean dirty;
    private boolean loading;

    /** Renders the plan (with its legend) for image and report export. */
    @FunctionalInterface
    interface PlanImage {
        byte[] png() throws IOException;
    }

    ProjectController(SimulatorModel model, UiState state, Stage stage, HostServices hostServices, PlanImage planImage) {
        this.model = model;
        this.state = state;
        this.stage = stage;
        this.hostServices = hostServices;
        this.planImage = planImage;
        model.addListener(change -> {
            if (!loading && change != SimulatorModel.Change.DISPLAY && !dirty) {
                dirty = true;
                updateTitle();
            }
        });
        stage.setOnCloseRequest(this::onCloseRequest);
        updateTitle();
    }

    // ---------------------------------------------------------------------------------------
    // Projects
    // ---------------------------------------------------------------------------------------

    void newProject() {
        if (!confirmDiscard()) {
            return;
        }
        Environment env = new Environment();
        FloorPlanTemplate template = FloorPlanTemplate.defaultTemplate();
        env.setWalls(template.walls());
        load(new ProjectState(template, env, new SimulationSettings(), List.of(), null), null, "Untitled");
        state.status.set("New project. Place sensors and a hub, or load another template.");
    }

    void openExample(String name, ProjectState example) {
        if (!confirmDiscard()) {
            return;
        }
        load(example, null, name);
        state.status.set("Example loaded: " + name + ".");
    }

    void open() {
        if (!confirmDiscard()) {
            return;
        }
        File file = projectChooser("Open project").showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            load(ProjectFiles.read(file.toPath()), file.toPath(), baseName(file.toPath()));
            state.status.set("Opened " + file.getName() + ".");
        } catch (IOException | ProjectFiles.InvalidProjectException ex) {
            error("Could not open the project", ex.getMessage());
        }
    }

    boolean save() {
        if (currentFile == null) {
            return saveAs();
        }
        return writeTo(currentFile);
    }

    boolean saveAs() {
        FileChooser chooser = projectChooser("Save project");
        chooser.setInitialFileName(projectName + ProjectFiles.EXTENSION);
        File file = chooser.showSaveDialog(stage);
        return file != null && writeTo(file.toPath());
    }

    private boolean writeTo(Path file) {
        try {
            ProjectFiles.write(model.state(), file);
            currentFile = file;
            projectName = baseName(file);
            dirty = false;
            updateTitle();
            state.status.set("Saved " + file.getFileName() + ".");
            return true;
        } catch (IOException ex) {
            error("Could not save the project", ex.getMessage());
            return false;
        }
    }

    /** Replaces the model without marking the project as modified. */
    private void load(ProjectState project, Path file, String name) {
        loading = true;
        try {
            state.selectedDevice.set(null);
            state.selectedWall.set(null);
            model.load(project);
        } finally {
            loading = false;
        }
        currentFile = file;
        projectName = name;
        dirty = false;
        updateTitle();
    }

    // ---------------------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------------------

    void exportImage() {
        File file = chooser("Export plan image", projectName + ".png",
                new FileChooser.ExtensionFilter("PNG image", "*.png")).showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            Files.write(file.toPath(), planImage.png());
            state.status.set("Plan image exported to " + file.getName() + ".");
        } catch (IOException ex) {
            error("Could not export the image", ex.getMessage());
        }
    }

    void exportReport() {
        File file = chooser("Export coverage report", projectName + "-report.html",
                new FileChooser.ExtensionFilter("HTML report", "*.html")).showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            String html = CoverageReport.html(model, state.coverage.get(), planImage.png(), LocalDateTime.now());
            Files.writeString(file.toPath(), html, StandardCharsets.UTF_8);
            state.status.set("Report exported to " + file.getName() + ".");
            hostServices.showDocument(file.toURI().toString());
        } catch (IOException ex) {
            error("Could not export the report", ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private void onCloseRequest(WindowEvent event) {
        if (!confirmDiscard()) {
            event.consume();
        }
    }

    /** Asks what to do with unsaved changes. Returns false if the user cancelled. */
    boolean confirmDiscard() {
        if (!dirty) {
            return true;
        }
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discardButton = new ButtonType("Don't save", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Do you want to save the changes to " + projectName + "?",
                saveButton, discardButton, ButtonType.CANCEL);
        alert.initOwner(stage);
        alert.setHeaderText("Unsaved changes");
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) {
            return false;
        }
        return answer.get() != saveButton || save();
    }

    private void updateTitle() {
        stage.setTitle(projectName + (dirty ? " *" : "") + " - " + APP_TITLE);
    }

    private FileChooser projectChooser(String title) {
        return chooser(title, null,
                new FileChooser.ExtensionFilter("Smart Home Simulator project", "*" + ProjectFiles.EXTENSION, "*.json"));
    }

    private FileChooser chooser(String title, String initialName, FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(filter);
        if (initialName != null) {
            chooser.setInitialFileName(initialName);
        }
        if (currentFile != null && currentFile.getParent() != null) {
            chooser.setInitialDirectory(currentFile.getParent().toFile());
        }
        return chooser;
    }

    private void error(String header, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR, detail, ButtonType.OK);
        alert.initOwner(stage);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(ProjectFiles.EXTENSION)) return name.substring(0, name.length() - ProjectFiles.EXTENSION.length());
        if (name.endsWith(".json")) return name.substring(0, name.length() - ".json".length());
        return name;
    }
}
