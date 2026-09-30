package UI;

import core.Configuracion;
import core.Hub;
import core.RayMetrics;
import core.Sensor;
import core.Wall;
import core.Propagation;
import core.material.MaterialsDB;
import core.material.WallInteraction;
import core.sim.CellResult;
import core.sim.FadingModel;
import core.sim.HeatmapResult;
import core.sim.IndoorWaveEngine;
import core.sim.MapMetric;
import core.sim.PathContribution;
import core.sim.PropagationMode;
import core.sim.SimulationSettings;
import env.Environment;

import javafx.util.Duration;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

import javafx.scene.canvas.Canvas;

import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.*;

/**
 * Ventana principal: editor de planos, heatmap de cobertura y animación de rayos/ondas.
 */
public class VisualGridApp extends Application {

	// ============================
	// Constantes de dibujo / Grid
	// ============================
	public static final int MARGIN = 35;
	public static final int GRID_MAX_X = 50;
	public static final int GRID_MAX_Y = 40;
	public static final int SCALE = 22;

	// ============================
	// Parámetros raytracing legacy
	// ============================
	private static final int RAY_STEP_MS = 30;
	private static final int MAX_BOUNCES = 10;
	private static final double HUB_HIT_THRESHOLD = 0.30; // radio “acierto” (m)
	private static final double RAY_STEP_METERS = 1.0;
	private static final double EPS = 1e-3;

	// ============================
	// Estado global
	// ============================
	private final Configuracion config = new Configuracion();

	// Nuevo motor simple
	private Environment env; // parámetros físicos + paredes (materiales)
	private List<Wall> walls = new ArrayList<>();
	private Group wallsLayer;
	private Group editorOverlayLayer;

	// UI base
	private Group rootGroup; // superpone heatmap + canvas
	private Canvas gridCanvas; // rejilla + paredes + objetos
	private WritableImage heatmapImg; // 1 px por celda
	private ImageView heatmapView; // escalado a pixeles

	// Flags heatmap
	private boolean showHeatmap = false;
	private final SimulationSettings simulationSettings = new SimulationSettings();
	private HeatmapResult lastHeatmapResult;
	private ComboBox<MapMetric> cbMetric;
	private ComboBox<PropagationMode> cbMode;
	private ComboBox<FadingModel> cbFading;
	private ComboBox<Sensor> sensorSelectorCombo;
	private ComboBox<FloorPlanTool> canvasToolCombo;
	private CheckBox chkDiffraction;
	private CheckBox chkScattering;
	private String activeTemplateName = Environment.defaultTemplateName();
	private FloorPlanTool floorPlanTool = FloorPlanTool.SELECT;
	private Wall selectedWall;
	private double draftStartGridX = Double.NaN;
	private double draftStartGridY = Double.NaN;
	private double draftHoverGridX = Double.NaN;
	private double draftHoverGridY = Double.NaN;
	private core.material.Material floorPlanMaterial = MaterialsDB.DRYWALL;
	private double floorPlanThicknessCm = 8.0;
	private boolean floorPlanSnapToGrid = true;
	private final Deque<List<Wall>> wallEditHistory = new ArrayDeque<>();
	private ComboBox<MaterialChoice> floorPlanMaterialCombo;
	private TextField floorPlanThicknessField;
	private TextField deviceXField;
	private TextField deviceYField;
	private Label devicePlacementHintLabel;
	private Label floorPlanHintLabel;
	private Label floorPlanSelectionLabel;

	// Dimensiones raster
	private int cellSizePx = SCALE;
	private int gridW = GRID_MAX_X;
	private int gridH = GRID_MAX_Y;

	// Hub UI
	private Group hubNode = null;
	private Rectangle hubBox;
	private Tooltip hubTooltip;

	// Olas visuales
	private static final double WAVE_INITIAL_RADIUS = 5;
	private static final double WAVE_MAX_RADIUS = 300;
	private static final double WAVE_DURATION_SEC = 10.0;
	private static final double WAVE_INITIAL_OPACITY = 0.6;
	private static final double WAVE_FINAL_OPACITY = 0.0;

	private enum FloorPlanTool {
		SELECT("Seleccionar", "Selecciona una pared para editarla o cambiar su material."),
		WALL("Muro", "Haz clic en dos puntos para crear un muro recto."),
		ROOM("Habitacion", "Haz clic en dos esquinas opuestas para crear una estancia rectangular."),
		SENSOR("Sensor", "Haz clic en el plano para colocar un sensor."),
		HUB("Hub", "Haz clic en el plano para colocar o mover el hub."),
		DELETE("Borrar", "Haz clic sobre una pared para eliminarla.");

		private final String label;
		private final String helpText;

		FloorPlanTool(String label, String helpText) {
			this.label = label;
			this.helpText = helpText;
		}

		public String helpText() {
			return helpText;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private record MaterialChoice(String label, core.material.Material material) {
		@Override
		public String toString() {
			return label;
		}
	}

	// ============================
	// Main
	// ============================
	public static void main(String[] args) {
		Application.launch(VisualGridApp.class, args);
	}

	// ============================
	// JavaFX
	// ============================
	@Override
	public void start(Stage stage) {
		initModel();
		Parent root = buildMainUI();

		// Entorno
		env.setFreqMHz(Environment.WIFI_24_GHZ_MHZ); // 2400 MHz
		env.setBandwidthHz(20e6);
		env.setNoiseFigureDb(7.0);
		env.setAlphaDbPerMeter(0.0);

		// Panel lateral con ancho fijo y fondo opaco
		if (root instanceof BorderPane bp && bp.getRight() instanceof Region side) {
			side.setPrefWidth(360); // elige el ancho que prefieras para el panel
			side.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
			side.setBorder(new Border(new BorderStroke(Color.web("#dddddd"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
					BorderWidths.DEFAULT)));
		}

		// Scene sin tamaño fijo: se calcula a partir de los preferred sizes (canvas + panel)
		Scene scene = new Scene(root);
		stage.setTitle("Smart Home Simulator");
		stage.setScene(scene);
		stage.sizeToScene();
		fitStageToScreen(stage);
		stage.show();

		// Dibujo inicial + utilidades
		repaintAll();
		configureCanvasInteractions();
	}

	/** Evita que la ventana sea mayor que el área visible de la pantalla principal. */
	private static void fitStageToScreen(Stage stage) {
		Rectangle2D screen = Screen.getPrimary().getVisualBounds();
		if (stage.getWidth() > screen.getWidth()) stage.setWidth(screen.getWidth());
		if (stage.getHeight() > screen.getHeight()) stage.setHeight(screen.getHeight());
		stage.centerOnScreen();
	}

	// ============================
	// Inicialización y UI
	// ============================
	private void initModel() {
		env = new Environment();
		activeTemplateName = Environment.defaultTemplateName();
		env.setWalls(Environment.wallsForTemplate(Environment.defaultTemplateName()));
		walls = new ArrayList<>(env.getWalls());
	}

	private Parent buildMainUI() {
		gridCanvas = new Canvas(gridW * cellSizePx + 2 * MARGIN, gridH * cellSizePx + 2 * MARGIN);

		int imgW = gridW * cellSizePx + 2 * MARGIN;
		int imgH = gridH * cellSizePx + 2 * MARGIN;
		heatmapImg = new WritableImage(imgW, imgH);
		heatmapView = new ImageView(heatmapImg);
		heatmapView.setOpacity(0.74);
		heatmapView.setVisible(false);

		wallsLayer = new Group();
		editorOverlayLayer = new Group();
		editorOverlayLayer.setMouseTransparent(true);
		Pane canvasHolder = new Pane(gridCanvas);
		rootGroup = new Group(heatmapView, wallsLayer, canvasHolder, editorOverlayLayer);
		Pane centerPane = new Pane(rootGroup);

		VBox sideContent = new VBox(14);
		sideContent.setPadding(new Insets(16));
		sideContent.setFillWidth(true);
		sideContent.setPrefWidth(360);
		sideContent.setStyle("-fx-background-color: #f4f6f8;");

		Label panelTitle = new Label("Panel de control");
		panelTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #18212b;");
		Label panelSubtitle = new Label(
				"Coloca dispositivos, ajusta la simulacion y lanza acciones rapidas sin saturar la vista.");
		panelSubtitle.setWrapText(true);
		panelSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f6b76;");

		deviceXField = new TextField("5");
		deviceYField = new TextField("5");
		deviceXField.setPromptText("X");
		deviceYField.setPromptText("Y");
		deviceXField.setPrefWidth(70);
		deviceYField.setPrefWidth(70);
		styleInputField(deviceXField);
		styleInputField(deviceYField);

		Label lblX = new Label("X");
		Label lblY = new Label("Y");
		lblX.setStyle("-fx-text-fill: #5f6b76; -fx-font-weight: bold;");
		lblY.setStyle("-fx-text-fill: #5f6b76; -fx-font-weight: bold;");

		HBox positionRow = new HBox(8, lblX, deviceXField, lblY, deviceYField);
		positionRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(deviceXField, Priority.ALWAYS);
		HBox.setHgrow(deviceYField, Priority.ALWAYS);

		Button btnAddSensor = new Button("Anadir sensor");
		Button btnAddHub = new Button("Anadir hub");
		Button btnRemove = new Button("Eliminar seleccionado");
		stylePrimaryButton(btnAddSensor);
		styleSecondaryButton(btnAddHub);
		styleSecondaryButton(btnRemove);

		sensorSelectorCombo = new ComboBox<>();
		sensorSelectorCombo.setPromptText("Selecciona un sensor");
		sensorSelectorCombo.getItems().setAll(config.getSensores());
		styleInputField(sensorSelectorCombo);

		ComboBox<String> cbAntType = new ComboBox<>();
		cbAntType.getItems().addAll("Omni", "Direccional");
		cbAntType.getSelectionModel().select("Omni");
		styleInputField(cbAntType);

		TextField tfOrient = new TextField("0");
		TextField tfBeam = new TextField("90");
		TextField tfGain = new TextField("0");
		TextField tfPattern = new TextField("1.8");
		TextField tfPol = new TextField("0");
		styleInputField(tfOrient);
		styleInputField(tfBeam);
		styleInputField(tfGain);
		styleInputField(tfPattern);
		styleInputField(tfPol);

		Button btnApplyConfig = new Button("Guardar antena");
		stylePrimaryButton(btnApplyConfig);

		sensorSelectorCombo.valueProperty().addListener((obs, old, selected) -> {
			if (selected == null) {
				return;
			}
			cbAntType.getSelectionModel().select(selected.isDirectional() ? "Direccional" : "Omni");
			tfOrient.setText(String.format(Locale.US, "%.1f", selected.getOrientationDeg()));
			tfBeam.setText(String.format(Locale.US, "%.1f", selected.getBeamwidthDeg()));
			tfGain.setText(String.format(Locale.US, "%.1f", selected.getTxGainDb()));
			tfPattern.setText(String.format(Locale.US, "%.2f", selected.getPatternSharpness()));
			tfPol.setText(String.format(Locale.US, "%.1f", selected.getPolarizationDeg()));
			updateDevicePositionFields(selected.getX(), selected.getY());
		});

		btnAddSensor.setOnAction(e -> {
			try {
				int x = Integer.parseInt(deviceXField.getText().trim());
				int y = Integer.parseInt(deviceYField.getText().trim());
				createSensorAt(x, y);
			} catch (NumberFormatException ex) {
				System.err.println("Coordenadas invalidas");
			}
		});

		btnAddHub.setOnAction(e -> {
			try {
				int x = Integer.parseInt(deviceXField.getText().trim());
				int y = Integer.parseInt(deviceYField.getText().trim());
				placeHubAt(x, y);
			} catch (NumberFormatException ex) {
				System.err.println("Coordenadas invalidas");
			}
		});

		btnRemove.setOnAction(e -> {
			Sensor selected = sensorSelectorCombo.getSelectionModel().getSelectedItem();
			if (selected != null) {
				config.removeSensor(selected);
				sensorSelectorCombo.getItems().remove(selected);
			} else if (config.hasHub()) {
				config.setHub(null);
			}
			repaintAll();
		});

		btnApplyConfig.setOnAction(e -> {
			Sensor selected = sensorSelectorCombo.getSelectionModel().getSelectedItem();
			if (selected == null) {
				return;
			}

			selected.setAntennaType("Direccional".equals(cbAntType.getValue())
					? Sensor.AntennaType.DIRECTIONAL
					: Sensor.AntennaType.OMNI);
			try {
				selected.setOrientationDeg(Double.parseDouble(tfOrient.getText().trim()));
			} catch (NumberFormatException ex) {
				selected.setOrientationDeg(0.0);
			}
			try {
				selected.setBeamwidthDeg(Double.parseDouble(tfBeam.getText().trim()));
			} catch (NumberFormatException ex) {
				selected.setBeamwidthDeg(90.0);
			}
			try {
				selected.setTxGainDb(Double.parseDouble(tfGain.getText().trim()));
			} catch (NumberFormatException ex) {
				selected.setTxGainDb(0.0);
			}
			try {
				selected.setPatternSharpness(Double.parseDouble(tfPattern.getText().trim()));
			} catch (NumberFormatException ex) {
				selected.setPatternSharpness(1.8);
			}
			try {
				selected.setPolarizationDeg(Double.parseDouble(tfPol.getText().trim()));
			} catch (NumberFormatException ex) {
				selected.setPolarizationDeg(0.0);
			}
			repaintAll();
		});

		GridPane deviceGrid = createFormGrid();
		addFormRow(deviceGrid, 0, "Posicion", positionRow);
		addFormRow(deviceGrid, 1, "Sensor activo", sensorSelectorCombo);
		addFormRow(deviceGrid, 2, "Antena", cbAntType);
		addFormRow(deviceGrid, 3, "Orientacion (deg)", tfOrient);
		addFormRow(deviceGrid, 4, "Apertura (deg)", tfBeam);
		addFormRow(deviceGrid, 5, "Ganancia Tx (dB)", tfGain);
		addFormRow(deviceGrid, 6, "Patron G(theta)", tfPattern);
		addFormRow(deviceGrid, 7, "Polarizacion (deg)", tfPol);

		Button btnPlaceSensor = new Button("Colocar sensor en mapa");
		Button btnPlaceHub = new Button("Colocar hub en mapa");
		Button btnStopPlacing = new Button("Usar coordenadas");
		styleSecondaryButton(btnPlaceSensor);
		styleSecondaryButton(btnPlaceHub);
		styleSecondaryButton(btnStopPlacing);

		btnPlaceSensor.setOnAction(e -> setFloorPlanTool(FloorPlanTool.SENSOR));
		btnPlaceHub.setOnAction(e -> setFloorPlanTool(FloorPlanTool.HUB));
		btnStopPlacing.setOnAction(e -> setFloorPlanTool(FloorPlanTool.SELECT));

		devicePlacementHintLabel = new Label();
		devicePlacementHintLabel.setWrapText(true);
		devicePlacementHintLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #425466;");
		updateDevicePlacementHint();

		GridPane deviceButtons = createButtonGrid();
		deviceButtons.add(btnAddSensor, 0, 0);
		deviceButtons.add(btnAddHub, 1, 0);
		deviceButtons.add(btnRemove, 0, 1, 2, 1);
		deviceButtons.add(btnPlaceSensor, 0, 2);
		deviceButtons.add(btnPlaceHub, 1, 2);
		deviceButtons.add(btnStopPlacing, 0, 3, 2, 1);

		VBox deviceContent = new VBox(12, deviceGrid, deviceButtons, devicePlacementHintLabel, btnApplyConfig);
		VBox devicesSection = createSection("Dispositivos", deviceContent);

		ComboBox<String> cbFreq = new ComboBox<>();
		cbFreq.getItems().addAll("2.4 GHz", "5 GHz");
		cbFreq.getSelectionModel().select("2.4 GHz");
		styleInputField(cbFreq);
		cbFreq.setOnAction(e -> {
			String sel = cbFreq.getSelectionModel().getSelectedItem();
			env.setFreqMHz("2.4 GHz".equals(sel) ? Environment.WIFI_24_GHZ_MHZ : Environment.WIFI_5_GHZ_MHZ);
			repaintAll();
		});

		Button btnHeatmap = new Button("Mostrar o actualizar mapa");
		Button btnClearHeatmap = new Button("Ocultar mapa");
		stylePrimaryButton(btnHeatmap);
		styleSecondaryButton(btnClearHeatmap);
		btnHeatmap.setOnAction(e -> {
			showHeatmap = true;
			heatmapView.setVisible(true);
			drawHeatmap();
		});
		btnClearHeatmap.setOnAction(e -> {
			showHeatmap = false;
			heatmapView.setVisible(false);
			lastHeatmapResult = null;
		});

		cbMetric = new ComboBox<>();
		cbMetric.getItems().addAll(MapMetric.values());
		cbMetric.getSelectionModel().select(simulationSettings.getMapMetric());
		styleInputField(cbMetric);
		cbMetric.setOnAction(e -> {
			simulationSettings.setMapMetric(cbMetric.getValue());
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});

		cbMode = new ComboBox<>();
		cbMode.getItems().addAll(PropagationMode.values());
		cbMode.getSelectionModel().select(simulationSettings.getPropagationMode());
		styleInputField(cbMode);
		cbMode.setOnAction(e -> {
			simulationSettings.setPropagationMode(cbMode.getValue());
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});

		cbFading = new ComboBox<>();
		cbFading.getItems().addAll(FadingModel.values());
		cbFading.getSelectionModel().select(simulationSettings.getFadingModel());
		styleInputField(cbFading);
		cbFading.setOnAction(e -> {
			simulationSettings.setFadingModel(cbFading.getValue());
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});

		chkDiffraction = new CheckBox("Usar difraccion");
		chkScattering = new CheckBox("Usar dispersion");
		CheckBox chkParallel = new CheckBox("Calculo en paralelo");
		chkDiffraction.setSelected(simulationSettings.isDiffractionEnabled());
		chkScattering.setSelected(simulationSettings.isScatteringEnabled());
		chkParallel.setSelected(simulationSettings.isParallelComputation());
		chkDiffraction.setStyle("-fx-text-fill: #243341;");
		chkScattering.setStyle("-fx-text-fill: #243341;");
		chkParallel.setStyle("-fx-text-fill: #243341;");
		chkDiffraction.setOnAction(e -> {
			simulationSettings.setDiffractionEnabled(chkDiffraction.isSelected());
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});
		chkScattering.setOnAction(e -> {
			simulationSettings.setScatteringEnabled(chkScattering.isSelected());
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});
		chkParallel.setOnAction(e -> {
			simulationSettings.setParallelComputation(chkParallel.isSelected());
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});

		GridPane mapGrid = createFormGrid();
		addFormRow(mapGrid, 0, "Frecuencia", cbFreq);
		addFormRow(mapGrid, 1, "Metrica", cbMetric);
		addFormRow(mapGrid, 2, "Modo", cbMode);
		addFormRow(mapGrid, 3, "Fading", cbFading);
		VBox mapChecks = new VBox(8, chkDiffraction, chkScattering, chkParallel);
		addFormRow(mapGrid, 4, "Opciones", mapChecks);

		GridPane mapButtons = createButtonGrid();
		mapButtons.add(btnHeatmap, 0, 0, 2, 1);
		mapButtons.add(btnClearHeatmap, 0, 1, 2, 1);

		VBox mapContent = new VBox(12, mapGrid, mapButtons);
		VBox mapSection = createSection("Mapa y simulacion", mapContent);

		TextField tfExponent = new TextField(String.format(Locale.US, "%.2f", simulationSettings.getLogDistanceExponent()));
		TextField tfKFactor = new TextField(String.format(Locale.US, "%.1f", simulationSettings.getRicianKFactorDb()));
		TextField tfCull = new TextField(String.format(Locale.US, "%.0f", simulationSettings.getCullingThresholdDbm()));
		TextField tfBW = new TextField(String.format(Locale.US, "%.3g", env.getBandwidthHz()));
		TextField tfNF = new TextField(String.format(Locale.US, "%.1f", env.getNoiseFigureDb()));
		styleInputField(tfExponent);
		styleInputField(tfKFactor);
		styleInputField(tfCull);
		styleInputField(tfBW);
		styleInputField(tfNF);

		Button btnApplyModel = new Button("Guardar modelo");
		Button btnBW = new Button("Aplicar BW");
		Button btnNF = new Button("Aplicar NF");
		stylePrimaryButton(btnApplyModel);
		styleSecondaryButton(btnBW);
		styleSecondaryButton(btnNF);

		btnApplyModel.setOnAction(e -> {
			try {
				simulationSettings.setLogDistanceExponent(Double.parseDouble(tfExponent.getText().trim()));
			} catch (NumberFormatException ignored) {
			}
			try {
				simulationSettings.setRicianKFactorDb(Double.parseDouble(tfKFactor.getText().trim()));
			} catch (NumberFormatException ignored) {
			}
			try {
				simulationSettings.setCullingThresholdDbm(Double.parseDouble(tfCull.getText().trim()));
			} catch (NumberFormatException ignored) {
			}
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});

		btnBW.setOnAction(e -> {
			try {
				env.setBandwidthHz(Double.parseDouble(tfBW.getText().trim()));
				if (heatmapView.isVisible()) {
					drawHeatmap();
				}
			} catch (NumberFormatException ignored) {
			}
		});

		btnNF.setOnAction(e -> {
			try {
				env.setNoiseFigureDb(Double.parseDouble(tfNF.getText().trim()));
				if (heatmapView.isVisible()) {
					drawHeatmap();
				}
			} catch (NumberFormatException ignored) {
			}
		});

		HBox bwRow = new HBox(8, tfBW, btnBW);
		HBox nfRow = new HBox(8, tfNF, btnNF);
		HBox.setHgrow(tfBW, Priority.ALWAYS);
		HBox.setHgrow(tfNF, Priority.ALWAYS);

		GridPane radioGrid = createFormGrid();
		addFormRow(radioGrid, 0, "Exponente indoor", tfExponent);
		addFormRow(radioGrid, 1, "K-factor Rician", tfKFactor);
		addFormRow(radioGrid, 2, "Umbral de culling", tfCull);
		addFormRow(radioGrid, 3, "Ancho de banda (Hz)", bwRow);
		addFormRow(radioGrid, 4, "Figura de ruido (dB)", nfRow);

		VBox radioContent = new VBox(12, radioGrid, btnApplyModel);
		VBox radioSection = createSection("Entorno y radio", radioContent);
		VBox floorPlanSection = createPlanEditorSection();

		ComboBox<String> cbTemplate = new ComboBox<>();
		cbTemplate.getItems().setAll(Environment.builtInTemplateNames());
		cbTemplate.getSelectionModel().select(Environment.defaultTemplateName());
		styleInputField(cbTemplate);

		Label templateInfo = new Label(buildTemplateSummary(cbTemplate.getValue()));
		templateInfo.setWrapText(true);
		templateInfo.setMaxWidth(Double.MAX_VALUE);
		templateInfo.setStyle("-fx-padding: 10; -fx-background-color: #f7fafc; -fx-background-radius: 8; "
				+ "-fx-border-color: #d8e0e8; -fx-border-radius: 8; -fx-text-fill: #425466;");

		cbTemplate.valueProperty().addListener((obs, old, selected) -> {
			templateInfo.setText(buildTemplateSummary(selected));
		});

		Button btnLoadTemplate = new Button("Cargar escenario");
		Button btnAppendTemplate = new Button("Anadir escenario");
		Button btnClearTemplate = new Button("Vaciar plano");
		stylePrimaryButton(btnLoadTemplate);
		styleSecondaryButton(btnAppendTemplate);
		styleSecondaryButton(btnClearTemplate);

		TextField tfTemplateOffsetX = new TextField("0");
		TextField tfTemplateOffsetY = new TextField("0");
		tfTemplateOffsetX.setPromptText("X");
		tfTemplateOffsetY.setPromptText("Y");
		styleInputField(tfTemplateOffsetX);
		styleInputField(tfTemplateOffsetY);

		btnLoadTemplate.setOnAction(e -> applyTemplate(cbTemplate.getValue()));
		btnAppendTemplate.setOnAction(e -> {
			double offsetX = parseDoubleOrDefault(tfTemplateOffsetX.getText(), 0.0);
			double offsetY = parseDoubleOrDefault(tfTemplateOffsetY.getText(), 0.0);
			appendTemplateToPlan(cbTemplate.getValue(), offsetX, offsetY);
		});
		btnClearTemplate.setOnAction(e -> {
			clearAllWalls();
		});

		GridPane templateGrid = createFormGrid();
		addFormRow(templateGrid, 0, "Escenario", cbTemplate);
		addFormRow(templateGrid, 1, "Resumen", templateInfo);
		HBox templateOffsetRow = new HBox(8, tfTemplateOffsetX, tfTemplateOffsetY);
		HBox.setHgrow(tfTemplateOffsetX, Priority.ALWAYS);
		HBox.setHgrow(tfTemplateOffsetY, Priority.ALWAYS);
		addFormRow(templateGrid, 2, "Desplazamiento", templateOffsetRow);

		GridPane templateButtons = createButtonGrid();
		templateButtons.add(btnLoadTemplate, 0, 0);
		templateButtons.add(btnClearTemplate, 1, 0);
		templateButtons.add(btnAppendTemplate, 0, 1, 2, 1);
		VBox templateContent = new VBox(12, templateGrid, templateButtons);
		VBox templateSection = createSection("Plantillas", templateContent);

		Button btnRays = new Button("Lanzar rayos");
		Button btnWaves = new Button("Lanzar ondas");
		Button btnParams = new Button("Resumen de parametros");
		stylePrimaryButton(btnRays);
		styleSecondaryButton(btnWaves);
		styleSecondaryButton(btnParams);

		btnRays.setOnAction(e -> {
			simulationSettings.setPropagationMode(PropagationMode.RAYS);
			cbMode.getSelectionModel().select(PropagationMode.RAYS);
			centerPane.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));
			launchRaysForAllSensors(centerPane);
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});

		btnWaves.setOnAction(e -> {
			simulationSettings.setPropagationMode(PropagationMode.WAVES);
			cbMode.getSelectionModel().select(PropagationMode.WAVES);
			centerPane.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("wave")));
			for (Sensor sensor : config.getSensores()) {
				launchWavefronts(centerPane, sensor);
			}
			if (heatmapView.isVisible()) {
				drawHeatmap();
			}
		});

		btnParams.setOnAction(e -> showSummaryWindow());

		VBox actionsContent = new VBox(10, btnRays, btnWaves, btnParams);
		VBox actionsSection = createSection("Acciones rapidas", actionsContent);

		sideContent.getChildren().addAll(panelTitle, panelSubtitle, devicesSection, mapSection, radioSection, floorPlanSection,
				templateSection, actionsSection);

		ScrollPane side = new ScrollPane(sideContent);
		side.setFitToWidth(true);
		side.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		side.setPrefWidth(360);
		// Sin esto la altura preferida del panel es la de todo su contenido (~2800 px)
		// y la ventana se abre más alta que la pantalla.
		side.setPrefHeight(imgH);
		side.setStyle("-fx-background: #f4f6f8; -fx-background-color: #f4f6f8;");

		BorderPane bp = new BorderPane();
		bp.setCenter(centerPane);
		bp.setRight(side);

		drawAxesAndGrid(centerPane);
		drawWallsInLayer();

		return bp;
	}

	private void styleInputField(Control control) {
		control.setMaxWidth(Double.MAX_VALUE);
		control.setStyle("-fx-background-color: white; -fx-border-color: #cfd7df; -fx-border-radius: 8; "
				+ "-fx-background-radius: 8; -fx-padding: 8 10 8 10;");
	}

	private void stylePrimaryButton(Button button) {
		button.setMaxWidth(Double.MAX_VALUE);
		button.setStyle("-fx-background-color: #1f6feb; -fx-text-fill: white; -fx-font-weight: bold; "
				+ "-fx-background-radius: 10; -fx-padding: 10 14 10 14;");
	}

	private void styleSecondaryButton(Button button) {
		button.setMaxWidth(Double.MAX_VALUE);
		button.setStyle("-fx-background-color: white; -fx-text-fill: #1f2933; -fx-font-weight: bold; "
				+ "-fx-border-color: #cfd7df; -fx-border-radius: 10; -fx-background-radius: 10; "
				+ "-fx-padding: 10 14 10 14;");
	}

	private GridPane createFormGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);

		ColumnConstraints labelCol = new ColumnConstraints();
		labelCol.setMinWidth(130);
		labelCol.setPrefWidth(140);
		ColumnConstraints valueCol = new ColumnConstraints();
		valueCol.setHgrow(Priority.ALWAYS);
		valueCol.setFillWidth(true);
		grid.getColumnConstraints().addAll(labelCol, valueCol);

		return grid;
	}

	private void addFormRow(GridPane grid, int row, String labelText, Node field) {
		Label label = new Label(labelText);
		label.setWrapText(true);
		label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #31414f;");
		GridPane.setHgrow(field, Priority.ALWAYS);
		grid.add(label, 0, row);
		grid.add(field, 1, row);
	}

	private GridPane createButtonGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);

		ColumnConstraints col1 = new ColumnConstraints();
		col1.setPercentWidth(50);
		col1.setFillWidth(true);
		col1.setHgrow(Priority.ALWAYS);
		ColumnConstraints col2 = new ColumnConstraints();
		col2.setPercentWidth(50);
		col2.setFillWidth(true);
		col2.setHgrow(Priority.ALWAYS);
		grid.getColumnConstraints().addAll(col1, col2);

		return grid;
	}

	private VBox createSection(String title, Node content) {
		Label header = new Label(title);
		header.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #16212c;");

		VBox section = new VBox(12, header, content);
		section.setFillWidth(true);
		section.setPadding(new Insets(14));
		section.setStyle("-fx-background-color: white; -fx-background-radius: 14; "
				+ "-fx-border-color: #d8e0e8; -fx-border-radius: 14;");
		return section;
	}

	private VBox createPlanEditorSection() {
		canvasToolCombo = new ComboBox<>();
		canvasToolCombo.getItems().setAll(FloorPlanTool.values());
		canvasToolCombo.getSelectionModel().select(floorPlanTool);
		styleInputField(canvasToolCombo);

		floorPlanMaterialCombo = new ComboBox<>();
		floorPlanMaterialCombo.getItems().setAll(buildFloorPlanMaterials());
		floorPlanMaterialCombo.getSelectionModel().select(materialChoiceFor(floorPlanMaterial));
		styleInputField(floorPlanMaterialCombo);

		floorPlanThicknessField = new TextField(String.format(Locale.US, "%.1f", floorPlanThicknessCm));
		styleInputField(floorPlanThicknessField);

		CheckBox chkSnap = new CheckBox("Ajustar a rejilla de 1 m");
		chkSnap.setSelected(floorPlanSnapToGrid);
		chkSnap.setStyle("-fx-text-fill: #243341;");

		Button btnApplySelected = new Button("Aplicar a la seleccion");
		Button btnUndo = new Button("Deshacer");
		Button btnCancelDraft = new Button("Cancelar trazo");
		Button btnDeleteSelected = new Button("Borrar seleccion");
		stylePrimaryButton(btnApplySelected);
		styleSecondaryButton(btnUndo);
		styleSecondaryButton(btnCancelDraft);
		styleSecondaryButton(btnDeleteSelected);

		TextField tfRoomX = new TextField("4");
		TextField tfRoomY = new TextField("4");
		TextField tfRoomWidth = new TextField("8");
		TextField tfRoomHeight = new TextField("6");
		styleInputField(tfRoomX);
		styleInputField(tfRoomY);
		styleInputField(tfRoomWidth);
		styleInputField(tfRoomHeight);

		Button btnCreateRoom = new Button("Crear habitacion");
		stylePrimaryButton(btnCreateRoom);

		canvasToolCombo.setOnAction(e -> setFloorPlanTool(canvasToolCombo.getValue()));
		floorPlanMaterialCombo.setOnAction(e -> {
			MaterialChoice choice = floorPlanMaterialCombo.getValue();
			if (choice != null) {
				floorPlanMaterial = choice.material();
				updateFloorPlanHint(floorPlanTool.helpText());
			}
		});
		floorPlanThicknessField.textProperty().addListener((obs, old, value) -> {
			floorPlanThicknessCm = Math.max(1.0, parseDoubleOrDefault(value, floorPlanThicknessCm));
		});
		chkSnap.setOnAction(e -> {
			floorPlanSnapToGrid = chkSnap.isSelected();
			refreshPlanEditorOverlay();
		});

		btnApplySelected.setOnAction(e -> {
			floorPlanThicknessCm = Math.max(1.0, parseDoubleOrDefault(floorPlanThicknessField.getText(), floorPlanThicknessCm));
			floorPlanThicknessField.setText(String.format(Locale.US, "%.1f", floorPlanThicknessCm));
			MaterialChoice choice = floorPlanMaterialCombo.getValue();
			if (choice != null) {
				floorPlanMaterial = choice.material();
			}

			if (selectedWall == null) {
				updateFloorPlanHint("Selecciona una pared antes de aplicar cambios.");
				return;
			}

			selectedWall.setMaterial(floorPlanMaterial);
			selectedWall.setThicknessCm(floorPlanThicknessCm);
			syncWallsWithEnvironment();
			updateFloorPlanSelectionLabel();
			repaintAll();
			updateFloorPlanHint("Cambios aplicados a la pared seleccionada.");
		});

		btnUndo.setOnAction(e -> undoLastWallEdit());
		btnCancelDraft.setOnAction(e -> {
			resetFloorPlanDraft();
			refreshPlanEditorOverlay();
			updateFloorPlanHint(floorPlanTool.helpText());
		});
		btnDeleteSelected.setOnAction(e -> {
			if (selectedWall == null) {
				updateFloorPlanHint("No hay ninguna pared seleccionada para borrar.");
				return;
			}
			removeWallFromPlan(selectedWall, true);
		});

		btnCreateRoom.setOnAction(e -> {
			floorPlanThicknessCm = Math.max(1.0, parseDoubleOrDefault(floorPlanThicknessField.getText(), floorPlanThicknessCm));
			floorPlanThicknessField.setText(String.format(Locale.US, "%.1f", floorPlanThicknessCm));
			MaterialChoice choice = floorPlanMaterialCombo.getValue();
			if (choice != null) {
				floorPlanMaterial = choice.material();
			}

			double roomX = parseDoubleOrDefault(tfRoomX.getText(), 0.0);
			double roomY = parseDoubleOrDefault(tfRoomY.getText(), 0.0);
			double roomWidth = Math.max(1.0, parseDoubleOrDefault(tfRoomWidth.getText(), 4.0));
			double roomHeight = Math.max(1.0, parseDoubleOrDefault(tfRoomHeight.getText(), 4.0));

			addRoomRectangle(roomX, roomY, roomX + roomWidth, roomY + roomHeight,
					floorPlanMaterial, floorPlanThicknessCm);
			updateFloorPlanHint("Habitacion creada con el constructor rapido.");
		});

		GridPane editorGrid = createFormGrid();
		addFormRow(editorGrid, 0, "Herramienta", canvasToolCombo);
		addFormRow(editorGrid, 1, "Material", floorPlanMaterialCombo);
		addFormRow(editorGrid, 2, "Grosor (cm)", floorPlanThicknessField);
		addFormRow(editorGrid, 3, "Ajuste", chkSnap);

		GridPane quickRoomGrid = createFormGrid();
		HBox roomOriginRow = new HBox(8, tfRoomX, tfRoomY);
		HBox roomSizeRow = new HBox(8, tfRoomWidth, tfRoomHeight);
		HBox.setHgrow(tfRoomX, Priority.ALWAYS);
		HBox.setHgrow(tfRoomY, Priority.ALWAYS);
		HBox.setHgrow(tfRoomWidth, Priority.ALWAYS);
		HBox.setHgrow(tfRoomHeight, Priority.ALWAYS);
		addFormRow(quickRoomGrid, 0, "Origen (X,Y)", roomOriginRow);
		addFormRow(quickRoomGrid, 1, "Tamano (m)", roomSizeRow);

		GridPane editorButtons = createButtonGrid();
		editorButtons.add(btnApplySelected, 0, 0, 2, 1);
		editorButtons.add(btnUndo, 0, 1);
		editorButtons.add(btnCancelDraft, 1, 1);
		editorButtons.add(btnDeleteSelected, 0, 2, 2, 1);

		floorPlanSelectionLabel = new Label();
		floorPlanSelectionLabel.setWrapText(true);
		floorPlanSelectionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #102a43;");

		floorPlanHintLabel = new Label();
		floorPlanHintLabel.setWrapText(true);
		floorPlanHintLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #425466;");

		updateFloorPlanSelectionLabel();
		updateFloorPlanHint(floorPlanTool.helpText());

		VBox quickRoomBox = new VBox(10, quickRoomGrid, btnCreateRoom);
		quickRoomBox.setPadding(new Insets(10));
		quickRoomBox.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; "
				+ "-fx-border-color: #d8e0e8; -fx-border-radius: 10;");

		VBox infoBox = new VBox(8, floorPlanSelectionLabel, floorPlanHintLabel);
		infoBox.setPadding(new Insets(10));
		infoBox.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; "
				+ "-fx-border-color: #d8e0e8; -fx-border-radius: 10;");

		VBox editorContent = new VBox(12, editorGrid, editorButtons, quickRoomBox, infoBox);
		return createSection("Editor de planos", editorContent);
	}

	private List<MaterialChoice> buildFloorPlanMaterials() {
		return List.of(
				new MaterialChoice("Tabique", MaterialsDB.DRYWALL),
				new MaterialChoice("Ladrillo", MaterialsDB.BRICK),
				new MaterialChoice("Hormigon", MaterialsDB.CONCRETE),
				new MaterialChoice("Cristal", MaterialsDB.GLASS),
				new MaterialChoice("Madera", MaterialsDB.WOOD),
				new MaterialChoice("Puerta metalica", MaterialsDB.METAL_DOOR));
	}

	private MaterialChoice materialChoiceFor(core.material.Material material) {
		for (MaterialChoice choice : buildFloorPlanMaterials()) {
			if (choice.material() == material) {
				return choice;
			}
		}
		return buildFloorPlanMaterials().getFirst();
	}

	private void setFloorPlanTool(FloorPlanTool tool) {
		floorPlanTool = (tool == null) ? FloorPlanTool.SELECT : tool;
		if (canvasToolCombo != null && canvasToolCombo.getValue() != floorPlanTool) {
			canvasToolCombo.getSelectionModel().select(floorPlanTool);
		}
		resetFloorPlanDraft();
		updateFloorPlanHint(floorPlanTool.helpText());
		updateDevicePlacementHint();
		refreshPlanEditorOverlay();
	}

	private void updateFloorPlanSelectionLabel() {
		if (floorPlanSelectionLabel == null) {
			return;
		}
		if (selectedWall == null) {
			floorPlanSelectionLabel.setText("Seleccion actual: ninguna pared.");
			if (floorPlanMaterialCombo != null) {
				floorPlanMaterialCombo.getSelectionModel().select(materialChoiceFor(floorPlanMaterial));
			}
			if (floorPlanThicknessField != null) {
				floorPlanThicknessField.setText(String.format(Locale.US, "%.1f", floorPlanThicknessCm));
			}
			return;
		}
		String materialName = (selectedWall.getMaterial() == null) ? "Sin material" : selectedWall.getMaterial().getName();
		floorPlanMaterial = (selectedWall.getMaterial() == null) ? MaterialsDB.DRYWALL : selectedWall.getMaterial();
		floorPlanThicknessCm = selectedWall.getThicknessCm();
		if (floorPlanMaterialCombo != null) {
			floorPlanMaterialCombo.getSelectionModel().select(materialChoiceFor(floorPlanMaterial));
		}
		if (floorPlanThicknessField != null) {
			floorPlanThicknessField.setText(String.format(Locale.US, "%.1f", floorPlanThicknessCm));
		}
		floorPlanSelectionLabel.setText(String.format(Locale.US,
				"Seleccion actual: (%.1f, %.1f) -> (%.1f, %.1f) | %s | %.1f cm",
				selectedWall.getX1(), selectedWall.getY1(), selectedWall.getX2(), selectedWall.getY2(),
				materialName, selectedWall.getThicknessCm()));
	}

	private void updateDevicePlacementHint() {
		if (devicePlacementHintLabel == null) {
			return;
		}

		String text = switch (floorPlanTool) {
		case SENSOR -> "Modo activo: colocar sensor. Haz clic en el mapa para anadir emisores.";
		case HUB -> "Modo activo: colocar hub. Haz clic en el mapa para ubicar o mover el receptor.";
		default -> "Puedes anadir dispositivos por coordenadas o activar la colocacion con raton.";
		};
		devicePlacementHintLabel.setText(text);
	}

	private void updateDevicePositionFields(int x, int y) {
		if (deviceXField != null) {
			deviceXField.setText(Integer.toString(x));
		}
		if (deviceYField != null) {
			deviceYField.setText(Integer.toString(y));
		}
	}

	private int nextSensorNumber() {
		int next = 1;
		for (Sensor sensor : config.getSensores()) {
			String id = sensor.getId();
			if (id != null && id.matches("S\\d+")) {
				next = Math.max(next, Integer.parseInt(id.substring(1)) + 1);
			}
		}
		return next;
	}

	private Sensor createSensorAt(int x, int y) {
		int sensorNumber = nextSensorNumber();
		Sensor sensor = new Sensor("S" + sensorNumber, "Sensor " + sensorNumber, x, y, 22.5);
		config.addSensor(sensor);
		if (sensorSelectorCombo != null) {
			sensorSelectorCombo.getItems().setAll(config.getSensores());
			sensorSelectorCombo.getSelectionModel().select(sensor);
		}
		updateDevicePositionFields(x, y);
		repaintAll();
		return sensor;
	}

	private void placeHubAt(int x, int y) {
		config.setHub(new Hub("H1", "Hub central", x, y));
		if (sensorSelectorCombo != null) {
			sensorSelectorCombo.getSelectionModel().clearSelection();
		}
		updateDevicePositionFields(x, y);
		repaintAll();
	}

	private void updateFloorPlanHint(String text) {
		if (floorPlanHintLabel != null) {
			floorPlanHintLabel.setText(text);
		}
		if (devicePlacementHintLabel != null && (floorPlanTool == FloorPlanTool.SENSOR || floorPlanTool == FloorPlanTool.HUB)) {
			devicePlacementHintLabel.setText(text);
		}
	}

	private void resetFloorPlanDraft() {
		draftStartGridX = Double.NaN;
		draftStartGridY = Double.NaN;
		draftHoverGridX = Double.NaN;
		draftHoverGridY = Double.NaN;
	}

	private void clearAllWalls() {
		walls.clear();
		selectedWall = null;
		wallEditHistory.clear();
		resetFloorPlanDraft();
		syncWallsWithEnvironment();
		updateFloorPlanSelectionLabel();
		repaintAll();
		updateFloorPlanHint("Plano vaciado. Puedes dibujar muros o anadir una plantilla.");
	}

	private void appendTemplateToPlan(String templateName, double offsetX, double offsetY) {
		if (templateName == null) {
			updateFloorPlanHint("Selecciona una plantilla antes de anadirla.");
			return;
		}

		List<Wall> shiftedWalls = new ArrayList<>();
		for (Wall wall : Environment.wallsForTemplate(templateName)) {
			shiftedWalls.add(new Wall(
					wall.getX1() + offsetX,
					wall.getY1() + offsetY,
					wall.getX2() + offsetX,
					wall.getY2() + offsetY,
					wall.getMaterial(),
					wall.getThicknessCm()));
		}
		addWallsToPlan(shiftedWalls);
		updateFloorPlanHint(String.format(Locale.US,
				"Escenario \"%s\" anadido con desplazamiento (%.1f, %.1f).",
				templateName, offsetX, offsetY));
	}

	private void addWallsToPlan(List<Wall> newWalls) {
		if (newWalls == null || newWalls.isEmpty()) {
			return;
		}
		List<Wall> batch = new ArrayList<>();
		for (Wall wall : newWalls) {
			Wall copy = wall.copy();
			walls.add(copy);
			batch.add(copy.copy());
		}
		wallEditHistory.push(batch);
		syncWallsWithEnvironment();
		repaintAll();
	}

	private void addRoomRectangle(double x1, double y1, double x2, double y2,
			core.material.Material material, double thicknessCm) {
		double minX = Math.min(x1, x2);
		double minY = Math.min(y1, y2);
		double maxX = Math.max(x1, x2);
		double maxY = Math.max(y1, y2);
		if (Math.abs(maxX - minX) < 1e-6 || Math.abs(maxY - minY) < 1e-6) {
			updateFloorPlanHint("La habitacion necesita ancho y alto mayores que cero.");
			return;
		}

		List<Wall> roomWalls = List.of(
				new Wall(minX, minY, maxX, minY, material, thicknessCm),
				new Wall(maxX, minY, maxX, maxY, material, thicknessCm),
				new Wall(maxX, maxY, minX, maxY, material, thicknessCm),
				new Wall(minX, maxY, minX, minY, material, thicknessCm));
		addWallsToPlan(roomWalls);
	}

	private void syncWallsWithEnvironment() {
		env.setWalls(walls);
		walls = new ArrayList<>(env.getWalls());
		lastHeatmapResult = null;
		refreshPlanEditorOverlay();
	}

	private void undoLastWallEdit() {
		if (wallEditHistory.isEmpty()) {
			updateFloorPlanHint("No hay acciones de plano para deshacer.");
			return;
		}

		List<Wall> batch = wallEditHistory.pop();
		for (int i = batch.size() - 1; i >= 0; i--) {
			removeMatchingWallFromCurrentPlan(batch.get(i));
		}
		selectedWall = null;
		syncWallsWithEnvironment();
		updateFloorPlanSelectionLabel();
		repaintAll();
		updateFloorPlanHint("Se ha deshecho la ultima edicion del plano.");
	}

	private boolean removeMatchingWallFromCurrentPlan(Wall wallToRemove) {
		for (int i = walls.size() - 1; i >= 0; i--) {
			if (sameWall(walls.get(i), wallToRemove)) {
				walls.remove(i);
				return true;
			}
		}
		return false;
	}

	private void removeWallFromPlan(Wall wall, boolean recordHistory) {
		if (wall == null) {
			return;
		}
		if (recordHistory) {
			wallEditHistory.push(List.of(wall.copy()));
		}
		if (!walls.remove(wall)) {
			removeMatchingWallFromCurrentPlan(wall);
		}
		if (selectedWall == wall || (selectedWall != null && sameWall(selectedWall, wall))) {
			selectedWall = null;
		}
		syncWallsWithEnvironment();
		updateFloorPlanSelectionLabel();
		repaintAll();
		updateFloorPlanHint("Pared eliminada del plano.");
	}

	private double parseDoubleOrDefault(String text, double defaultValue) {
		try {
			return Double.parseDouble(text.trim());
		} catch (RuntimeException ex) {
			return defaultValue;
		}
	}

	private void applyTemplate(String templateName) {
		if (templateName == null) {
			return;
		}
		activeTemplateName = templateName;
		env.setWalls(Environment.wallsForTemplate(templateName));
		walls = new ArrayList<>(env.getWalls());
		selectedWall = null;
		wallEditHistory.clear();
		resetFloorPlanDraft();
		updateFloorPlanSelectionLabel();
		updateFloorPlanHint("Escenario cargado. Puedes ajustarlo o ampliarlo desde el editor de planos.");
		repaintAll();
	}

	private String buildTemplateSummary(String templateName) {
		if (templateName == null) {
			return "Selecciona una plantilla para cargar un escenario interior.";
		}
		return Environment.templateDescription(templateName) + "\nParedes: "
				+ Environment.wallsForTemplate(templateName).size() + " segmentos";
	}

	// ============================
	// Dibujo (rejilla / paredes / objetos)
	// ============================
	private void repaintAll() {
		boolean heatmapVisible = showHeatmap || (heatmapView != null && heatmapView.isVisible());
		if (heatmapVisible) {
			showHeatmap = true;
			drawHeatmap();
			heatmapView.setVisible(true);
		} else if (heatmapView != null) {
			heatmapView.setVisible(false);
			lastHeatmapResult = null;
		}

		Pane center = (Pane) ((BorderPane) rootGroup.getParent().getParent()).getCenter();
		drawAxesAndGrid(center);
		drawWallsInLayer();
		refreshPlanEditorOverlay();
		drawHubAndSensors(center);
	}

	private void drawAxesAndGrid(Pane root) {
		root.getChildren().removeIf(n -> "grid".equals(n.getId()));
		Group g = new Group();
		g.setId("grid");

		Font tickFont = Font.font(11);
		Color gridColor = Color.web("#eeeeee");

		for (int x = 0; x <= gridW; x++) {
			double xx = px(x);
			Line v = new Line(xx, py(0), xx, py(gridH));
			v.setStroke(gridColor);
			g.getChildren().add(v);
			Label lab = label(Integer.toString(x), xx, py(0) + 12, tickFont, Color.GRAY);
			g.getChildren().add(lab);
		}
		for (int y = 0; y <= gridH; y++) {
			double yy = py(y);
			Line h = new Line(px(0), yy, px(gridW), yy);
			h.setStroke(gridColor);
			g.getChildren().add(h);
			Label lab = label(Integer.toString(y), px(0) - 20, yy - 5, tickFont, Color.GRAY);
			g.getChildren().add(lab);
		}
		g.getChildren().addAll(label("X", px(gridW) + 15, py(0) - 10, Font.font(14), Color.BLACK),
				label("Y", px(0) - 20, py(gridH) + 15, Font.font(14), Color.BLACK));
		root.getChildren().add(g);
	}

	/** Color por material (ajusta a tu MaterialsDB). */
	private Color colorForMaterial(core.material.Material m) {
		if (m == null)
			return Color.DARKGRAY;
		String name = m.getName().toLowerCase();
		if (name.contains("brick"))
			return Color.SADDLEBROWN;
		if (name.contains("concrete"))
			return Color.DIMGRAY;
		if (name.contains("drywall"))
			return Color.LIGHTSLATEGRAY;
		if (name.contains("glass"))
			return Color.DEEPSKYBLUE;
		if (name.contains("metal"))
			return Color.DARKRED;
		return Color.DARKGRAY;
	}

	/** Grosor visual (no físico). Manténlo pequeño para que no tape la grid. */
	private double strokeForMaterial(core.material.Material m, double thicknessCm) {
		// puedes mapear el grosor real si quieres; aquí un valor fijo agradable
		return 3.0;
	}

	private void drawHubAndSensors(Pane root) {
		// limpia hub/sensores previos
		root.getChildren().removeIf(n -> "hubSensors".equals(n.getId()));
		Group g = new Group();
		g.setId("hubSensors");

		if (config.hasHub()) {
			Hub h = config.getHub();
			double cx = px(h.getX());
			double cy = py(h.getY());

			hubBox = new Rectangle(cx - 7, cy - 7, 14, 14);
			hubBox.setFill(Color.CRIMSON);
			hubBox.setStroke(Color.DARKRED);
			hubBox.setStrokeWidth(2);

			Label tag = label("HUB", cx + 10, cy - 10, Font.font(12), Color.CRIMSON);
			hubTooltip = new Tooltip(String.format("Hub %s (%d,%d)\nRx: —", h.getId(), h.getX(), h.getY()));
			Tooltip.install(hubBox, hubTooltip);
			hubTooltip.setText(String.format("Hub %s (%d,%d)\nSenal recibida: pendiente", h.getId(), h.getX(), h.getY()));

			hubBox.setOnMouseClicked(e -> {
				if (e.getButton() != MouseButton.PRIMARY) {
					return;
				}
				if (sensorSelectorCombo != null) {
					sensorSelectorCombo.getSelectionModel().clearSelection();
				}
				updateDevicePositionFields(h.getX(), h.getY());
				updateFloorPlanHint(String.format(Locale.US,
						"Hub seleccionado en (%d, %d). Puedes moverlo desde el mapa o ajustar su posicion.",
						h.getX(), h.getY()));
			});
			tag.setOnMouseClicked(e -> {
				if (e.getButton() != MouseButton.PRIMARY) {
					return;
				}
				if (sensorSelectorCombo != null) {
					sensorSelectorCombo.getSelectionModel().clearSelection();
				}
				updateDevicePositionFields(h.getX(), h.getY());
				updateFloorPlanHint(String.format(Locale.US,
						"Hub seleccionado en (%d, %d). Puedes moverlo desde el mapa o ajustar su posicion.",
						h.getX(), h.getY()));
			});
			hubNode = new Group(hubBox, tag);
			g.getChildren().add(hubNode);
		}

		for (Sensor s : config.getSensores()) {
			double cx = px(s.getX());
			double cy = py(s.getY());

			Circle dot = new Circle(cx, cy, 6, Color.DODGERBLUE);
			dot.setStroke(Color.DARKBLUE);

			// Menú contextual para tipo y cuadrante
			ContextMenu cm = new ContextMenu();
			Menu mType = new Menu("Tipo de antena");
			RadioMenuItem miOmni = new RadioMenuItem("Omnidireccional");
			RadioMenuItem miDir = new RadioMenuItem("Direccional (1 cuadrante)");
			ToggleGroup tg = new ToggleGroup();
			miOmni.setToggleGroup(tg);
			miDir.setToggleGroup(tg);
			miOmni.setSelected(s.getAntennaType() == Sensor.AntennaType.OMNI);
			miDir.setSelected(s.getAntennaType() == Sensor.AntennaType.DIRECTIONAL);
			miOmni.setOnAction(e -> {
				s.setAntennaType(Sensor.AntennaType.OMNI);
				repaintAll();
			});
			miDir.setOnAction(e -> {
				s.setAntennaType(Sensor.AntennaType.DIRECTIONAL);
				repaintAll();
			});
			mType.getItems().addAll(miOmni, miDir);
			cm.getItems().add(mType);

			dot.setOnMousePressed(e -> {
				if (e.isSecondaryButtonDown()) {
					cm.show(dot, e.getScreenX(), e.getScreenY());
				} else {
					cm.hide();
					if (sensorSelectorCombo != null) {
						sensorSelectorCombo.getSelectionModel().select(s);
					}
					updateDevicePositionFields(s.getX(), s.getY());
					updateFloorPlanHint(String.format(Locale.US,
							"Sensor %s seleccionado en (%d, %d).", s.getId(), s.getX(), s.getY()));
				}
			});

			Label lab = label(s.getNombre(), cx + 10, cy - 10, Font.font(12), Color.DARKBLUE);
			lab.setOnMouseClicked(e -> {
				if (e.getButton() != MouseButton.PRIMARY) {
					return;
				}
				if (sensorSelectorCombo != null) {
					sensorSelectorCombo.getSelectionModel().select(s);
				}
				updateDevicePositionFields(s.getX(), s.getY());
				updateFloorPlanHint(String.format(Locale.US,
						"Sensor %s seleccionado en (%d, %d).", s.getId(), s.getX(), s.getY()));
			});

			String tipText = String.format("%s\nPosicion: (%d,%d)\nPotencia Tx: %.1f dBm\nValor: %.1f", s.getNombre(), s.getX(),
					s.getY(), s.getTxDbm(), s.getValue());
			Tooltip tip = new Tooltip(tipText);
			Tooltip.install(dot, tip);

			g.getChildren().addAll(dot, lab);
		}
		Pane center = (Pane) ((BorderPane) rootGroup.getParent().getParent()).getCenter();
		center.getChildren().add(g);
	}

	// ============================
	// Edición de paredes (opcional)
	// ============================
	private void configureCanvasInteractions() {
		probeTip.setShowDelay(Duration.millis(80));

		gridCanvas.setOnMouseMoved(e -> {
			if (!isInsideEditableGrid(e.getX(), e.getY())) {
				Tooltip.uninstall(gridCanvas, probeTip);
				draftHoverGridX = Double.NaN;
				draftHoverGridY = Double.NaN;
				refreshPlanEditorOverlay();
				return;
			}

			draftHoverGridX = normalizeEditorCoordinate(toGridXd(e.getX()), gridW);
			draftHoverGridY = normalizeEditorCoordinate(toGridYd(e.getY()), gridH);
			refreshPlanEditorOverlay();
			updateProbeTooltip(e.getX(), e.getY());
		});

		gridCanvas.setOnMouseExited(e -> {
			Tooltip.uninstall(gridCanvas, probeTip);
			draftHoverGridX = Double.NaN;
			draftHoverGridY = Double.NaN;
			refreshPlanEditorOverlay();
		});

		gridCanvas.setOnMouseClicked(e -> {
			if (!isInsideEditableGrid(e.getX(), e.getY())) {
				return;
			}

			double gridX = normalizeEditorCoordinate(toGridXd(e.getX()), gridW);
			double gridY = normalizeEditorCoordinate(toGridYd(e.getY()), gridH);

			if (e.getButton() == MouseButton.SECONDARY) {
				Wall wall = findWallNear(px(gridX), py(gridY));
				if (wall != null) {
					selectWall(wall);
					showWallContextMenu(e.getScreenX(), e.getScreenY(), wall);
				}
				return;
			}

			if (e.getButton() != MouseButton.PRIMARY) {
				return;
			}

			handleFloorPlanPrimaryClick(gridX, gridY);
		});
	}

	private void handleFloorPlanPrimaryClick(double gridX, double gridY) {
		switch (floorPlanTool) {
		case SELECT -> {
			Wall wall = findWallNear(px(gridX), py(gridY));
			selectWall(wall);
			updateFloorPlanHint((wall == null)
					? "No hay ninguna pared en ese punto. Prueba con otro segmento."
					: "Pared seleccionada. Puedes editarla desde el panel o con clic derecho.");
		}
		case WALL -> {
			if (Double.isNaN(draftStartGridX) || Double.isNaN(draftStartGridY)) {
				draftStartGridX = gridX;
				draftStartGridY = gridY;
				draftHoverGridX = gridX;
				draftHoverGridY = gridY;
				selectWall(null);
				updateFloorPlanHint(String.format(Locale.US,
						"Punto inicial fijado en (%.1f, %.1f). Haz clic en el final del muro.",
						draftStartGridX, draftStartGridY));
				refreshPlanEditorOverlay();
				return;
			}

			if (Math.hypot(gridX - draftStartGridX, gridY - draftStartGridY) < 1e-6) {
				updateFloorPlanHint("El segundo punto debe ser distinto del primero.");
				return;
			}

			addWallsToPlan(List.of(new Wall(draftStartGridX, draftStartGridY, gridX, gridY,
					floorPlanMaterial, floorPlanThicknessCm)));
			resetFloorPlanDraft();
			updateFloorPlanHint("Muro creado. Puedes seguir dibujando.");
		}
		case ROOM -> {
			if (Double.isNaN(draftStartGridX) || Double.isNaN(draftStartGridY)) {
				draftStartGridX = gridX;
				draftStartGridY = gridY;
				draftHoverGridX = gridX;
				draftHoverGridY = gridY;
				selectWall(null);
				updateFloorPlanHint(String.format(Locale.US,
						"Primera esquina fijada en (%.1f, %.1f). Haz clic en la esquina opuesta.",
						draftStartGridX, draftStartGridY));
				refreshPlanEditorOverlay();
				return;
			}

			addRoomRectangle(draftStartGridX, draftStartGridY, gridX, gridY,
					floorPlanMaterial, floorPlanThicknessCm);
			resetFloorPlanDraft();
			updateFloorPlanHint("Habitacion creada. Puedes seguir anadiendo espacios.");
		}
		case SENSOR -> {
			int x = (int) Math.round(gridX);
			int y = (int) Math.round(gridY);
			Sensor sensor = createSensorAt(x, y);
			updateFloorPlanHint(String.format(Locale.US,
					"Sensor %s colocado en (%d, %d). Haz clic para seguir anadiendo sensores.",
					sensor.getId(), x, y));
		}
		case HUB -> {
			int x = (int) Math.round(gridX);
			int y = (int) Math.round(gridY);
			placeHubAt(x, y);
			updateFloorPlanHint(String.format(Locale.US,
					"Hub colocado en (%d, %d). Haz clic para recolocarlo o cambia de herramienta.",
					x, y));
		}
		case DELETE -> {
			Wall wall = findWallNear(px(gridX), py(gridY));
			if (wall == null) {
				updateFloorPlanHint("No hay una pared en ese punto para borrar.");
				return;
			}
			removeWallFromPlan(wall, true);
		}
		default -> {
		}
		}
	}

	private void selectWall(Wall wall) {
		selectedWall = wall;
		updateFloorPlanSelectionLabel();
		refreshPlanEditorOverlay();
	}

	private void showWallContextMenu(double screenX, double screenY, Wall wall) {
		ContextMenu cm = new ContextMenu();

		MenuItem miSelect = new MenuItem("Seleccionar pared");
		miSelect.setOnAction(e -> {
			selectWall(wall);
			updateFloorPlanHint("Pared seleccionada desde el menu contextual.");
		});

		Menu mMat = new Menu("Material");
		for (MaterialChoice choice : buildFloorPlanMaterials()) {
			MenuItem item = new MenuItem(choice.label());
			item.setOnAction(e -> {
				wall.setMaterial(choice.material());
				floorPlanMaterial = choice.material();
				syncWallsWithEnvironment();
				updateFloorPlanSelectionLabel();
				repaintAll();
				updateFloorPlanHint("Material actualizado en la pared seleccionada.");
			});
			mMat.getItems().add(item);
		}

		Menu mThickness = new Menu("Grosor");
		for (double thickness : List.of(4.0, 8.0, 12.0, 20.0)) {
			MenuItem item = new MenuItem(String.format(Locale.US, "%.0f cm", thickness));
			item.setOnAction(e -> {
				wall.setThicknessCm(thickness);
				floorPlanThicknessCm = thickness;
				syncWallsWithEnvironment();
				updateFloorPlanSelectionLabel();
				repaintAll();
				updateFloorPlanHint("Grosor actualizado en la pared seleccionada.");
			});
			mThickness.getItems().add(item);
		}

		MenuItem miDelete = new MenuItem("Borrar pared");
		miDelete.setOnAction(e -> removeWallFromPlan(wall, true));

		cm.getItems().addAll(miSelect, mMat, mThickness, new SeparatorMenuItem(), miDelete);
		cm.show(gridCanvas, screenX, screenY);
	}

	private void refreshPlanEditorOverlay() {
		if (editorOverlayLayer == null) {
			return;
		}
		editorOverlayLayer.getChildren().clear();

		if (selectedWall != null) {
			highlightWallOnOverlay(selectedWall, Color.web("#f59e0b"), 7.0, 0.75, false);
		}

		if (floorPlanTool == FloorPlanTool.DELETE && !Double.isNaN(draftHoverGridX) && !Double.isNaN(draftHoverGridY)) {
			Wall hoverWall = findWallNear(px(draftHoverGridX), py(draftHoverGridY));
			if (hoverWall != null && hoverWall != selectedWall) {
				highlightWallOnOverlay(hoverWall, Color.web("#dc2626"), 7.0, 0.55, false);
			}
		}

		if ((floorPlanTool == FloorPlanTool.SENSOR || floorPlanTool == FloorPlanTool.HUB)
				&& !Double.isNaN(draftHoverGridX) && !Double.isNaN(draftHoverGridY)) {
			if (floorPlanTool == FloorPlanTool.SENSOR) {
				Circle preview = createEditorAnchor(draftHoverGridX, draftHoverGridY, Color.web("#2563eb"));
				preview.setRadius(6.0);
				editorOverlayLayer.getChildren().add(preview);
			} else {
				Rectangle preview = new Rectangle(px(draftHoverGridX) - 8, py(draftHoverGridY) - 8, 16, 16);
				preview.setFill(Color.web("#dc2626", 0.20));
				preview.setStroke(Color.web("#dc2626"));
				preview.setStrokeWidth(2.5);
				editorOverlayLayer.getChildren().add(preview);
			}
		}

		if (Double.isNaN(draftStartGridX) || Double.isNaN(draftStartGridY)
				|| Double.isNaN(draftHoverGridX) || Double.isNaN(draftHoverGridY)) {
			return;
		}

		if (floorPlanTool == FloorPlanTool.WALL) {
			Line preview = new Line(px(draftStartGridX), py(draftStartGridY), px(draftHoverGridX), py(draftHoverGridY));
			preview.setStroke(Color.web("#1f6feb"));
			preview.setStrokeWidth(4.0);
			preview.setOpacity(0.75);
			preview.getStrokeDashArray().addAll(10.0, 6.0);
			editorOverlayLayer.getChildren().add(preview);
			editorOverlayLayer.getChildren().addAll(
					createEditorAnchor(draftStartGridX, draftStartGridY, Color.web("#1f6feb")),
					createEditorAnchor(draftHoverGridX, draftHoverGridY, Color.web("#1f6feb")));
			return;
		}

		if (floorPlanTool == FloorPlanTool.ROOM) {
			double minX = Math.min(draftStartGridX, draftHoverGridX);
			double minY = Math.min(draftStartGridY, draftHoverGridY);
			double maxX = Math.max(draftStartGridX, draftHoverGridX);
			double maxY = Math.max(draftStartGridY, draftHoverGridY);

			Rectangle preview = new Rectangle(px(minX), py(maxY), (maxX - minX) * cellSizePx, (maxY - minY) * cellSizePx);
			preview.setFill(Color.web("#1f6feb", 0.12));
			preview.setStroke(Color.web("#1f6feb"));
			preview.setStrokeWidth(3.0);
			preview.getStrokeDashArray().addAll(10.0, 6.0);
			editorOverlayLayer.getChildren().add(preview);
			editorOverlayLayer.getChildren().addAll(
					createEditorAnchor(minX, minY, Color.web("#1f6feb")),
					createEditorAnchor(maxX, maxY, Color.web("#1f6feb")));
		}
	}

	private void highlightWallOnOverlay(Wall wall, Color color, double strokeWidth, double opacity, boolean dashed) {
		Line highlight = new Line(px(wall.getX1()), py(wall.getY1()), px(wall.getX2()), py(wall.getY2()));
		highlight.setStroke(color);
		highlight.setStrokeWidth(strokeWidth);
		highlight.setOpacity(opacity);
		if (dashed) {
			highlight.getStrokeDashArray().addAll(10.0, 6.0);
		}
		editorOverlayLayer.getChildren().add(highlight);
	}

	private Circle createEditorAnchor(double gridX, double gridY, Color color) {
		Circle anchor = new Circle(px(gridX), py(gridY), 4.5, color);
		anchor.setStroke(Color.WHITE);
		anchor.setStrokeWidth(1.5);
		return anchor;
	}

	private boolean isInsideEditableGrid(double pixelX, double pixelY) {
		double gridX = toGridXd(pixelX);
		double gridY = toGridYd(pixelY);
		return gridX >= 0.0 && gridY >= 0.0 && gridX <= gridW && gridY <= gridH;
	}

	private double normalizeEditorCoordinate(double coordinate, double maxValue) {
		double normalized = Math.max(0.0, Math.min(maxValue, coordinate));
		return floorPlanSnapToGrid ? Math.rint(normalized) : normalized;
	}

	private void updateProbeTooltip(double pixelX, double pixelY) {
		int gx = (int) toGridXd(pixelX);
		int gy = (int) toGridYd(pixelY);
		if (gx < 0 || gy < 0 || gx >= gridW || gy >= gridH) {
			Tooltip.uninstall(gridCanvas, probeTip);
			return;
		}

		CellResult cell = (lastHeatmapResult != null)
				? lastHeatmapResult.getCell(gx, gy)
				: IndoorWaveEngine.computeCell(env, config.getSensores(), gx + 0.5, gy + 0.5, buildReceiverSettings());
		if (cell == null) {
			Tooltip.uninstall(gridCanvas, probeTip);
			return;
		}

		String strongestPaths = cell.contributions().stream().limit(3).map(PathContribution::summary)
				.reduce((a, b) -> a + "\n" + b).orElse("Sin trayectorias destacadas");

		probeTip.setText(String.format(Locale.US,
				"Celda (%d,%d)\nSensor dominante: %s\nPotencia total: %.1f dBm\nSenal util: %.1f dBm\n"
						+ "Interferencia: %.1f dBm\nRuido: %.1f dBm\nSNR: %.1f dB\nSINR: %.1f dB\n"
						+ "BER: %.2e\nCapacidad: %.2f Mbps\nMargen: %.1f dB\nFase: %.1f deg\n"
						+ "Trayectorias: %d\n%s",
				gx, gy, cell.dominantSensorId(), cell.totalPowerDbm(), cell.signalPowerDbm(),
				cell.interferencePowerDbm(), cell.noiseDbm(), cell.snrDb(), cell.sinrDb(), cell.ber(),
				cell.capacityMbps(), cell.linkMarginDb(), Math.toDegrees(cell.fieldPhaseRad()), cell.pathCount(),
				strongestPaths));
		Tooltip.install(gridCanvas, probeTip);
	}

	private Wall findWallNear(double px, double py) {
		final double tol = 6.0; // píxeles
		for (Wall w : walls) {
			double d = distPointToSegment(px, py, px(w.getX1()), py(w.getY1()), px(w.getX2()), py(w.getY2()));
			if (d <= tol)
				return w;
		}
		return null;
	}

	// ============================
	// Tooltip de sonda (opcional)
	// ============================
	private final Tooltip probeTip = new Tooltip();

	// ============================
	// Ventana de parámetros (ajustada a env)
	// ============================
	private void showSummaryWindow() {
		Stage paramStage = new Stage();
		paramStage.setTitle("Resumen de parametros");

		CellResult hubCell = computeHubCellForSummary();

		TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabs.getTabs().addAll(
				createSummaryTab("General", buildGeneralSummaryPage(hubCell)),
				createSummaryTab("Hub", buildHubSummaryPage(hubCell)),
				createSummaryTab("Sensores", buildSensorsSummaryPage()),
				createSummaryTab("Validacion", buildValidationSummaryPage(hubCell))
		);

		Scene scene = new Scene(tabs, 920, 740);
		paramStage.setScene(scene);
		paramStage.show();
	}

	private Tab createSummaryTab(String title, Node content) {
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scroll.setStyle("-fx-background-color: #f4f6f8; -fx-background: #f4f6f8;");

		Tab tab = new Tab(title, scroll);
		tab.setClosable(false);
		return tab;
	}

	private VBox buildGeneralSummaryPage(CellResult hubCell) {
		VBox page = createSummaryPage(
				"Resumen de simulacion",
				"Vista global del escenario, del modelo de propagacion y de los indicadores principales."
		);

		GridPane scenarioGrid = createSummaryGrid();
		addSummaryRow(scenarioGrid, 0, "Escenario activo", currentScenarioName());
		addSummaryRow(scenarioGrid, 1, "Descripcion", currentScenarioDescription());
		addSummaryRow(scenarioGrid, 2, "Paredes", Integer.toString(env.getWalls().size()));
		addSummaryRow(scenarioGrid, 3, "Materiales", buildMaterialBreakdown());
		addSummaryRow(scenarioGrid, 4, "Sensores", Integer.toString(config.getSensores().size()));
		addSummaryRow(scenarioGrid, 5, "Hub", config.hasHub()
				? config.getHub().getId() + " en (" + config.getHub().getX() + ", " + config.getHub().getY() + ")"
				: "No colocado");
		page.getChildren().add(createSummaryCard("Escenario", "Configuracion actual del plano interior.", scenarioGrid));

		FlowPane quickMetrics = new FlowPane(12, 12);
		quickMetrics.getChildren().addAll(
				createMetricTile("Suelo de ruido", formatDbm(env.noiseFloorDbm()), "#0f766e"),
				createMetricTile("Sensibilidad Rx", formatDbm(simulationSettings.getReceiverSensitivityDbm()), "#7c3aed"),
				createMetricTile("Longitud de onda", formatMeters(env.wavelengthMeters()), "#1d4ed8"),
				createMetricTile("Alpha medio", formatDb(env.getAlphaDbPerMeter()) + "/m", "#92400e")
		);
		if (hubCell != null) {
			quickMetrics.getChildren().addAll(
					createMetricTile("SINR en hub", formatDb(hubCell.sinrDb()), "#2563eb"),
					createMetricTile("Capacidad", formatMbps(hubCell.capacityMbps()), "#b45309")
			);
		}
		page.getChildren().add(createSummaryCard("Indicadores rapidos", "", quickMetrics));

		GridPane radioGrid = createSummaryGrid();
		addSummaryRow(radioGrid, 0, "Frecuencia", formatFrequency(env.getFreqMHz()));
		addSummaryRow(radioGrid, 1, "Longitud de onda", formatMeters(env.wavelengthMeters()));
		addSummaryRow(radioGrid, 2, "Ancho de banda", formatHertz(env.getBandwidthHz()));
		addSummaryRow(radioGrid, 3, "Figura de ruido", formatDb(env.getNoiseFigureDb()));
		addSummaryRow(radioGrid, 4, "Suelo de ruido", formatDbm(env.noiseFloorDbm()));
		addSummaryRow(radioGrid, 5, "Atenuacion por metro", formatDb(env.getAlphaDbPerMeter()) + "/m");
		addSummaryRow(radioGrid, 6, "Ganancia de sistema", formatDb(env.getSystemGainDb()));
		addSummaryRow(radioGrid, 7, "Sensibilidad Rx", formatDbm(simulationSettings.getReceiverSensitivityDbm()));
		page.getChildren().add(createSummaryCard("Radio y ruido", "Parametros fisicos que condicionan todas las metricas.", radioGrid));
		page.getChildren().add(createSummaryCard("Referencias de calculo",
				"Esta guia resume como interpretar los numeros mas importantes del simulador.",
				createInfoLabel(
						"Ruido = -174 dBm/Hz + 10 log10(BW) + NF. " +
						"SNR = senal / ruido. " +
						"SINR = senal / (ruido + interferencia). " +
						"Capacidad = BW * log2(1 + SINR lineal). " +
						"Margen = senal util - sensibilidad del receptor.")));

		GridPane modelGrid = createSummaryGrid();
		addSummaryRow(modelGrid, 0, "Modo de propagacion", simulationSettings.getPropagationMode().toString());
		addSummaryRow(modelGrid, 1, "Metrica del mapa", simulationSettings.getMapMetric().toString());
		addSummaryRow(modelGrid, 2, "Fading", simulationSettings.getFadingModel().toString());
		addSummaryRow(modelGrid, 3, "Exponente indoor", String.format(Locale.US, "%.2f", simulationSettings.getLogDistanceExponent()));
		addSummaryRow(modelGrid, 4, "K-factor Rician", formatDb(simulationSettings.getRicianKFactorDb()));
		addSummaryRow(modelGrid, 5, "Difraccion", simulationSettings.isDiffractionEnabled() ? "Activa" : "Inactiva");
		addSummaryRow(modelGrid, 6, "Dispersion", simulationSettings.isScatteringEnabled() ? "Activa" : "Inactiva");
		addSummaryRow(modelGrid, 7, "Calculo en paralelo", simulationSettings.isParallelComputation() ? "Si" : "No");
		addSummaryRow(modelGrid, 8, "Umbral de culling", formatDbm(simulationSettings.getCullingThresholdDbm()));
		addSummaryRow(modelGrid, 9, "Max. reflexiones", Integer.toString(simulationSettings.getMaxReflectionPaths()));
		addSummaryRow(modelGrid, 10, "Max. difracciones", Integer.toString(simulationSettings.getMaxDiffractionPaths()));
		addSummaryRow(modelGrid, 11, "Max. dispersiones", Integer.toString(simulationSettings.getMaxScatteringPaths()));
		page.getChildren().add(createSummaryCard("Modelo de propagacion", "Resumen del motor de simulacion que alimenta el heatmap y los enlaces.", modelGrid));

		return page;
	}

	private VBox buildHubSummaryPage(CellResult hubCell) {
		VBox page = createSummaryPage(
				"Resumen del hub",
				"Estado combinado en el punto receptor. Si hay varios sensores, la senal dominante se compara con la interferencia del resto."
		);

		if (!config.hasHub()) {
			page.getChildren().add(createSummaryCard("Hub no disponible",
					"Coloca un hub en el plano para ver el resumen combinado y las comprobaciones del enlace.",
					createInfoLabel("Ahora mismo solo hay resumen global del escenario y de los sensores.")));
			return page;
		}

		Hub hub = config.getHub();
		GridPane hubGrid = createSummaryGrid();
		addSummaryRow(hubGrid, 0, "Id", hub.getId());
		addSummaryRow(hubGrid, 1, "Nombre", hub.getNombre());
		addSummaryRow(hubGrid, 2, "Posicion", "(" + hub.getX() + ", " + hub.getY() + ")");
		addSummaryRow(hubGrid, 3, "Ganancia Rx", formatDb(hub.getGrDb()));
		addSummaryRow(hubGrid, 4, "Polarizacion", formatDeg(hub.getPolarizationDeg()));
		addSummaryRow(hubGrid, 5, "Sensibilidad", formatDbm(simulationSettings.getReceiverSensitivityDbm()));
		page.getChildren().add(createSummaryCard("Configuracion del receptor", "", hubGrid));

		if (hubCell == null) {
			page.getChildren().add(createSummaryCard("Sin datos de enlace",
					"No hay sensores activos o no se ha podido calcular la celda del hub.",
					createInfoLabel("Anade al menos un sensor para ver la mezcla de potencia, SINR y capacidad.")));
			return page;
		}

		FlowPane metricStrip = new FlowPane(12, 12);
		metricStrip.getChildren().addAll(
				createMetricTile("Potencia total", formatDbm(hubCell.totalPowerDbm()), "#1d4ed8"),
				createMetricTile("SINR", formatDb(hubCell.sinrDb()), "#0f766e"),
				createMetricTile("BER", formatBer(hubCell.ber()), "#7c3aed"),
				createMetricTile("Capacidad", formatMbps(hubCell.capacityMbps()), "#b45309"),
				createMetricTile("Margen", formatDb(hubCell.linkMarginDb()), "#be123c")
		);
		page.getChildren().add(createSummaryCard("Estado rapido", "Lectura directa de las metricas mas importantes en el hub.", metricStrip));

		GridPane metricsGrid = createSummaryGrid();
		addSummaryRow(metricsGrid, 0, "Sensor dominante", hubCell.dominantSensorId());
		addSummaryRow(metricsGrid, 1, "Potencia total", formatDbm(hubCell.totalPowerDbm()));
		addSummaryRow(metricsGrid, 2, "Senal util", formatDbm(hubCell.signalPowerDbm()));
		addSummaryRow(metricsGrid, 3, "Interferencia", formatDbm(hubCell.interferencePowerDbm()));
		addSummaryRow(metricsGrid, 4, "Ruido", formatDbm(hubCell.noiseDbm()));
		addSummaryRow(metricsGrid, 5, "SNR", formatDb(hubCell.snrDb()));
		addSummaryRow(metricsGrid, 6, "SINR", formatDb(hubCell.sinrDb()));
		addSummaryRow(metricsGrid, 7, "BER", formatBer(hubCell.ber()));
		addSummaryRow(metricsGrid, 8, "Capacidad", formatMbps(hubCell.capacityMbps()));
		addSummaryRow(metricsGrid, 9, "Margen de enlace", formatDb(hubCell.linkMarginDb()));
		addSummaryRow(metricsGrid, 10, "Fase del campo", formatDeg(Math.toDegrees(hubCell.fieldPhaseRad())));
		addSummaryRow(metricsGrid, 11, "Trayectorias consideradas", Integer.toString(hubCell.pathCount()));
		page.getChildren().add(createSummaryCard("Detalle del enlace combinado", "", metricsGrid));

		page.getChildren().add(createSummaryCard("Trayectorias dominantes",
				"Las trayectorias se ordenan por potencia recibida. Esto ayuda a entender si manda la linea directa, una reflexion o la difraccion.",
				createContributionList(hubCell.contributions(), 8)));

		return page;
	}

	private VBox buildSensorsSummaryPage() {
		VBox page = createSummaryPage(
				"Resumen por sensor",
				"Cada bloque combina configuracion de antena y, si hay hub, el enlace individual hasta el receptor."
		);

		if (config.getSensores().isEmpty()) {
			page.getChildren().add(createSummaryCard("Sin sensores",
					"No hay dispositivos emisores colocados en el plano.",
					createInfoLabel("Anade un sensor para ver potencia transmitida, patron y metricas de enlace.")));
			return page;
		}

		for (Sensor sensor : config.getSensores()) {
			VBox sensorContent = new VBox(12);

			GridPane configGrid = createSummaryGrid();
			addSummaryRow(configGrid, 0, "Id", sensor.getId());
			addSummaryRow(configGrid, 1, "Nombre", sensor.getNombre());
			addSummaryRow(configGrid, 2, "Posicion", "(" + sensor.getX() + ", " + sensor.getY() + ")");
			addSummaryRow(configGrid, 3, "Antena", sensor.getAntennaType().toString());
			addSummaryRow(configGrid, 4, "Orientacion", formatDeg(sensor.getOrientationDeg()));
			addSummaryRow(configGrid, 5, "Apertura", formatDeg(sensor.getBeamwidthDeg()));
			addSummaryRow(configGrid, 6, "Potencia Tx", formatDbm(sensor.getTxDbm()));
			addSummaryRow(configGrid, 7, "Ganancia Tx", formatDb(sensor.getTxGainDb()));
			addSummaryRow(configGrid, 8, "Patron", String.format(Locale.US, "%.2f", sensor.getPatternSharpness()));
			addSummaryRow(configGrid, 9, "Polarizacion", formatDeg(sensor.getPolarizationDeg()));
			sensorContent.getChildren().add(createSummaryCard("Configuracion", "", configGrid));

			if (config.hasHub()) {
				CellResult linkCell = computeSensorLinkForSummary(sensor);
				FlowPane linkStrip = new FlowPane(12, 12);
				linkStrip.getChildren().addAll(
						createMetricTile("Potencia Rx", formatDbm(linkCell.totalPowerDbm()), "#1d4ed8"),
						createMetricTile("SNR", formatDb(linkCell.snrDb()), "#0f766e"),
						createMetricTile("Margen", formatDb(linkCell.linkMarginDb()), "#be123c"),
						createMetricTile("Capacidad", formatMbps(linkCell.capacityMbps()), "#b45309")
				);
				sensorContent.getChildren().add(createSummaryCard("Enlace con el hub", "", linkStrip));

				GridPane linkGrid = createSummaryGrid();
				addSummaryRow(linkGrid, 0, "Distancia al hub",
						formatMeters(Math.hypot(config.getHub().getX() - sensor.getX(), config.getHub().getY() - sensor.getY())));
				addSummaryRow(linkGrid, 1, "Potencia recibida", formatDbm(linkCell.totalPowerDbm()));
				addSummaryRow(linkGrid, 2, "Ruido", formatDbm(linkCell.noiseDbm()));
				addSummaryRow(linkGrid, 3, "SNR", formatDb(linkCell.snrDb()));
				addSummaryRow(linkGrid, 4, "SINR", formatDb(linkCell.sinrDb()));
				addSummaryRow(linkGrid, 5, "BER", formatBer(linkCell.ber()));
				addSummaryRow(linkGrid, 6, "Capacidad", formatMbps(linkCell.capacityMbps()));
				addSummaryRow(linkGrid, 7, "Margen de enlace", formatDb(linkCell.linkMarginDb()));
				addSummaryRow(linkGrid, 8, "Trayectorias", Integer.toString(linkCell.pathCount()));
				addSummaryRow(linkGrid, 9, "Trayectoria mas fuerte",
						linkCell.contributions().isEmpty() ? "Sin trayectorias" : describeContribution(linkCell.contributions().getFirst()));
				sensorContent.getChildren().add(createSummaryCard("Detalle del enlace", "", linkGrid));

				sensorContent.getChildren().add(createSummaryCard("Trayectorias del sensor",
						"Vista de las contribuciones individuales que alcanzan el hub desde este emisor.",
						createContributionList(linkCell.contributions(), 6)));
			} else {
				sensorContent.getChildren().add(createSummaryCard("Enlace con el hub",
						"No hay hub colocado, por lo que solo se muestra la configuracion del emisor.",
						createInfoLabel("Coloca un hub para ver potencia recibida, BER, capacidad y margen de enlace.")));
			}

			page.getChildren().add(createSummaryCard(
					sensor.getId() + " - " + sensor.getNombre(),
					"Resumen del emisor y su comportamiento frente al receptor activo.",
					sensorContent));
		}

		return page;
	}

	private VBox buildValidationSummaryPage(CellResult hubCell) {
		VBox page = createSummaryPage(
				"Validacion numerica",
				"Estas comprobaciones contrastan lo que ensena la interfaz con las mismas formulas del motor: ruido termico, SNR, SINR, BER, Shannon y margen."
		);

		page.getChildren().add(createSummaryCard("Criterio de validacion",
				"Cada fila compara valor mostrado frente a valor esperado. Si ves todo en OK, la ventana esta reflejando bien los calculos del motor.",
				createInfoLabel("En modo Ondas, la potencia total es coherente en fase, pero la interferencia que usa la SINR se mantiene agregada de forma incoherente, igual que en el motor.")));

		page.getChildren().add(createValidationCard("Entorno y radio", buildEnvironmentChecks()));

		if (hubCell != null) {
			page.getChildren().add(createValidationCard("Hub combinado", buildCellChecks(hubCell, buildReceiverSettings())));
			List<SummaryCheck> aggregateChecks = buildHubAggregateChecks(hubCell);
			if (!aggregateChecks.isEmpty()) {
				page.getChildren().add(createValidationCard("Mezcla de sensores", aggregateChecks));
			}
		}

		if (!config.hasHub()) {
			page.getChildren().add(createSummaryCard("Sin hub",
					"No se pueden validar enlaces individuales porque falta el receptor.",
					createInfoLabel("Coloca un hub para activar la validacion de SNR, BER, capacidad y margen por enlace.")));
			return page;
		}

		for (Sensor sensor : config.getSensores()) {
			page.getChildren().add(createValidationCard(
					sensor.getId() + " - " + sensor.getNombre(),
					buildCellChecks(computeSensorLinkForSummary(sensor), buildReceiverSettings())));
		}

		return page;
	}

	private VBox createSummaryPage(String title, String subtitle) {
		Label header = new Label(title);
		header.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #16212c;");

		Label sub = new Label(subtitle);
		sub.setWrapText(true);
		sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f6b76;");

		VBox page = new VBox(16, header, sub);
		page.setPadding(new Insets(18));
		page.setFillWidth(true);
		page.setStyle("-fx-background-color: #f4f6f8;");
		return page;
	}

	private VBox createSummaryCard(String title, String subtitle, Node content) {
		Label header = new Label(title);
		header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #16212c;");

		VBox box = new VBox(12);
		box.getChildren().add(header);

		if (subtitle != null && !subtitle.isBlank()) {
			Label sub = new Label(subtitle);
			sub.setWrapText(true);
			sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f6b76;");
			box.getChildren().add(sub);
		}

		box.getChildren().add(content);
		box.setPadding(new Insets(14));
		box.setFillWidth(true);
		box.setStyle("-fx-background-color: white; -fx-background-radius: 14; "
				+ "-fx-border-color: #d8e0e8; -fx-border-radius: 14;");
		return box;
	}

	private GridPane createSummaryGrid() {
		GridPane grid = new GridPane();
		grid.setHgap(12);
		grid.setVgap(10);

		ColumnConstraints labelCol = new ColumnConstraints();
		labelCol.setMinWidth(180);
		labelCol.setPrefWidth(190);
		ColumnConstraints valueCol = new ColumnConstraints();
		valueCol.setHgrow(Priority.ALWAYS);
		valueCol.setFillWidth(true);
		grid.getColumnConstraints().addAll(labelCol, valueCol);
		return grid;
	}

	private void addSummaryRow(GridPane grid, int row, String labelText, String valueText) {
		Label label = new Label(labelText);
		label.setWrapText(true);
		label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #31414f;");

		Label value = new Label(valueText);
		value.setWrapText(true);
		value.setMaxWidth(Double.MAX_VALUE);
		value.setStyle("-fx-font-size: 12px; -fx-text-fill: #102a43;");

		grid.add(label, 0, row);
		grid.add(value, 1, row);
	}

	private VBox createMetricTile(String labelText, String valueText, String accentColor) {
		Label label = new Label(labelText);
		label.setWrapText(true);
		label.setStyle("-fx-font-size: 11px; -fx-text-fill: #5f6b76;");

		Label value = new Label(valueText);
		value.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");

		VBox tile = new VBox(6, label, value);
		tile.setPadding(new Insets(12));
		tile.setPrefWidth(150);
		tile.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 12; "
				+ "-fx-border-color: #d8e0e8; -fx-border-radius: 12;");
		return tile;
	}

	private Label createInfoLabel(String text) {
		Label label = new Label(text);
		label.setWrapText(true);
		label.setStyle("-fx-font-size: 12px; -fx-text-fill: #425466;");
		return label;
	}

	private VBox createContributionList(List<PathContribution> contributions, int limit) {
		VBox box = new VBox(8);
		if (contributions.isEmpty()) {
			box.getChildren().add(createInfoLabel("Sin trayectorias registradas para este punto."));
			return box;
		}

		int count = Math.min(limit, contributions.size());
		for (int i = 0; i < count; i++) {
			PathContribution contribution = contributions.get(i);
			Label item = new Label((i + 1) + ". " + describeContribution(contribution));
			item.setWrapText(true);
			item.setStyle("-fx-font-size: 12px; -fx-text-fill: #102a43;");
			box.getChildren().add(item);
		}
		return box;
	}

	private String describeContribution(PathContribution contribution) {
		return String.format(Locale.US,
				"%s | %.1f dBm | %.2f m | ganancia %.1f dB | perdidas extra %.1f dB",
				contribution.type(),
				contribution.powerDbm(),
				contribution.distanceMeters(),
				contribution.txGainDb(),
				contribution.extraLossDb());
	}

	private VBox createValidationCard(String title, List<SummaryCheck> checks) {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(8);

		ColumnConstraints statusCol = new ColumnConstraints();
		statusCol.setMinWidth(70);
		ColumnConstraints labelCol = new ColumnConstraints();
		labelCol.setMinWidth(170);
		ColumnConstraints actualCol = new ColumnConstraints();
		actualCol.setHgrow(Priority.ALWAYS);
		ColumnConstraints expectedCol = new ColumnConstraints();
		expectedCol.setHgrow(Priority.ALWAYS);
		grid.getColumnConstraints().addAll(statusCol, labelCol, actualCol, expectedCol);

		addValidationHeader(grid, 0, "Estado");
		addValidationHeader(grid, 1, "Parametro");
		addValidationHeader(grid, 2, "Mostrado");
		addValidationHeader(grid, 3, "Esperado");

		int row = 1;
		for (SummaryCheck check : checks) {
			Label status = new Label(check.ok() ? "OK" : "Revisar");
			status.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white; "
					+ "-fx-background-color: " + (check.ok() ? "#0f766e" : "#b91c1c")
					+ "; -fx-background-radius: 999; -fx-padding: 4 8 4 8;");

			Label label = new Label(check.label());
			label.setStyle("-fx-font-size: 12px; -fx-text-fill: #102a43;");

			Label actual = new Label(check.actual());
			actual.setWrapText(true);
			actual.setStyle("-fx-font-size: 12px; -fx-text-fill: #102a43;");

			Label expected = new Label(check.expected());
			expected.setWrapText(true);
			expected.setStyle("-fx-font-size: 12px; -fx-text-fill: #425466;");

			grid.add(status, 0, row);
			grid.add(label, 1, row);
			grid.add(actual, 2, row);
			grid.add(expected, 3, row);
			row++;
		}

		return createSummaryCard(title,
				"Comprobacion interna de formulas del motor para este punto de recepcion.",
				grid);
	}

	private void addValidationHeader(GridPane grid, int column, String text) {
		Label label = new Label(text);
		label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #5f6b76;");
		grid.add(label, column, 0);
	}

	private List<SummaryCheck> buildCellChecks(CellResult cell, SimulationSettings settings) {
		List<SummaryCheck> checks = new ArrayList<>();

		double expectedNoise = env.noiseFloorDbm();
		double expectedSnr = cell.signalPowerDbm() - cell.noiseDbm();
		double signalMw = Propagation.dbmToMilliwatt(cell.signalPowerDbm());
		double noiseMw = Propagation.dbmToMilliwatt(cell.noiseDbm());
		double interferenceMw = Propagation.dbmToMilliwatt(cell.interferencePowerDbm());
		double expectedSinr = Propagation.linearRatioToDb(signalMw / Math.max(noiseMw + interferenceMw, 1e-15));
		double expectedBer = Propagation.bpskBer(cell.sinrDb());
		double expectedCapacity = Propagation.shannonCapacityMbps(cell.sinrDb(), env.getBandwidthHz());
		double expectedMargin = cell.signalPowerDbm() - settings.getReceiverSensitivityDbm();

		checks.add(new SummaryCheck("Suelo de ruido", formatDbm(cell.noiseDbm()), formatDbm(expectedNoise),
				nearlyEqual(cell.noiseDbm(), expectedNoise, 0.01)));
		checks.add(new SummaryCheck("SNR", formatDb(cell.snrDb()), formatDb(expectedSnr),
				nearlyEqual(cell.snrDb(), expectedSnr, 0.01)));
		checks.add(new SummaryCheck("SINR", formatDb(cell.sinrDb()), formatDb(expectedSinr),
				nearlyEqual(cell.sinrDb(), expectedSinr, 0.01)));
		checks.add(new SummaryCheck("BER", formatBer(cell.ber()), formatBer(expectedBer),
				nearlyEqual(cell.ber(), expectedBer, 1e-12)));
		checks.add(new SummaryCheck("Capacidad", formatMbps(cell.capacityMbps()), formatMbps(expectedCapacity),
				nearlyEqual(cell.capacityMbps(), expectedCapacity, 0.01)));
		checks.add(new SummaryCheck("Margen", formatDb(cell.linkMarginDb()), formatDb(expectedMargin),
				nearlyEqual(cell.linkMarginDb(), expectedMargin, 0.01)));

		return checks;
	}

	private List<SummaryCheck> buildEnvironmentChecks() {
		List<SummaryCheck> checks = new ArrayList<>();

		double expectedFrequencyHz = env.getFreqMHz() * 1e6;
		double expectedWavelength = Environment.SPEED_OF_LIGHT_MS / expectedFrequencyHz;
		double expectedNoise = -174.0 + 10.0 * Math.log10(env.getBandwidthHz()) + env.getNoiseFigureDb();

		checks.add(new SummaryCheck("Frecuencia central", formatFrequency(env.getFreqMHz()),
				formatFrequency(expectedFrequencyHz / 1e6),
				nearlyEqual(env.getFreqMHz(), expectedFrequencyHz / 1e6, 1e-9)));
		checks.add(new SummaryCheck("Longitud de onda", formatMeters(env.wavelengthMeters()),
				formatMeters(expectedWavelength),
				nearlyEqual(env.wavelengthMeters(), expectedWavelength, 1e-9)));
		checks.add(new SummaryCheck("Suelo de ruido", formatDbm(env.noiseFloorDbm()),
				formatDbm(expectedNoise),
				nearlyEqual(env.noiseFloorDbm(), expectedNoise, 0.01)));

		return checks;
	}

	private List<SummaryCheck> buildHubAggregateChecks(CellResult hubCell) {
		if (!config.hasHub() || config.getSensores().isEmpty()) {
			return List.of();
		}

		List<SummaryCheck> checks = new ArrayList<>();
		double dominantPowerMw = 0.0;
		double interferenceMw = 0.0;
		String dominantSensorId = "Sin sensor";

		for (Sensor sensor : config.getSensores()) {
			CellResult sensorLink = computeSensorLinkForSummary(sensor);
			double sensorPowerMw = Propagation.dbmToMilliwatt(sensorLink.totalPowerDbm());

			if (sensorPowerMw > dominantPowerMw) {
				interferenceMw += dominantPowerMw;
				dominantPowerMw = sensorPowerMw;
				dominantSensorId = sensor.getId();
			} else {
				interferenceMw += sensorPowerMw;
			}
		}

		double expectedSignalDbm = Propagation.milliwattToDbm(Math.max(dominantPowerMw, 1e-15));
		double expectedInterferenceDbm = Propagation.milliwattToDbm(Math.max(interferenceMw, 1e-15));

		checks.add(new SummaryCheck("Sensor dominante", hubCell.dominantSensorId(),
				dominantSensorId,
				Objects.equals(hubCell.dominantSensorId(), dominantSensorId)));
		checks.add(new SummaryCheck("Senal dominante", formatDbm(hubCell.signalPowerDbm()),
				formatDbm(expectedSignalDbm),
				nearlyEqual(hubCell.signalPowerDbm(), expectedSignalDbm, 0.01)));
		checks.add(new SummaryCheck("Interferencia agregada", formatDbm(hubCell.interferencePowerDbm()),
				formatDbm(expectedInterferenceDbm),
				nearlyEqual(hubCell.interferencePowerDbm(), expectedInterferenceDbm, 0.01)));

		return checks;
	}

	private CellResult computeHubCellForSummary() {
		if (!config.hasHub()) {
			return null;
		}
		Hub hub = config.getHub();
		SimulationSettings hubSettings = buildReceiverSettings();
		hubSettings.setReceiverGainDb(hub.getGrDb());
		hubSettings.setReceiverPolarizationDeg(hub.getPolarizationDeg());
		return IndoorWaveEngine.computeCell(env, config.getSensores(), hub.getX(), hub.getY(), hubSettings);
	}

	private CellResult computeSensorLinkForSummary(Sensor sensor) {
		Hub hub = config.getHub();
		SimulationSettings singleSensorSettings = buildReceiverSettings();
		singleSensorSettings.setReceiverGainDb(hub.getGrDb());
		singleSensorSettings.setReceiverPolarizationDeg(hub.getPolarizationDeg());
		return IndoorWaveEngine.computeCell(env, List.of(sensor), hub.getX(), hub.getY(), singleSensorSettings);
	}

	private String buildMaterialBreakdown() {
		if (env.getWalls().isEmpty()) {
			return "Sin paredes";
		}

		Map<String, Integer> counts = new LinkedHashMap<>();
		for (Wall wall : env.getWalls()) {
			String name = (wall.getMaterial() == null) ? "Sin material" : wall.getMaterial().getName();
			counts.merge(name, 1, Integer::sum);
		}

		List<String> parts = new ArrayList<>();
		int shown = 0;
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			parts.add(entry.getKey() + ": " + entry.getValue());
			shown++;
			if (shown >= 4 && counts.size() > shown) {
				parts.add("...");
				break;
			}
		}
		return String.join(", ", parts);
	}

	private boolean nearlyEqual(double actual, double expected, double tolerance) {
		return Math.abs(actual - expected) <= Math.max(tolerance, Math.abs(expected) * 1e-6);
	}

	private String currentScenarioName() {
		if (env.getWalls().isEmpty()) {
			return "Plano vacio";
		}
		if (matchesActiveTemplate()) {
			return activeTemplateName;
		}
		return "Escenario personalizado";
	}

	private String currentScenarioDescription() {
		if (env.getWalls().isEmpty()) {
			return "No hay paredes cargadas en el entorno actual.";
		}
		if (matchesActiveTemplate()) {
			return Environment.templateDescription(activeTemplateName);
		}
		return "El plano actual se ha modificado respecto a la plantilla original y se trata como escenario personalizado.";
	}

	private boolean matchesActiveTemplate() {
		if (activeTemplateName == null || activeTemplateName.isBlank()) {
			return false;
		}

		List<Wall> currentWalls = env.getWalls();
		List<Wall> templateWalls = Environment.wallsForTemplate(activeTemplateName);
		if (currentWalls.size() != templateWalls.size()) {
			return false;
		}

		for (int i = 0; i < currentWalls.size(); i++) {
			if (!sameWall(currentWalls.get(i), templateWalls.get(i))) {
				return false;
			}
		}
		return true;
	}

	private boolean sameWall(Wall left, Wall right) {
		if (left == null || right == null) {
			return left == right;
		}

		String leftMaterial = (left.getMaterial() == null) ? "" : left.getMaterial().getName();
		String rightMaterial = (right.getMaterial() == null) ? "" : right.getMaterial().getName();

		return nearlyEqual(left.getX1(), right.getX1(), 1e-9)
				&& nearlyEqual(left.getY1(), right.getY1(), 1e-9)
				&& nearlyEqual(left.getX2(), right.getX2(), 1e-9)
				&& nearlyEqual(left.getY2(), right.getY2(), 1e-9)
				&& nearlyEqual(left.getThicknessCm(), right.getThicknessCm(), 1e-9)
				&& Objects.equals(leftMaterial, rightMaterial);
	}

	private String formatDbm(double value) {
		return String.format(Locale.US, "%.2f dBm", value);
	}

	private String formatDb(double value) {
		return String.format(Locale.US, "%.2f dB", value);
	}

	private String formatMbps(double value) {
		return String.format(Locale.US, "%.2f Mbps", value);
	}

	private String formatBer(double value) {
		return String.format(Locale.US, "%.2e", value);
	}

	private String formatMeters(double value) {
		return String.format(Locale.US, "%.2f m", value);
	}

	private String formatDeg(double value) {
		return String.format(Locale.US, "%.1f deg", value);
	}

	private String formatHertz(double value) {
		double absValue = Math.abs(value);
		if (absValue >= 1e9) {
			return String.format(Locale.US, "%.2f GHz", value / 1e9);
		}
		if (absValue >= 1e6) {
			return String.format(Locale.US, "%.2f MHz", value / 1e6);
		}
		if (absValue >= 1e3) {
			return String.format(Locale.US, "%.2f kHz", value / 1e3);
		}
		return String.format(Locale.US, "%.0f Hz", value);
	}

	private String formatFrequency(double freqMHz) {
		if (freqMHz >= 1000.0) {
			return String.format(Locale.US, "%.2f GHz (%.0f MHz)", freqMHz / 1000.0, freqMHz);
		}
		return String.format(Locale.US, "%.0f MHz", freqMHz);
	}

	private record SummaryCheck(String label, String actual, String expected, boolean ok) {
	}

	// ============================
	// Animación de rayos (reflexión + transmisión en paredes)
	// ============================
	private void launchRaysForAllSensors(Pane root) {
		root.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));

		final double stepDeg = 10.0; // resolución angular

		for (Sensor s : config.getSensores()) {
			// Si tienes Sensor.emissionAnglesDeg(stepDeg):
			for (double deg : s.emissionAnglesDeg(stepDeg)) {
				double rad = Math.toRadians(deg);
				startRayFrom(root, s, s.getX(), s.getY(), Math.cos(rad), Math.sin(rad), s.getTxDbm(), 0, Color.ORANGE);
			}

			/*
			 * --- Alternativa si no tienes emissionAnglesDeg(...) --- if (s.isOmni()) { for
			 * (double deg = 0; deg < 360.0; deg += stepDeg) { double rad =
			 * Math.toRadians(deg); startRayFrom(root, s, s.getX(), s.getY(), Math.cos(rad),
			 * Math.sin(rad), s.getTxDbm(), 0, Color.ORANGE); } } else { double[] r =
			 * quadrantToDegRange(s.getDirectiveQuadrant()); // {start,end} for (double deg
			 * = r[0]; deg < r[1]; deg += stepDeg) { double rad = Math.toRadians(deg);
			 * startRayFrom(root, s, s.getX(), s.getY(), Math.cos(rad), Math.sin(rad),
			 * s.getTxDbm(), 0, Color.ORANGE); } }
			 * ----------------------------------------------------------
			 */
		}
	}

	/** Raytracing con división de energía en impactos: transmisión + reflexión. */
	private void startRayFrom(Pane root, Sensor s, double startX, double startY, double dirX0, double dirY0,
			double txEffDbm, int bounces, Color color) {

		if (bounces >= MAX_BOUNCES || txEffDbm <= -120)
			return;

		final double[] currX = { startX };
		final double[] currY = { startY };
		final double[] dirX = { dirX0 };
		final double[] dirY = { dirY0 };
		final double[] traveled = { 0.0 };

		final Line[] seg = { new Line(px(startX), py(startY), px(startX), py(startY)) };
		seg[0].setStroke(color);
		seg[0].setStrokeWidth(2.0);
		seg[0].getProperties().put("ray", true);
		root.getChildren().add(seg[0]);

		Timeline tl = new Timeline();
		KeyFrame frame = new KeyFrame(Duration.millis(RAY_STEP_MS), ev -> {
			double nextX = currX[0] + dirX[0] * RAY_STEP_METERS;
			double nextY = currY[0] + dirY[0] * RAY_STEP_METERS;

// ===== 1) Buscar primer impacto con paredes =====
			double[] bestHit = null;
			Wall bestWall = null;
			double bestT = Double.POSITIVE_INFINITY;

			for (Wall w : env.getWalls()) {
				double[] h = segIntersectionD(currX[0], currY[0], nextX, nextY, w.getX1(), w.getY1(), w.getX2(),
						w.getY2());
				if (h == null)
					continue;
				double t = h[2];
				if (t <= 1e-6 || t > 1.0)
					continue;
				if (t < bestT) {
					bestT = t;
					bestHit = h;
					bestWall = w;
				}
			}

			if (bestHit != null && bestWall != null) {
				traveled[0] += bestT * RAY_STEP_METERS;
				double hx = bestHit[0], hy = bestHit[1];

// Cierra el segmento en el punto de impacto
				seg[0].setEndX(px(hx));
				seg[0].setEndY(py(hy));

// Calcular ángulo de incidencia
				double wx = bestWall.getX2() - bestWall.getX1();
				double wy = bestWall.getY2() - bestWall.getY1();
				double wl = Math.hypot(wx, wy);
				if (wl < 1e-12) {
					tl.stop();
					return;
				}
				double nx = -wy / wl, ny = wx / wl;
				double dot = dirX[0] * nx + dirY[0] * ny;
				double incAngleDeg = Math.toDegrees(Math.acos(Math.abs(dot)));

// Interacción física con la pared
				WallInteraction wi = MaterialsDB.interact(bestWall.getMaterial(), env.getFreqMHz(),
						bestWall.getThicknessCm(), incAngleDeg);

// ===== 1.a) RAMA REFLEJADA =====
				if (wi.hasReflection()) {
					double rx = dirX[0] - 2.0 * dot * nx;
					double ry = dirY[0] - 2.0 * dot * ny;
					double rl = Math.hypot(rx, ry);
					rx /= rl;
					ry /= rl;

					double txReflectedDbm = txEffDbm - wi.getReflectLossDb();

					startRayFrom(root, s, hx + rx * EPS, hy + ry * EPS, rx, ry, txReflectedDbm, bounces + 1, color);
				}

// ===== 1.b) RAMA TRANSMITIDA =====
				double txTransmittedDbm = txEffDbm - wi.getTransmitLossDb();
				startRayFrom(root, s, hx + dirX[0] * EPS, hy + dirY[0] * EPS, dirX[0], dirY[0], txTransmittedDbm,
						bounces + 1, color);

				tl.stop();
				return;
			}

// ===== 2) Detección de HUB en el tramo (curr -> next) =====
			if (config.hasHub()) {
				Hub h = config.getHub();
				double segDx = nextX - currX[0], segDy = nextY - currY[0];
				double segLen = Math.hypot(segDx, segDy);
				double denom = segDx * segDx + segDy * segDy;
				double tHub = (denom <= 1e-12) ? 0.0
						: ((h.getX() - currX[0]) * segDx + (h.getY() - currY[0]) * segDy) / denom;
				tHub = Math.max(0.0, Math.min(1.0, tHub));
				double closestX = currX[0] + tHub * segDx;
				double closestY = currY[0] + tHub * segDy;
				double distPerp = Math.hypot(h.getX() - closestX, h.getY() - closestY);

				if (distPerp <= HUB_HIT_THRESHOLD) {
					double dMeters = traveled[0] + tHub * segLen;
					seg[0].setEndX(px(h.getX()));
					seg[0].setEndY(py(h.getY()));

					double lossDb = Propagation.fsplLossDb(dMeters, env.getFreqMHz());
					double prxDbm = txEffDbm + h.getGrDb() - lossDb;
					double noiseDbm = env.noiseFloorDbm();

					RayMetrics metrics = new RayMetrics();
					metrics.setTxDbm(txEffDbm);
					metrics.setGrDb(h.getGrDb());
					metrics.setDistanceTraveled(dMeters);
					metrics.setNumBounces(bounces);
					metrics.setFsplDb(lossDb);
					metrics.setPrxDbm(prxDbm);
					metrics.setNoiseDbm(noiseDbm);
					metrics.setSnrDb(prxDbm - noiseDbm);
					metrics.setRssi(prxDbm);
					metrics.setLinkMargin(prxDbm - buildReceiverSettings().getReceiverSensitivityDbm());
					metrics.setBer(Propagation.bpskBer(metrics.getSnrDb()));
					metrics.setChannelCapacityMbps(Propagation.shannonCapacityMbps(metrics.getSnrDb(), env.getBandwidthHz()));

					if (hubTooltip != null) {
						hubTooltip.setText(String.format(
								"Hub %s (%d,%d)\nSensor: %s\nDistancia: %.2f m\nFSPL: %.1f dB\nPotencia recibida: %.1f dBm\nSNR: %.1f dB\nBER: %.2e\nCapacidad: %.2f Mbps",
								h.getId(), h.getX(), h.getY(), s.getNombre(), dMeters, lossDb, prxDbm,
								metrics.getSnrDb(), metrics.getBer(), metrics.getChannelCapacityMbps()));
						showHubTooltipOverHub();
					}
					tl.stop();
					return;
				}
			}

// ===== 3) Avance sin impacto =====
			traveled[0] += RAY_STEP_METERS;
			currX[0] = nextX;
			currY[0] = nextY;
			seg[0].setEndX(px(currX[0]));
			seg[0].setEndY(py(currY[0]));
			if (traveled[0] >= 50.0)
				tl.stop();
		});

		tl.getKeyFrames().add(frame);
		tl.setCycleCount(Animation.INDEFINITE);
		tl.play();
	}

	/**
	 * Ondas circulares que detectan llegada al HUB y muestran métricas como los
	 * rayos.
	 */
	private void launchWavefronts(Pane root, Sensor s) {
		// Geometría de referencia
		if (!config.hasHub())
			return;
		Hub h = config.getHub();

		// Distancia sensor→hub en celdas (m) y en píxeles (para el círculo)
		double dx = h.getX() - s.getX();
		double dy = h.getY() - s.getY();
		double dMeters = Math.hypot(dx, dy);
		double targetRadiusPx = dMeters * cellSizePx; // 1 celda = 1 m

		// Círculo-onda
		Circle wave = new Circle(px(s.getX()), py(s.getY()), WAVE_INITIAL_RADIUS);
		wave.setStroke(Color.DODGERBLUE);
		wave.setStrokeWidth(1.5);
		wave.setFill(Color.TRANSPARENT);
		wave.setOpacity(WAVE_INITIAL_OPACITY);
		wave.getProperties().put("wave", true);
		root.getChildren().add(wave);

		// Animaremos a “pasos” para poder comprobar colisión con el HUB
		final double totalMs = WAVE_DURATION_SEC * 1000.0;
		final double frames = Math.max(2.0, totalMs / RAY_STEP_MS);
		final double radiusStep = (WAVE_MAX_RADIUS - WAVE_INITIAL_RADIUS) / frames;
		final double opacityStep = (WAVE_INITIAL_OPACITY - WAVE_FINAL_OPACITY) / frames;

		// Bandera para no repetir el “impacto”
		final boolean[] hitShown = { false };

		Timeline tl = new Timeline(new KeyFrame(Duration.millis(RAY_STEP_MS), ev -> {
			// Avanza radio y opacidad
			wave.setRadius(wave.getRadius() + radiusStep);
			wave.setOpacity(wave.getOpacity() - opacityStep);

			// ¿Ha “tocado” el hub? (tolerancia de media celda)
			if (!hitShown[0] && wave.getRadius() >= (targetRadiusPx - cellSizePx * 0.5)) {
				hitShown[0] = true;

				// ===== Métricas físicas (igual que en rays + paredes) =====
				double fspl = Propagation.fsplLossDb(dMeters, env.getFreqMHz());
				double wallLoss = Propagation.wallLossAlongLine(env.getWalls(), env.getFreqMHz(), s.getX(), s.getY(),
						h.getX(), h.getY());
				double prxDbm = s.getTxDbm() + h.getGrDb() // ganancia RX
						- fspl - env.getAlphaDbPerMeter() * dMeters - wallLoss;

				double prxMw = Math.pow(10.0, prxDbm / 10.0);
				double noise = env.noiseFloorDbm();
				double snr = prxDbm - noise;
				double capacityMbps = Propagation.shannonCapacityMbps(snr, env.getBandwidthHz());
				double ber = Propagation.bpskBer(snr);

				// Tooltip del HUB
				if (hubTooltip != null) {
					hubTooltip.setText(String.format(
							"Hub %s (%d,%d)\nSensor: %s\nDistancia: %.2f m\nFSPL: %.1f dB\nPerdida en paredes: %.1f dB\n"
									+ "Potencia recibida: %.1f dBm (%.3f mW)\nRuido: %.1f dBm\nSNR: %.1f dB\nBER: %.2e\nCapacidad: %.2f Mbps",
							h.getId(), h.getX(), h.getY(), s.getNombre(), dMeters, fspl, wallLoss, prxDbm, prxMw, noise,
							snr, ber, capacityMbps));
					showHubTooltipOverHub();
				}

				// Pequeño “flash” en el hub para feedback visual
				flashAtHub(root, h);
				root.getChildren().remove(wave); // quitas la onda
			}

			// Fin de vida de la onda
			if (wave.getRadius() >= WAVE_MAX_RADIUS || wave.getOpacity() <= 0.0) {
				root.getChildren().remove(wave);
			}
		}));
		tl.setCycleCount((int) Math.ceil(frames));
		tl.setOnFinished(e -> root.getChildren().remove(wave));
		tl.play();
	}

	// ==== ActiveRay ======
	private static class ActiveRay {
		double x, y; // posición actual
		double dirX, dirY; // dirección normalizada
		double powerDbm; // potencia actual en dBm
		int bounces; // nº de rebotes
		Line line; // segmento gráfico

		ActiveRay(double x, double y, double dirX, double dirY, double powerDbm, int bounces, Line line) {
			this.x = x;
			this.y = y;
			this.dirX = dirX;
			this.dirY = dirY;
			this.powerDbm = powerDbm;
			this.bounces = bounces;
			this.line = line;
		}
	}

	/** Dibuja un heatmap de Rx (o SNR si está activado) celda a celda. */
	private void drawHeatmap() {
	    PixelWriter pw = heatmapImg.getPixelWriter();
	    int imgW = (int) heatmapImg.getWidth();
	    int imgH = (int) heatmapImg.getHeight();

	    for (int py = 0; py < imgH; py++) {
	        for (int px = 0; px < imgW; px++) {
	            pw.setColor(px, py, Color.TRANSPARENT);
	        }
	    }

	    if (config.getSensores().isEmpty()) {
	        lastHeatmapResult = null;
	        return;
	    }

	    SimulationSettings heatmapSettings = simulationSettings.copy();
	    if (config.hasHub()) {
	        heatmapSettings.setReceiverPolarizationDeg(config.getHub().getPolarizationDeg());
	    }
	    heatmapSettings.setReceiverGainDb(0.0);
	    lastHeatmapResult = IndoorWaveEngine.computeHeatmap(env, config.getSensores(), gridW, gridH, heatmapSettings);

	    MapMetric metric = heatmapSettings.getMapMetric();
	    for (int y = 0; y < gridH; y++) {
	        for (int x = 0; x < gridW; x++) {
	            CellResult cell = lastHeatmapResult.getCell(x, y);
	            if (cell == null || !cell.hasEnergy()) continue;

	            Color color = colorForMetric(metric, cell.valueFor(metric));
	            int pxStart = MARGIN + x * cellSizePx;
	            int pxEnd = Math.min(imgW, pxStart + cellSizePx);
	            int pyStart = imgH - MARGIN - (y + 1) * cellSizePx;
	            int pyEnd = Math.min(imgH, pyStart + cellSizePx);

	            for (int py = Math.max(0, pyStart); py < pyEnd; py++) {
	                for (int px = Math.max(0, pxStart); px < pxEnd; px++) {
	                    pw.setColor(px, py, color);
	                }
	            }
	        }
	    }
	}

	private Color colorForMetric(MapMetric metric, double value) {
	    double normalized;
	    switch (metric) {
	        case POWER_DBM -> normalized = normalize(value, -110.0, -35.0);
	        case SNR_DB -> normalized = normalize(value, -10.0, 35.0);
	        case BER -> {
	            double logBer = Math.log10(Math.max(1e-8, Math.min(0.5, value)));
	            normalized = 1.0 - normalize(logBer, -8.0, -0.3);
	        }
	        case CAPACITY_MBPS -> normalized = normalize(value, 0.0, 450.0);
	        default -> normalized = 0.0;
	    }
	    return lerpTurbo(normalized);
	}
//aa
	private double normalize(double value, double min, double max) {
	    if (Math.abs(max - min) < 1e-9) return 0.0;
	    return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
	}

	private SimulationSettings buildReceiverSettings() {
	    SimulationSettings settings = simulationSettings.copy();
	    if (config.hasHub()) {
	        settings.setReceiverGainDb(config.getHub().getGrDb());
	        settings.setReceiverPolarizationDeg(config.getHub().getPolarizationDeg());
	    } else {
	        settings.setReceiverGainDb(0.0);
	        settings.setReceiverPolarizationDeg(0.0);
	    }
	    return settings;
	}

	// ============================
	// Utilidades geométricas/UI
	// ============================
	private double px(double gridX) {
		return MARGIN + gridX * cellSizePx;
	}

	private double py(double gridY) {
		return gridH * cellSizePx + MARGIN - gridY * cellSizePx;
	}

	private static Label label(String text, double x, double y, Font font, Color color) {
		Label l = new Label(text);
		l.setFont(font);
		l.setTextFill(color);
		l.setAlignment(Pos.CENTER_LEFT);
		l.setLayoutX(x);
		l.setLayoutY(y);
		l.setMouseTransparent(true);
		return l;
	}

	private double toGridXd(double pixelX) {
		return (pixelX - MARGIN) / (double) cellSizePx;
	}

	private double toGridYd(double pixelY) {
		return (gridH * cellSizePx + MARGIN - pixelY) / (double) cellSizePx;
	}

	private static double[] segIntersectionD(double x1, double y1, double x2, double y2, double x3, double y3,
			double x4, double y4) {
		double den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
		if (Math.abs(den) < 1e-12)
			return null;
		double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den;
		double u = ((x1 - x3) * (y1 - y2) - (y1 - y3) * (x1 - x2)) / den;
		if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0)
			return null;
		double px = x1 + t * (x2 - x1);
		double py = y1 + t * (y2 - y1);
		return new double[] { px, py, t };
	}

	private static double distPointToSegment(double px, double py, double x1, double y1, double x2, double y2) {
		double vx = x2 - x1, vy = y2 - y1;
		double wx = px - x1, wy = py - y1;
		double c1 = vx * wx + vy * wy;
		if (c1 <= 0)
			return Math.hypot(px - x1, py - y1);
		double c2 = vx * vx + vy * vy;
		if (c2 <= c1)
			return Math.hypot(px - x2, py - y2);
		double t = c1 / c2;
		double projx = x1 + t * vx, projy = y1 + t * vy;
		return Math.hypot(px - projx, py - projy);
	}

	private void showHubTooltipOverHub() {
		if (hubBox == null || hubTooltip == null)
			return;
		Bounds b = hubBox.localToScreen(hubBox.getBoundsInLocal());
		if (b == null)
			return;
		hubTooltip.show(hubBox, b.getMinX(), b.getMinY() - 24);
		PauseTransition hide = new PauseTransition(Duration.seconds(3));
		hide.setOnFinished(e -> hubTooltip.hide());
		hide.play();
	}

	/** Devuelve el rango angular [startDeg, endDeg) del cuadrante en grados. */
	
	/** Pequeño flash en el HUB cuando una onda/rayo “impacta”. */
	private void flashAtHub(Pane root, Hub h) {
		Circle flash = new Circle(px(h.getX()), py(h.getY()), 6);
		flash.setFill(Color.CRIMSON);
		flash.setOpacity(0.9);
		root.getChildren().add(flash);

		Timeline anim = new Timeline(
				new KeyFrame(Duration.millis(0), new javafx.animation.KeyValue(flash.radiusProperty(), 6),
						new javafx.animation.KeyValue(flash.opacityProperty(), 0.9)),
				new KeyFrame(Duration.millis(350), new javafx.animation.KeyValue(flash.radiusProperty(), 22),
						new javafx.animation.KeyValue(flash.opacityProperty(), 0.0)));
		anim.setOnFinished(e -> root.getChildren().remove(flash));
		anim.play();
	}

	// Paleta "turbo" aproximada (suave y moderna)
	private Color lerpTurbo(double t) {
		// clamp
		t = Math.max(0.0, Math.min(1.0, t));
		// aproximación simple: puedes sustituir por una LUT si quieres más fidelidad
		// aquí uso una mezcla de stops para un efecto turbo-like
		return Color.hsb(260 * (1 - t), 0.95, 0.95); // simple: morado→azul→...→rojo
	}

	private void drawWallsInLayer() {
		wallsLayer.getChildren().clear();
		for (Wall w : env.getWalls()) {
			Line l = new Line(px(w.getX1()), py(w.getY1()), px(w.getX2()), py(w.getY2()));
			l.setStroke(colorForMaterial(w.getMaterial()));
			l.setStrokeWidth(strokeForMaterial(w.getMaterial(), w.getThicknessCm()));
			l.setOpacity(0.95);

			double lossDb = (w.getMaterial() == null) ? 0.0
					: w.getMaterial().lossDb(env.getFreqMHz(), w.getThicknessCm());
			Tooltip.install(l,
					new Tooltip(String.format("%s (%.1f cm) — Loss@%.0fMHz: %.1f dB",
							w.getMaterial() != null ? w.getMaterial().getName() : "WALL", w.getThicknessCm(),
							env.getFreqMHz(), lossDb)));
			Tooltip.install(l,
					new Tooltip(String.format("%s (%.1f cm)\nPerdida a %.0f MHz: %.1f dB",
							w.getMaterial() != null ? w.getMaterial().getName() : "Pared", w.getThicknessCm(),
							env.getFreqMHz(), lossDb)));

			wallsLayer.getChildren().add(l);
		}
	}

}
