package UI;

import core.Configuracion;
import core.Hub;
import core.Obstacle; // LEGACY: usado aún por el raytracing viejo
import core.RayMetrics;
import core.Sensor;
import core.WaveContribution;
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

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.scene.effect.GaussianBlur;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smart Home Visual Grid + Heatmap RSSI/SNR + Raytracing legacy.
 */
public class VisualGridApp extends Application {

	// ============================
	// Constantes de dibujo / Grid
	// ============================
	public static final int WIDTH = 1000;
	public static final int HEIGHT = 700;
	public static final int MARGIN = 35;
	public static final int GRID_MAX_X = 35;
	public static final int GRID_MAX_Y = 35;
	public static final int SCALE = 18;


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
	private final Configuracion config = new Configuracion(); // tu contenedor legacy
	private final Map<Sensor, NodeBundle> nodos = new HashMap<>();
	private final List<WaveContribution> waveField = new ArrayList<>();
	private final List<RayMetrics> rayMetricsList = new ArrayList<>();

	// Nuevo motor simple
	private Environment env; // parámetros físicos + paredes (materiales)
	private List<Wall> walls = new ArrayList<>();
	private Group wallsLayer;

	// UI base
	private Group rootGroup; // superpone heatmap + canvas
	private Canvas gridCanvas; // rejilla + paredes + objetos
	private WritableImage heatmapImg; // 1 px por celda
	private ImageView heatmapView; // escalado a pixeles

	// Flags heatmap
	private boolean showHeatmap = false;
	private boolean showSNR = false;
	private CheckBox chkShowSnr;
	private final SimulationSettings simulationSettings = new SimulationSettings();
	private HeatmapResult lastHeatmapResult;
	private ComboBox<MapMetric> cbMetric;
	private ComboBox<PropagationMode> cbMode;
	private ComboBox<FadingModel> cbFading;
	private CheckBox chkDiffraction;
	private CheckBox chkScattering;
	private String activeTemplateName = Environment.defaultTemplateName();

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
	private static final double WAVE_INTERVAL_MS = 250;

	// Aux record
	private record NodeBundle(Circle dot, Label label, Tooltip tip) {
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

		// 1) Sensor y Hub
//		Sensor S1 = new Sensor("S1", "Temp salón", 5, 5, 22.5);
//		S1.setTxDbm(20.0);
//		S1.setOrientationDeg(70);
//		config.addSensor(S1);

//		Sensor S2 = new Sensor("S2", "Router", 25, 10, 22.5);
//		S2.setTxDbm(20.0);
//		config.addSensor(S2);

//		Hub H1 = new Hub("H1", "Hub central", 15, 6);
//		H1.setGrDb(0.0);
//		config.setHub(H1);

		// 2) Entorno
		env.setFreqMHz(Environment.WIFI_24_GHZ_MHZ); // 2400 MHz
		env.setBandwidthHz(20e6);
		env.setNoiseFigureDb(7.0);
		env.setAlphaDbPerMeter(0.0);

		// Obstáculos legacy (para rayos viejos). En paralelo tenemos walls en env.
		// Puedes quitar esto si ya migraste todo a env.setWalls(...).
		config.setObstaculos(List.of()); // vacío para no duplicar paredes visuales

		// 1) Asegura que el panel lateral tenga ancho fijo y fondo opacos
		if (root instanceof BorderPane bp && bp.getRight() instanceof Region side) {
			side.setPrefWidth(360); // elige el ancho que prefieras para el panel
			side.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
			side.setBorder(new Border(new BorderStroke(Color.web("#dddddd"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
					BorderWidths.DEFAULT)));
		}

		// 2) Scene SIN width/height fijos (se calcula a partir de los preferred sizes)
		Scene scene = new Scene(root);
		stage.setTitle("Smart Home — Visual Grid con Heatmap");
		stage.setScene(scene);
		stage.setTitle("Smart Home Simulator");

		// 3) Ajusta la ventana al tamaño preferido de root (canvas + panel)
		stage.sizeToScene();
		stage.show();

		// Dibujo inicial + utilidades
		repaintAll();
		enableWallEditing(); // menú contextual para cambiar material
		enableCleanProbe(); // tooltip con metricas al mover raton
	}

	// ============================Sss
	// Inicialización y UI
	// ============================
	private void initModel() {
		env = new Environment();
		activeTemplateName = Environment.defaultTemplateName();
		env.setWalls(Environment.wallsForTemplate(Environment.defaultTemplateName()));
		walls = new ArrayList<>(env.getWalls());
	}

	// Legacy panel kept as a fallback while the new UI settles.
	private Parent buildLegacyMainUI() {
	    gridCanvas = new Canvas(gridW * cellSizePx + 2 * MARGIN,
	            gridH * cellSizePx + 2 * MARGIN);

	    int imgW = gridW * cellSizePx + 2 * MARGIN;
	    int imgH = gridH * cellSizePx + 2 * MARGIN;
	    heatmapImg = new WritableImage(imgW, imgH);
	    heatmapView = new ImageView(heatmapImg);
	    heatmapView.setOpacity(0.74);
	    heatmapView.setVisible(false);

	    wallsLayer = new Group();
	    Pane canvasHolder = new Pane(gridCanvas);
	    rootGroup = new Group(heatmapView, wallsLayer, canvasHolder);
	    Pane centerPane = new Pane(rootGroup);

	    VBox sideContent = new VBox(10);
	    sideContent.setPadding(new Insets(10));
	    sideContent.setPrefWidth(290);
	    sideContent.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));

	    Label lblDevices = new Label("GestiÃ³n de dispositivos");
	    Label lblCoords = new Label("Coordenadas (X,Y)");
	    TextField tfX = new TextField("5");
	    tfX.setPrefWidth(60);
	    TextField tfY = new TextField("5");
	    tfY.setPrefWidth(60);
	    HBox coordRow = new HBox(6, tfX, tfY);

	    Button btnAddSensor = new Button("AÃ±adir Sensor");
	    Button btnAddHub = new Button("AÃ±adir Hub");
	    Button btnRemove = new Button("Eliminar dispositivo");

	    Label lblSelSensor = new Label("Seleccionar sensor");
	    ComboBox<Sensor> cbSelectSensor = new ComboBox<>();
	    cbSelectSensor.setPrefWidth(220);

	    Label lblAnt = new Label("ConfiguraciÃ³n de antena");
	    ComboBox<String> cbAntType = new ComboBox<>();
	    cbAntType.getItems().addAll("Omni", "Direccional");
	    cbAntType.getSelectionModel().select("Omni");

	    Label lblOrient = new Label("OrientaciÃ³n (Â°)");
	    TextField tfOrient = new TextField("0");
	    Label lblBeam = new Label("Beamwidth (Â°)");
	    TextField tfBeam = new TextField("90");
	    Label lblGain = new Label("Ganancia Tx (dB)");
	    TextField tfGain = new TextField("0");
	    Label lblPattern = new Label("PatrÃ³n G(theta)");
	    TextField tfPattern = new TextField("1.8");
	    Label lblPol = new Label("PolarizaciÃ³n (Â°)");
	    TextField tfPol = new TextField("0");
	    Button btnApplyConfig = new Button("Aplicar configuraciÃ³n");

	    cbSelectSensor.valueProperty().addListener((obs, old, selected) -> {
	        if (selected == null) return;
	        cbAntType.getSelectionModel().select(selected.isDirectional() ? "Direccional" : "Omni");
	        tfOrient.setText(String.format(Locale.US, "%.1f", selected.getOrientationDeg()));
	        tfBeam.setText(String.format(Locale.US, "%.1f", selected.getBeamwidthDeg()));
	        tfGain.setText(String.format(Locale.US, "%.1f", selected.getTxGainDb()));
	        tfPattern.setText(String.format(Locale.US, "%.2f", selected.getPatternSharpness()));
	        tfPol.setText(String.format(Locale.US, "%.1f", selected.getPolarizationDeg()));
	    });

	    btnAddSensor.setOnAction(e -> {
	        try {
	            int x = Integer.parseInt(tfX.getText().trim());
	            int y = Integer.parseInt(tfY.getText().trim());
	            Sensor sensor = new Sensor("S" + (config.getSensores().size() + 1),
	                    "Sensor " + (config.getSensores().size() + 1), x, y, 22.5);
	            config.addSensor(sensor);
	            cbSelectSensor.getItems().add(sensor);
	            cbSelectSensor.getSelectionModel().select(sensor);
	            repaintAll();
	        } catch (NumberFormatException ex) {
	            System.err.println("Coordenadas invÃ¡lidas");
	        }
	    });

	    btnAddHub.setOnAction(e -> {
	        try {
	            int x = Integer.parseInt(tfX.getText().trim());
	            int y = Integer.parseInt(tfY.getText().trim());
	            config.setHub(new Hub("H1", "Hub central", x, y));
	            repaintAll();
	        } catch (NumberFormatException ex) {
	            System.err.println("Coordenadas invÃ¡lidas");
	        }
	    });

	    btnRemove.setOnAction(e -> {
	        Sensor selected = cbSelectSensor.getSelectionModel().getSelectedItem();
	        if (selected != null) {
	            config.removeSensor(selected);
	            cbSelectSensor.getItems().remove(selected);
	        } else if (config.hasHub()) {
	            config.setHub(null);
	        }
	        repaintAll();
	    });

	    btnApplyConfig.setOnAction(e -> {
	        Sensor selected = cbSelectSensor.getSelectionModel().getSelectedItem();
	        if (selected == null) return;

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

	    VBox deviceBox = new VBox(8,
	            lblDevices, lblCoords, coordRow,
	            new HBox(6, btnAddSensor, btnAddHub),
	            btnRemove,
	            lblSelSensor, cbSelectSensor,
	            lblAnt, cbAntType,
	            lblOrient, tfOrient,
	            lblBeam, tfBeam,
	            lblGain, tfGain,
	            lblPattern, tfPattern,
	            lblPol, tfPol,
	            btnApplyConfig
	    );

	    Label lblFreq = new Label("Frecuencia");
	    ComboBox<String> cbFreq = new ComboBox<>();
	    cbFreq.getItems().addAll("2.4 GHz", "5 GHz");
	    cbFreq.getSelectionModel().select("2.4 GHz");
	    cbFreq.setOnAction(e -> {
	        String sel = cbFreq.getSelectionModel().getSelectedItem();
	        env.setFreqMHz("2.4 GHz".equals(sel) ? Environment.WIFI_24_GHZ_MHZ : Environment.WIFI_5_GHZ_MHZ);
	        repaintAll();
	    });

	    Button btnHeatmap = new Button("Mostrar Heatmap");
	    btnHeatmap.setOnAction(e -> {
	        showHeatmap = !heatmapView.isVisible();
	        heatmapView.setVisible(showHeatmap);
	        if (showHeatmap) {
	            drawHeatmap();
	        }
	    });

	    Button btnClearHeatmap = new Button("Ocultar Heatmap");
	    btnClearHeatmap.setOnAction(e -> {
	        showHeatmap = false;
	        heatmapView.setVisible(false);
	        lastHeatmapResult = null;
	    });

	    Label lblMetric = new Label("Mapa / modelo");
	    cbMetric = new ComboBox<>();
	    cbMetric.getItems().addAll(MapMetric.values());
	    cbMetric.getSelectionModel().select(simulationSettings.getMapMetric());
	    cbMetric.setOnAction(e -> {
	        simulationSettings.setMapMetric(cbMetric.getValue());
	        if (heatmapView.isVisible()) drawHeatmap();
	    });

	    cbMode = new ComboBox<>();
	    cbMode.getItems().addAll(PropagationMode.values());
	    cbMode.getSelectionModel().select(simulationSettings.getPropagationMode());
	    cbMode.setOnAction(e -> {
	        simulationSettings.setPropagationMode(cbMode.getValue());
	        if (heatmapView.isVisible()) drawHeatmap();
	    });

	    cbFading = new ComboBox<>();
	    cbFading.getItems().addAll(FadingModel.values());
	    cbFading.getSelectionModel().select(simulationSettings.getFadingModel());
	    cbFading.setOnAction(e -> {
	        simulationSettings.setFadingModel(cbFading.getValue());
	        if (heatmapView.isVisible()) drawHeatmap();
	    });

	    chkDiffraction = new CheckBox("DifracciÃ³n");
	    chkDiffraction.setSelected(simulationSettings.isDiffractionEnabled());
	    chkDiffraction.setOnAction(e -> {
	        simulationSettings.setDiffractionEnabled(chkDiffraction.isSelected());
	        if (heatmapView.isVisible()) drawHeatmap();
	    });

	    chkScattering = new CheckBox("DispersiÃ³n");
	    chkScattering.setSelected(simulationSettings.isScatteringEnabled());
	    chkScattering.setOnAction(e -> {
	        simulationSettings.setScatteringEnabled(chkScattering.isSelected());
	        if (heatmapView.isVisible()) drawHeatmap();
	    });

	    CheckBox chkParallel = new CheckBox("Paralelizar cÃ¡lculo");
	    chkParallel.setSelected(simulationSettings.isParallelComputation());
	    chkParallel.setOnAction(e -> {
	        simulationSettings.setParallelComputation(chkParallel.isSelected());
	        if (heatmapView.isVisible()) drawHeatmap();
	    });

	    Label lblExponent = new Label("Exponente indoor n");
	    TextField tfExponent = new TextField(String.format(Locale.US, "%.2f", simulationSettings.getLogDistanceExponent()));
	    Label lblKFactor = new Label("K-factor Rician (dB)");
	    TextField tfKFactor = new TextField(String.format(Locale.US, "%.1f", simulationSettings.getRicianKFactorDb()));
	    Label lblCull = new Label("Umbral de culling (dBm)");
	    TextField tfCull = new TextField(String.format(Locale.US, "%.0f", simulationSettings.getCullingThresholdDbm()));
	    Button btnApplyModel = new Button("Aplicar modelo");
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
	        if (heatmapView.isVisible()) drawHeatmap();
	    });

	    Label lblBW = new Label("BW (Hz)");
	    TextField tfBW = new TextField(String.format(Locale.US, "%.3g", env.getBandwidthHz()));
	    Button btnBW = new Button("Aplicar BW");
	    btnBW.setOnAction(e -> {
	        try {
	            env.setBandwidthHz(Double.parseDouble(tfBW.getText().trim()));
	            if (heatmapView.isVisible()) drawHeatmap();
	        } catch (NumberFormatException ignored) {
	        }
	    });

	    Label lblNF = new Label("NF (dB)");
	    TextField tfNF = new TextField(String.format(Locale.US, "%.1f", env.getNoiseFigureDb()));
	    Button btnNF = new Button("Aplicar NF");
	    btnNF.setOnAction(e -> {
	        try {
	            env.setNoiseFigureDb(Double.parseDouble(tfNF.getText().trim()));
	            if (heatmapView.isVisible()) drawHeatmap();
	        } catch (NumberFormatException ignored) {
	        }
	    });

	    Label lblTpl = new Label("Plantillas de paredes");
	    HBox tplRow = new HBox(8);
	    Button tplRect = new Button("Tpl RectÃ¡ngulo");
	    Button tplRoom = new Button("Tpl HabitaciÃ³n");
	    Button tplHouse = new Button("Tpl House L");

	    tplRect.setOnAction(e -> {
	        env.setWalls(Environment.WALLS_TEMPLATE_House1);
	        repaintAll();
	    });
	    tplRoom.setOnAction(e -> {
	        env.setWalls(Environment.WALLS_TEMPLATE_2_MAT);
	        repaintAll();
	    });
	    tplHouse.setOnAction(e -> {
	        env.setWalls(Environment.WALLS_TEMPLATE_DRAWN);
	        repaintAll();
	    });
	    tplRow.getChildren().addAll(tplRect, tplRoom, tplHouse);

	    Label lblActions = new Label("Acciones");
	    HBox actions1 = new HBox(8);
	    Button btnRays = new Button("Rayos");
	    btnRays.setOnAction(e -> {
	        simulationSettings.setPropagationMode(PropagationMode.RAYS);
	        cbMode.getSelectionModel().select(PropagationMode.RAYS);
	        rayMetricsList.clear();
	        centerPane.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));
	        launchRaysForAllSensors(centerPane);
	        if (heatmapView.isVisible()) drawHeatmap();
	    });
	    Button btnWaves = new Button("Ondas");
	    btnWaves.setOnAction(e -> {
	        simulationSettings.setPropagationMode(PropagationMode.WAVES);
	        cbMode.getSelectionModel().select(PropagationMode.WAVES);
	        centerPane.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("wave")));
	        for (Sensor sensor : config.getSensores()) launchWavefronts(centerPane, sensor);
	        if (heatmapView.isVisible()) drawHeatmap();
	    });
	    Button btnParams = new Button("ParÃ¡metros");
	    btnParams.setOnAction(e -> showParametersWindow());
	    actions1.getChildren().addAll(btnRays, btnWaves, btnParams);

	    sideContent.getChildren().addAll(
	            deviceBox,
	            new Separator(),
	            lblFreq, cbFreq,
	            btnHeatmap, btnClearHeatmap,
	            lblMetric,
	            new Label("MÃ©trica"), cbMetric,
	            new Label("Modo"), cbMode,
	            new Label("Fading"), cbFading,
	            chkDiffraction, chkScattering, chkParallel,
	            lblExponent, tfExponent,
	            lblKFactor, tfKFactor,
	            lblCull, tfCull,
	            btnApplyModel,
	            new Separator(),
	            lblBW, new HBox(6, tfBW, btnBW),
	            lblNF, new HBox(6, tfNF, btnNF),
	            new Separator(),
	            lblTpl, tplRow,
	            new Separator(),
	            lblActions, actions1
	    );

	    ScrollPane side = new ScrollPane(sideContent);
	    side.setFitToWidth(true);
	    side.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
	    side.setPrefWidth(305);

	    BorderPane bp = new BorderPane();
	    bp.setCenter(centerPane);
	    bp.setRight(side);

	    drawAxesAndGrid(centerPane);
	    drawWallsInLayer();

	    return bp;
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
		Pane canvasHolder = new Pane(gridCanvas);
		rootGroup = new Group(heatmapView, wallsLayer, canvasHolder);
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

		TextField tfX = new TextField("5");
		TextField tfY = new TextField("5");
		tfX.setPromptText("X");
		tfY.setPromptText("Y");
		tfX.setPrefWidth(70);
		tfY.setPrefWidth(70);
		styleInputField(tfX);
		styleInputField(tfY);

		Label lblX = new Label("X");
		Label lblY = new Label("Y");
		lblX.setStyle("-fx-text-fill: #5f6b76; -fx-font-weight: bold;");
		lblY.setStyle("-fx-text-fill: #5f6b76; -fx-font-weight: bold;");

		HBox positionRow = new HBox(8, lblX, tfX, lblY, tfY);
		positionRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(tfX, Priority.ALWAYS);
		HBox.setHgrow(tfY, Priority.ALWAYS);

		Button btnAddSensor = new Button("Anadir sensor");
		Button btnAddHub = new Button("Anadir hub");
		Button btnRemove = new Button("Eliminar seleccionado");
		stylePrimaryButton(btnAddSensor);
		styleSecondaryButton(btnAddHub);
		styleSecondaryButton(btnRemove);

		ComboBox<Sensor> cbSelectSensor = new ComboBox<>();
		cbSelectSensor.setPromptText("Selecciona un sensor");
		cbSelectSensor.getItems().setAll(config.getSensores());
		styleInputField(cbSelectSensor);

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

		cbSelectSensor.valueProperty().addListener((obs, old, selected) -> {
			if (selected == null) {
				return;
			}
			cbAntType.getSelectionModel().select(selected.isDirectional() ? "Direccional" : "Omni");
			tfOrient.setText(String.format(Locale.US, "%.1f", selected.getOrientationDeg()));
			tfBeam.setText(String.format(Locale.US, "%.1f", selected.getBeamwidthDeg()));
			tfGain.setText(String.format(Locale.US, "%.1f", selected.getTxGainDb()));
			tfPattern.setText(String.format(Locale.US, "%.2f", selected.getPatternSharpness()));
			tfPol.setText(String.format(Locale.US, "%.1f", selected.getPolarizationDeg()));
		});

		btnAddSensor.setOnAction(e -> {
			try {
				int x = Integer.parseInt(tfX.getText().trim());
				int y = Integer.parseInt(tfY.getText().trim());
				Sensor sensor = new Sensor("S" + (config.getSensores().size() + 1),
						"Sensor " + (config.getSensores().size() + 1), x, y, 22.5);
				config.addSensor(sensor);
				cbSelectSensor.getItems().add(sensor);
				cbSelectSensor.getSelectionModel().select(sensor);
				repaintAll();
			} catch (NumberFormatException ex) {
				System.err.println("Coordenadas invalidas");
			}
		});

		btnAddHub.setOnAction(e -> {
			try {
				int x = Integer.parseInt(tfX.getText().trim());
				int y = Integer.parseInt(tfY.getText().trim());
				config.setHub(new Hub("H1", "Hub central", x, y));
				repaintAll();
			} catch (NumberFormatException ex) {
				System.err.println("Coordenadas invalidas");
			}
		});

		btnRemove.setOnAction(e -> {
			Sensor selected = cbSelectSensor.getSelectionModel().getSelectedItem();
			if (selected != null) {
				config.removeSensor(selected);
				cbSelectSensor.getItems().remove(selected);
			} else if (config.hasHub()) {
				config.setHub(null);
			}
			repaintAll();
		});

		btnApplyConfig.setOnAction(e -> {
			Sensor selected = cbSelectSensor.getSelectionModel().getSelectedItem();
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
		addFormRow(deviceGrid, 1, "Sensor activo", cbSelectSensor);
		addFormRow(deviceGrid, 2, "Antena", cbAntType);
		addFormRow(deviceGrid, 3, "Orientacion (deg)", tfOrient);
		addFormRow(deviceGrid, 4, "Apertura (deg)", tfBeam);
		addFormRow(deviceGrid, 5, "Ganancia Tx (dB)", tfGain);
		addFormRow(deviceGrid, 6, "Patron G(theta)", tfPattern);
		addFormRow(deviceGrid, 7, "Polarizacion (deg)", tfPol);

		GridPane deviceButtons = createButtonGrid();
		deviceButtons.add(btnAddSensor, 0, 0);
		deviceButtons.add(btnAddHub, 1, 0);
		deviceButtons.add(btnRemove, 0, 1, 2, 1);

		VBox deviceContent = new VBox(12, deviceGrid, deviceButtons, btnApplyConfig);
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
		Button btnClearTemplate = new Button("Vaciar plano");
		stylePrimaryButton(btnLoadTemplate);
		styleSecondaryButton(btnClearTemplate);

		btnLoadTemplate.setOnAction(e -> applyTemplate(cbTemplate.getValue()));
		btnClearTemplate.setOnAction(e -> {
			env.clearWalls();
			walls = new ArrayList<>(env.getWalls());
			repaintAll();
		});

		GridPane templateGrid = createFormGrid();
		addFormRow(templateGrid, 0, "Escenario", cbTemplate);
		addFormRow(templateGrid, 1, "Resumen", templateInfo);

		GridPane templateButtons = createButtonGrid();
		templateButtons.add(btnLoadTemplate, 0, 0);
		templateButtons.add(btnClearTemplate, 1, 0);
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
			rayMetricsList.clear();
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

		sideContent.getChildren().addAll(panelTitle, panelSubtitle, devicesSection, mapSection, radioSection,
				templateSection, actionsSection);

		ScrollPane side = new ScrollPane(sideContent);
		side.setFitToWidth(true);
		side.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		side.setPrefWidth(360);
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

	private void applyTemplate(String templateName) {
		if (templateName == null) {
			return;
		}
		activeTemplateName = templateName;
		env.setWalls(Environment.wallsForTemplate(templateName));
		walls = new ArrayList<>(env.getWalls());
		repaintAll();
	}

	private String buildTemplateSummary(String templateName) {
		if (templateName == null) {
			return "Selecciona una plantilla para cargar un escenario interior.";
		}
		return Environment.templateDescription(templateName) + "\nParedes: "
				+ Environment.wallsForTemplate(templateName).size() + " segmentos";
	}

	private Parent buildUI() {
	    // ===== Centro: heatmap (debajo) + wallsLayer (medio) + canvas (encima) =====
	    gridCanvas = new Canvas(gridW * cellSizePx + 2 * MARGIN,
	                            gridH * cellSizePx + 2 * MARGIN);

	    // Heatmap con tamaño exacto del canvas (incluye márgenes)
	    int imgW = gridW * cellSizePx + 2 * MARGIN;
	    int imgH = gridH * cellSizePx + 2 * MARGIN;
	    heatmapImg = new WritableImage(imgW, imgH);
	    heatmapView = new ImageView(heatmapImg);

	    // ✅ No aplicar setFitWidth/Height ni translateX/Y → ya tiene el tamaño correcto
	    heatmapView.setOpacity(0.72);
	    heatmapView.setVisible(false);

	    wallsLayer = new Group();
	    Pane canvasHolder = new Pane(gridCanvas);

	    // Orden: primero heatmap, luego paredes, luego canvas
	    rootGroup = new Group(heatmapView, wallsLayer, canvasHolder);

	    // Lo envolvemos en un Pane
	    Pane centerPane = new Pane(rootGroup);

	    // ===== Lateral derecho =====
	    VBox side = new VBox(10);
	    side.setPadding(new Insets(10));
	    side.setPrefWidth(250);
	    side.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
	    side.setBorder(new Border(new BorderStroke(Color.web("#dddddd"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
	            BorderWidths.DEFAULT)));

	    // ===== Sección: Dispositivos =====
	    Label lblDevices = new Label("Gestión de dispositivos");

	    // Coordenadas
	    Label lblCoords = new Label("Coordenadas (X,Y)");
	    TextField tfX = new TextField("5");
	    tfX.setPrefWidth(60);
	    TextField tfY = new TextField("5");
	    tfY.setPrefWidth(60);
	    HBox coordRow = new HBox(6, tfX, tfY);

	    // Botones de gestión
	    Button btnAddSensor = new Button("Añadir Sensor");
	    Button btnAddHub = new Button("Añadir Hub");
	    Button btnRemove = new Button("Eliminar dispositivo");

	    // Selector de sensor
	    Label lblSelSensor = new Label("Seleccionar sensor");
	    ComboBox<Sensor> cbSelectSensor = new ComboBox<>();
	    cbSelectSensor.setPrefWidth(180);

	    // Configuración de antena
	    Label lblAnt = new Label("Configuración de antena");
	    ComboBox<String> cbAntType = new ComboBox<>();
	    cbAntType.getItems().addAll("Omni", "Direccional");
	    cbAntType.getSelectionModel().select("Omni");

	    Label lblOrient = new Label("Orientación (°)");
	    TextField tfOrient = new TextField("0");

	    Label lblBeam = new Label("Beamwidth (°)");
	    TextField tfBeam = new TextField("90");

	    Button btnApplyConfig = new Button("Aplicar configuración");

	    // --- Acciones de los botones ---
	    btnAddSensor.setOnAction(e -> {
	        try {
	            int x = Integer.parseInt(tfX.getText());
	            int y = Integer.parseInt(tfY.getText());

	            Sensor s = new Sensor("S" + (config.getSensores().size() + 1),
	                                  "Sensor demo", x, y, 22.5);
	            config.addSensor(s);
	            pintarSensor(centerPane, s, cbSelectSensor);

	            cbSelectSensor.getItems().add(s);
	            cbSelectSensor.getSelectionModel().select(s);
	        } catch (NumberFormatException ex) {
	            System.err.println("Coordenadas inválidas");
	        }
	    });

	    btnAddHub.setOnAction(e -> {
	        try {
	            int x = Integer.parseInt(tfX.getText());
	            int y = Integer.parseInt(tfY.getText());

	            Hub h = new Hub("H" + (config.hasHub() ? 2 : 1),
	                            "Hub demo", x, y);
	            config.setHub(h);
	            pintarHub(centerPane, h);
	        } catch (NumberFormatException ex) {
	            System.err.println("Coordenadas inválidas");
	        }
	    });

	    btnRemove.setOnAction(e -> {
	        Sensor s = cbSelectSensor.getSelectionModel().getSelectedItem();
	        if (s != null) {
	            config.getSensores().remove(s);
	            cbSelectSensor.getItems().remove(s);
	            centerPane.getChildren().removeIf(n -> n.getProperties().get("sensor") == s);
	        } else if (config.hasHub()) {
	            config.setHub(null);
	            centerPane.getChildren().removeIf(n -> n.getProperties().get("hub") != null);
	        }
	    });

	    btnApplyConfig.setOnAction(e -> {
	        Sensor s = cbSelectSensor.getSelectionModel().getSelectedItem();
	        if (s != null) {
	            String t = cbAntType.getSelectionModel().getSelectedItem();
	            if ("Omni".equals(t)) {
	                s.setAntennaType(Sensor.AntennaType.OMNI);
	            } else {
	                s.setAntennaType(Sensor.AntennaType.DIRECTIONAL);
	            }
	            try {
	                s.setOrientationDeg(Double.parseDouble(tfOrient.getText()));
	            } catch (NumberFormatException ex) { s.setOrientationDeg(0); }
	            try {
	                s.setBeamwidthDeg(Double.parseDouble(tfBeam.getText()));
	            } catch (NumberFormatException ex) { s.setBeamwidthDeg(90); }

	            if (heatmapView.isVisible()) drawHeatmap();
	        }
	    });

	    VBox deviceBox = new VBox(8,
	        lblDevices, lblCoords, coordRow,
	        btnAddSensor, btnAddHub, btnRemove,
	        lblSelSensor, cbSelectSensor,
	        lblAnt, cbAntType, lblOrient, tfOrient, lblBeam, tfBeam,
	        btnApplyConfig
	    );

	    // ===== Frecuencia =====
	    Label lblFreq = new Label("Frecuencia");
	    ComboBox<String> cbFreq = new ComboBox<>();
	    cbFreq.getItems().addAll("2.4 GHz", "5 GHz");
	    cbFreq.getSelectionModel().select("2.4 GHz");
	    cbFreq.setOnAction(e -> {
	        String sel = cbFreq.getSelectionModel().getSelectedItem();
	        env.setFreqMHz("2.4 GHz".equals(sel) ? Environment.WIFI_24_GHZ_MHZ : Environment.WIFI_5_GHZ_MHZ);
	        if (heatmapView.isVisible()) drawHeatmap();
	        drawWallsInLayer();
	    });

	    // ===== Heatmap =====
	    Button btnHeatmap = new Button("Heatmap");
	    btnHeatmap.setOnAction(e -> {
	        boolean newVis = !heatmapView.isVisible();
	        heatmapView.setVisible(newVis);
	        if (newVis) drawHeatmap();
	    });

	    Button btnClearHeatmap = new Button("Quitar Heatmap");
	    btnClearHeatmap.setOnAction(e -> {
	        heatmapView.setVisible(false);
	        GraphicsContext g = gridCanvas.getGraphicsContext2D();
	        g.clearRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());
	    });

	    chkShowSnr = new CheckBox("Mostrar SNR");
	    chkShowSnr.setSelected(false);
	    chkShowSnr.setOnAction(e -> {
	        if (heatmapView.isVisible()) drawHeatmap();
	    });

	    // ===== BW (Hz) =====
	    Label lblBW = new Label("BW (Hz)");
	    TextField tfBW = new TextField(String.format("%.3g", env.getBandwidthHz()));
	    Button btnBW = new Button("BW→");
	    btnBW.setOnAction(e -> {
	        try {
	            double bw = Double.parseDouble(tfBW.getText());
	            env.setBandwidthHz(bw);
	            if (heatmapView.isVisible()) drawHeatmap();
	        } catch (NumberFormatException ex) { }
	    });

	    // ===== NF (dB) =====
	    Label lblNF = new Label("NF (dB)");
	    TextField tfNF = new TextField(String.format("%.1f", env.getNoiseFigureDb()));
	    Button btnNF = new Button("NF→");
	    btnNF.setOnAction(e -> {
	        try {
	            double nf = Double.parseDouble(tfNF.getText());
	            env.setNoiseFigureDb(nf);
	            if (heatmapView.isVisible()) drawHeatmap();
	        } catch (NumberFormatException ex) { }
	    });

	    // ===== Plantillas de paredes =====
	    Label lblTpl = new Label("Plantillas de paredes");
	    HBox tplRow = new HBox(8);
	    Button tplRect = new Button("Tpl Rectángulo");
	    Button tplRoom = new Button("Tpl Habitación");
	    Button tplHouse = new Button("Tpl House L");

	    tplRect.setOnAction(e -> {
	        env.setWalls(Environment.WALLS_TEMPLATE_House1);
	        drawWallsInLayer();
	        if (heatmapView.isVisible()) drawHeatmap();
	    });
	    tplRoom.setOnAction(e -> {
	        env.setWalls(Environment.WALLS_TEMPLATE_2_MAT);
	        drawWallsInLayer();
	        if (heatmapView.isVisible()) drawHeatmap();
	    });
	    tplHouse.setOnAction(e -> {
	        env.setWalls(Environment.WALLS_TEMPLATE_DRAWN);
	        drawWallsInLayer();
	        if (heatmapView.isVisible()) drawHeatmap();
	    });
	    tplRow.getChildren().addAll(tplRect, tplRoom, tplHouse);

	    // ===== Acciones =====
	    Label lblActions = new Label("Acciones");
	    HBox actions1 = new HBox(8);
	    Button btnRays = new Button("Rayos (legacy)");
	    btnRays.setOnAction(e -> {
	        centerPane.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));
	        launchRaysForAllSensors(centerPane);
	    });
	    Button btnWaves = new Button("Ondas");
	    btnWaves.setOnAction(e -> {
	        centerPane.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("wave")));
	        for (Sensor s : config.getSensores()) launchWavefronts(centerPane, s);
	    });
	    Button btnParams = new Button("Parámetros");
	    btnParams.setOnAction(e -> showParametersWindow());
	    actions1.getChildren().addAll(btnRays, btnWaves, btnParams);

	    // ===== Montaje lateral =====
	    side.getChildren().addAll(
	        deviceBox,
	        lblFreq, cbFreq,
	        btnHeatmap, btnClearHeatmap, chkShowSnr,
	        lblBW, new HBox(6, tfBW, btnBW),
	        lblNF, new HBox(6, tfNF, btnNF),
	        lblTpl, tplRow,
	        lblActions, actions1
	    );

	    // ===== BorderPane principal =====
	    BorderPane bp = new BorderPane();
	    bp.setCenter(centerPane);
	    bp.setRight(side);

	    drawAxesAndGrid(centerPane);
	    drawWallsInLayer();

	    return bp;
	}



	private VBox buildSidePanel() {
		// Heatmap toggles
		ToggleButton btnHeatmap = new ToggleButton("Heatmap");
		btnHeatmap.setOnAction(e -> {
			showHeatmap = btnHeatmap.isSelected();
			repaintAll();
		});

		CheckBox cbSNR = new CheckBox("Mostrar SNR");
		cbSNR.selectedProperty().addListener((obs, a, b) -> {
			showSNR = b;
			repaintAll();
		});

		// Frecuencia
		ComboBox<String> cbFreq = new ComboBox<>();
		cbFreq.getItems().addAll("2.4 GHz", "5 GHz");
		cbFreq.getSelectionModel().select(0);
		cbFreq.valueProperty().addListener((o, old, v) -> {
			env.setFreqMHz(v.contains("2.4") ? Environment.WIFI_24_GHZ_MHZ : Environment.WIFI_5_GHZ_MHZ);
			repaintAll();
		});

		// BW / NF
		TextField tfBW = new TextField(Double.toString(env.getBandwidthHz()));
		tfBW.setPrefColumnCount(10);
		Button bwApply = new Button("BW→");
		bwApply.setOnAction(e -> {
			try {
				env.setBandwidthHz(Double.parseDouble(tfBW.getText().trim()));
				repaintAll();
			} catch (Exception ex) {
			}
		});

		TextField tfNF = new TextField(Double.toString(env.getNoiseFigureDb()));
		tfNF.setPrefColumnCount(5);
		Button nfApply = new Button("NF→");
		nfApply.setOnAction(e -> {
			try {
				env.setNoiseFigureDb(Double.parseDouble(tfNF.getText().trim()));
				repaintAll();
			} catch (Exception ex) {
			}
		});

		// Templates paredes (materiales)
		Button btnTpl1 = new Button("Tpl Rectángulo");
		btnTpl1.setOnAction(e -> {
			env.setWalls(Environment.WALLS_TEMPLATE_House1);
			walls = new ArrayList<>(env.getWalls());
			repaintAll();
		});

		Button btnTpl2 = new Button("Tpl Habitación");
		btnTpl2.setOnAction(e -> {
			env.setWalls(Environment.WALLS_TEMPLATE_2_MAT);
			walls = new ArrayList<>(env.getWalls());
			repaintAll();
		});

		Button btnTplH = new Button("Tpl House L");
		btnTplH.setOnAction(e -> {
			env.setWalls(Environment.WALLS_TEMPLATE_DRAWN);
			walls = new ArrayList<>(env.getWalls());
			repaintAll();
		});

		// Botones legacy (rayos/ondas/params)
		Button btnSimular = new Button("Rayos (legacy)");
		btnSimular.setOnAction(e -> {
			Pane center = (Pane) ((BorderPane) rootGroup.getParent().getParent()).getCenter();
			center.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));
			launchRaysForAllSensors(center);
		});

		Button btnWaves = new Button("Ondas");
		btnWaves.setOnAction(e -> {
			Pane center = (Pane) ((BorderPane) rootGroup.getParent().getParent()).getCenter();
			center.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("wave")));
			for (Sensor s : config.getSensores())
				launchWavefronts(center, s);
		});

		Button btnParams = new Button("Parámetros");
		btnParams.setOnAction(e -> showParametersWindow());

		VBox box = new VBox(10, new Label("Frecuencia"), cbFreq, btnHeatmap, cbSNR, new Separator(),
				new HBox(6, new Label("BW (Hz)"), tfBW, bwApply), new HBox(6, new Label("NF (dB)"), tfNF, nfApply),
				new Separator(), new Label("Plantillas de paredes"), new HBox(6, btnTpl1, btnTpl2, btnTplH),
				new Separator(), new Label("Acciones"), new HBox(6, btnSimular, btnWaves, btnParams));
		box.setPadding(new Insets(10));
		return box;
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
		}

		Pane center = (Pane) ((BorderPane) rootGroup.getParent().getParent()).getCenter();
		drawAxesAndGrid(center);
		drawWallsInLayer();
		drawHubAndSensors(center);
	}

	private void drawAxesAndGrid(Pane root) {
		root.getChildren().removeIf(n -> "grid".equals(n.getId()));
		Group g = new Group();
		g.setId("grid");

		Font tickFont = Font.font(11);
		Color gridColor = Color.web("#eeeeee");

		for (int x = 0; x <= GRID_MAX_X; x++) {
			double xx = px(x);
			Line v = new Line(xx, py(0), xx, py(GRID_MAX_Y));
			v.setStroke(gridColor);
			g.getChildren().add(v);
			Label lab = label(Integer.toString(x), xx, py(0) + 12, tickFont, Color.GRAY);
			g.getChildren().add(lab);
		}
		for (int y = 0; y <= GRID_MAX_Y; y++) {
			double yy = py(y);
			Line h = new Line(px(0), yy, px(GRID_MAX_X), yy);
			h.setStroke(gridColor);
			g.getChildren().add(h);
			Label lab = label(Integer.toString(y), px(0) - 20, yy - 5, tickFont, Color.GRAY);
			g.getChildren().add(lab);
		}
		g.getChildren().addAll(label("X", px(GRID_MAX_X) + 15, py(0) - 10, Font.font(14), Color.BLACK),
				label("Y", px(0) - 20, py(GRID_MAX_Y) + 15, Font.font(14), Color.BLACK));
		root.getChildren().add(g);
	}

	/** Dibuja las paredes actuales de env, limpiando las anteriores. */
	private void drawWalls(Pane root) {
		// 1) Borrar paredes antiguas (las marcamos con layer="wall")
		root.getChildren().removeIf(n -> "wall".equals(n.getProperties().get("layer")));

		// 2) Dibujar paredes nuevas
		for (Wall w : env.getWalls()) {
			Line l = new Line(px(w.getX1()), py(w.getY1()), px(w.getX2()), py(w.getY2()));
			l.setStroke(colorForMaterial(w.getMaterial()));
			l.setStrokeWidth(strokeForMaterial(w.getMaterial(), w.getThicknessCm()));
			l.setOpacity(0.95);

			// marca para poder borrarlas la próxima vez
			l.getProperties().put("layer", "wall");

			// tooltip con material y pérdidas
			double lossDb = (w.getMaterial() == null) ? 0.0
					: w.getMaterial().lossDb(env.getFreqMHz(), w.getThicknessCm());
			Tooltip t = new Tooltip(String.format("%s (%.1f cm)\nLoss @%.0f MHz: %.1f dB",
					w.getMaterial() != null ? w.getMaterial().getName() : "WALL", w.getThicknessCm(), env.getFreqMHz(),
					lossDb));
			Tooltip.install(l, t);

			root.getChildren().add(l);
		}
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

			

			

			dot.setOnMousePressed(e -> {
				if (e.isSecondaryButtonDown()) {
					cm.show(dot, e.getScreenX(), e.getScreenY());
				} else {
					cm.hide();
				}
			});

			Label lab = label(s.getNombre(), cx + 10, cy - 10, Font.font(12), Color.DARKBLUE);

			String tipText = String.format("%s\nPosicion: (%d,%d)\nPotencia Tx: %.1f dBm\nValor: %.1f", s.getNombre(), s.getX(),
					s.getY(), s.getTxDbm(), s.getValue());
			Tooltip tip = new Tooltip(tipText);
			Tooltip.install(dot, tip);

			g.getChildren().addAll(dot, lab);
			nodos.put(s, new NodeBundle(dot, lab, tip));
		}
		Pane center = (Pane) ((BorderPane) rootGroup.getParent().getParent()).getCenter();
		center.getChildren().add(g);
	}

	// ============================
	// Heatmap RSSI / SNR
	// ============================
	private void renderHeatmap() {
		PixelWriter pw = heatmapImg.getPixelWriter();

		// Para cada celda, suma incoherente de potencias de todos los sensores
		for (int y = 0; y < gridH; y++) {
			for (int x = 0; x < gridW; x++) {
				double total_mW = 0.0;
				double bestPrx = -999.0;
				for (Sensor s : config.getSensores()) {
					RayMetrics m = computeDirectLink(x + 0.5, y + 0.5, s);
					double mw = Math.pow(10.0, m.getPrxDbm() / 10.0);
					total_mW += mw;
					if (m.getPrxDbm() > bestPrx)
						bestPrx = m.getPrxDbm();
				}
				double prxSumDbm = 10.0 * Math.log10(Math.max(total_mW, 1e-15));
				double valDb = showSNR ? (prxSumDbm - env.noiseFloorDbm()) : prxSumDbm;
				pw.setColor(x, y, colorScale(valDb, showSNR));
			}
		}
	}

	private RayMetrics computeDirectLink(double rxX, double rxY, Sensor s) {
		double txX = s.getX();
		double txY = s.getY();
		double d = Math.hypot(rxX - txX, rxY - txY);

		double fspl = Propagation.fsplLossDb(d, env.getFreqMHz());
		double wallLoss = Propagation.wallLossAlongLine(env.getWalls(), env.getFreqMHz(), txX, txY, rxX, rxY);

		// Ganancias (si aún no implementaste patrones, usa 0/Gr)
		double gt = 0.0;
		double gr = config.hasHub() ? config.getHub().getGrDb() : 0.0;

		double prx = s.getTxDbm() + gt + gr - fspl - env.getAlphaDbPerMeter() * d - wallLoss;

		RayMetrics m = new RayMetrics();
		m.setTxDbm(s.getTxDbm());
		m.setGtDb(gt);
		m.setGrDb(gr);
		m.setDistanceTraveled(d);
		m.setFsplDb(fspl);
		m.setWallLossDb(wallLoss);
		m.setPrxDbm(prx);
		m.setNoiseDbm(env.noiseFloorDbm());
		m.setSnrDb(prx - env.noiseFloorDbm());
		return m;
	}

	private Color colorScale(double valDb, boolean isSnr) {
		double min = isSnr ? 0.0 : -100.0;
		double max = isSnr ? 40.0 : -50.0;
		double t = (valDb - min) / (max - min);
		t = Math.max(0.0, Math.min(1.0, t));
		if (t < 0.5) {
			double k = t / 0.5; // 0 → 1
			return new Color(0, k, 1 - k, 1);
		} else {
			double k = (t - 0.5) / 0.5; // 0 → 1
			return new Color(k, 1 - k, 0, 1);
		}
	}

	// ============================
	// Edición de paredes (opcional)
	// ============================
	private void enableWallEditing() {
		gridCanvas.setOnMouseClicked(e -> {
			if (!e.isSecondaryButtonDown())
				return;
			double gx = toGridXd(e.getX());
			double gy = toGridYd(e.getY());
			Wall w = findWallNear(px(gx), py(gy));
			if (w == null)
				return;

			ContextMenu cm = new ContextMenu();
			Menu mMat = new Menu("Material");
			MenuItem miCon = new MenuItem("Hormigón");
			miCon.setOnAction(a -> {
				w.setMaterial(MaterialsDB.CONCRETE);
				repaintAll();
			});
			MenuItem miBri = new MenuItem("Ladrillo");
			miBri.setOnAction(a -> {
				w.setMaterial(MaterialsDB.BRICK);
				repaintAll();
			});
			MenuItem miDry = new MenuItem("Tabique");
			miDry.setOnAction(a -> {
				w.setMaterial(MaterialsDB.DRYWALL);
				repaintAll();
			});
			MenuItem miGla = new MenuItem("Cristal");
			miGla.setOnAction(a -> {
				w.setMaterial(MaterialsDB.GLASS);
				repaintAll();
			});
			MenuItem miMet = new MenuItem("Puerta metálica");
			miMet.setOnAction(a -> {
				w.setMaterial(MaterialsDB.METAL_DOOR);
				repaintAll();
			});
			mMat.getItems().addAll(miCon, miBri, miDry, miGla, miMet);

			cm.getItems().addAll(mMat);
			cm.show(gridCanvas, e.getScreenX(), e.getScreenY());
		});
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

	private void enableCleanProbe() {
		probeTip.setShowDelay(Duration.millis(80));

		gridCanvas.setOnMouseMoved(e -> {
			int gx = (int) ((e.getX() - MARGIN) / cellSizePx);
			int gy = (int) ((HEIGHT - MARGIN - e.getY()) / cellSizePx);
			if (gx < 0 || gy < 0 || gx >= gridW || gy >= gridH) {
				return;
			}

			CellResult cell = (lastHeatmapResult != null)
					? lastHeatmapResult.getCell(gx, gy)
					: IndoorWaveEngine.computeCell(env, config.getSensores(), gx + 0.5, gy + 0.5, buildReceiverSettings());
			if (cell == null) {
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
		});
	}

	private void enableProbe() {
		gridCanvas.setOnMouseMoved(e -> {
			int gx = (int) ((e.getX() - MARGIN) / cellSizePx);
			int gy = (int) ((HEIGHT - MARGIN - e.getY()) / cellSizePx);
			if (gx < 0 || gy < 0 || gx >= gridW || gy >= gridH)
				return;

			CellResult cell = (lastHeatmapResult != null)
					? lastHeatmapResult.getCell(gx, gy)
					: IndoorWaveEngine.computeCell(env, config.getSensores(), gx + 0.5, gy + 0.5, buildReceiverSettings());
			if (cell == null) return;

			String strongestPaths = cell.contributions().stream()
					.limit(3)
					.map(PathContribution::summary)
					.reduce((a, b) -> a + "\n" + b)
					.orElse("Sin trayectorias");

			probeTip.setText(String.format(Locale.US,
					"Cell(%d,%d)\nDominante: %s\nPotencia total: %.1f dBm\nSeñal: %.1f dBm\nInterferencia: %.1f dBm\n"
							+ "Ruido: %.1f dBm\nSNR: %.1f dB\nSINR: %.1f dB\nBER: %.2e\nCapacidad: %.2f Mbps\n"
							+ "Margen: %.1f dB\nFase: %.1f°\nTrayectorias: %d\n%s",
					gx, gy,
					cell.dominantSensorId(),
					cell.totalPowerDbm(),
					cell.signalPowerDbm(),
					cell.interferencePowerDbm(),
					cell.noiseDbm(),
					cell.snrDb(),
					cell.sinrDb(),
					cell.ber(),
					cell.capacityMbps(),
					cell.linkMarginDb(),
					Math.toDegrees(cell.fieldPhaseRad()),
					cell.pathCount(),
					strongestPaths));
			Tooltip.install(gridCanvas, probeTip);
		});
	}

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

	private void showParametersWindow() {
		Stage paramStage = new Stage();
		paramStage.setTitle("Simulation Parameters");

		StringBuilder sb = new StringBuilder();
		sb.append("📡 Simulation Parameters\n\n");

		if (config.hasHub()) {
			Hub h = config.getHub();
			sb.append("Hub: ").append(h.getId()).append(" at (").append(h.getX()).append(",").append(h.getY())
					.append(")\n")
					.append("Rx Gain: ").append(h.getGrDb()).append(" dB\n")
					.append("Polarización: ").append(String.format(Locale.US, "%.1f°", h.getPolarizationDeg()))
					.append("\n\n");

			SimulationSettings hubSettings = buildReceiverSettings();
			hubSettings.setReceiverGainDb(h.getGrDb());
			hubSettings.setReceiverPolarizationDeg(h.getPolarizationDeg());
			CellResult hubCell = IndoorWaveEngine.computeCell(env, config.getSensores(), h.getX(), h.getY(), hubSettings);
			sb.append("Hub / mapa combinado\n");
			sb.append("Modo: ").append(hubSettings.getPropagationMode()).append("\n");
			sb.append("Potencia total: ").append(String.format(Locale.US, "%.2f dBm", hubCell.totalPowerDbm())).append("\n");
			sb.append("Señal dominante: ").append(hubCell.dominantSensorId()).append(" (")
					.append(String.format(Locale.US, "%.2f dBm", hubCell.signalPowerDbm())).append(")\n");
			sb.append("Interferencia: ").append(String.format(Locale.US, "%.2f dBm", hubCell.interferencePowerDbm())).append("\n");
			sb.append("SINR: ").append(String.format(Locale.US, "%.2f dB", hubCell.sinrDb())).append("\n");
			sb.append("BER: ").append(String.format(Locale.US, "%.2e", hubCell.ber())).append("\n");
			sb.append("Capacidad: ").append(String.format(Locale.US, "%.2f Mbps", hubCell.capacityMbps())).append("\n");
			sb.append("Trayectorias: ").append(hubCell.pathCount()).append("\n\n");
		}

		for (Sensor s : config.getSensores()) {
			sb.append("Sensor ").append(s.getId()).append(" — ").append(s.getNombre()).append("\n");
			sb.append("Pos: (").append(s.getX()).append(",").append(s.getY()).append(")\n");
			sb.append("Tx Power: ").append(s.getTxDbm()).append(" dBm\n");
			sb.append("Ganancia: ").append(String.format(Locale.US, "%.1f dB", s.getTxGainDb())).append("\n");
			sb.append("Patrón: ").append(String.format(Locale.US, "%.2f", s.getPatternSharpness())).append("\n");
			sb.append("Polarización: ").append(String.format(Locale.US, "%.1f°", s.getPolarizationDeg())).append("\n");

			if (config.hasHub()) {
				Hub h = config.getHub();
				SimulationSettings singleSensorSettings = buildReceiverSettings();
				singleSensorSettings.setReceiverGainDb(h.getGrDb());
				singleSensorSettings.setReceiverPolarizationDeg(h.getPolarizationDeg());
				CellResult singleSensorLink = IndoorWaveEngine.computeCell(env, List.of(s), h.getX(), h.getY(), singleSensorSettings);

				sb.append("Distance: ").append(String.format(Locale.US, "%.2f m",
						Math.hypot(h.getX() - s.getX(), h.getY() - s.getY()))).append("\n");
				sb.append("Potencia recibida: ").append(String.format(Locale.US, "%.2f dBm", singleSensorLink.totalPowerDbm())).append("\n");
				sb.append("SNR: ").append(String.format(Locale.US, "%.2f dB", singleSensorLink.snrDb())).append("\n");
				sb.append("Margen de enlace: ").append(String.format(Locale.US, "%.2f dB", singleSensorLink.linkMarginDb())).append("\n");
				sb.append("BER: ").append(String.format(Locale.US, "%.2e", singleSensorLink.ber())).append("\n");
				sb.append("Capacidad (Shannon): ").append(String.format(Locale.US, "%.2f Mbps", singleSensorLink.capacityMbps())).append("\n");
				sb.append("Trayectorias: ").append(singleSensorLink.pathCount()).append("\n\n");
			}
		}

		sb.append("🌍 Global Parameters:\n");
		sb.append("Freq: ").append(env.getFreqMHz()).append(" MHz\n");
		sb.append("BW: ").append(env.getBandwidthHz()).append(" Hz\n");
		sb.append("NF: ").append(env.getNoiseFigureDb()).append(" dB\n");
		sb.append("Noise floor: ").append(String.format(Locale.US, "%.2f dBm", env.noiseFloorDbm())).append("\n");
		sb.append("Modo heatmap: ").append(simulationSettings.getPropagationMode()).append("\n");
		sb.append("Métrica: ").append(simulationSettings.getMapMetric()).append("\n");
		sb.append("Fading: ").append(simulationSettings.getFadingModel()).append("\n");
		sb.append("n indoor: ").append(String.format(Locale.US, "%.2f", simulationSettings.getLogDistanceExponent())).append("\n");
		sb.append("Difracción: ").append(simulationSettings.isDiffractionEnabled() ? "ON" : "OFF").append("\n");
		sb.append("Dispersión: ").append(simulationSettings.isScatteringEnabled() ? "ON" : "OFF").append("\n");
		sb.append("Culling: ").append(String.format(Locale.US, "%.0f dBm", simulationSettings.getCullingThresholdDbm())).append("\n");

		TextArea area = new TextArea(sb.toString());
		area.setEditable(false);
		area.setWrapText(true);
		Scene scene = new Scene(area, 520, 680);
		paramStage.setScene(scene);
		paramStage.show();
	}

	// ============================
	// Raytracing legacy (sin cambios de materiales; usa config.getObstaculos())
	// ============================
	private void launchRaysForAllSensors(Pane root) {
		root.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));
		rayMetricsList.clear();

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

	/** Raytracing simple con rebotes contra paredes (env.getWalls()). */
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
					rayMetricsList.add(metrics);

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
		double targetRadiusPx = dMeters * SCALE; // 1 celda = 1 m

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
			if (!hitShown[0] && wave.getRadius() >= (targetRadiusPx - SCALE * 0.5)) {
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
	private static double px(double gridX) {
		return MARGIN + gridX * SCALE;
	}

	private static double py(double gridY) {
		return HEIGHT - MARGIN - gridY * SCALE;
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

	private static double toGridXd(double pixelX) {
		return (pixelX - MARGIN) / (double) SCALE;
	}

	private static double toGridYd(double pixelY) {
		return (HEIGHT - MARGIN - pixelY) / (double) SCALE;
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

	private Color colorFor(Wall w) {
		String n = (w.getMaterial() != null ? w.getMaterial().getName() : "");
		if (n.contains("Hormig"))
			return Color.GRAY;
		if (n.contains("Ladr"))
			return Color.SIENNA;
		if (n.contains("Crist"))
			return Color.LIGHTBLUE;
		if (n.contains("Tabique"))
			return Color.BURLYWOOD;
		if (n.contains("Metal"))
			return Color.DARKSLATEGRAY;
		if (n.contains("Made"))
			return Color.SADDLEBROWN;
		return Color.BLACK;
	}

	/** Dibuja un sensor en el pane con su tooltip. */
	private void pintarSensor(Pane root, Sensor s, ComboBox<Sensor> cbSelectSensor) {
	    Circle circle = new Circle(px(s.getX()), py(s.getY()), 6, Color.BLUE);
	    circle.setStroke(Color.BLACK);
	    circle.setStrokeWidth(1.2);
	    circle.getProperties().put("sensor", s);

	    Tooltip tooltip = new Tooltip(s.toString());
	    Tooltip.install(circle, tooltip);

	    // === Click para seleccionar sensor en el ComboBox ===
	    circle.setOnMouseClicked(e -> {
	        cbSelectSensor.getSelectionModel().select(s);
	    });

	    root.getChildren().add(circle);

	    // Texto con el nombre
	    Text label = new Text(px(s.getX()) + 10, py(s.getY()), s.getNombre());
	    label.setFont(Font.font(12));
	    root.getChildren().add(label);
	}


	/** Dibuja el hub en el pane con su tooltip. */
	private void pintarHub(Pane root, Hub h) {
	    Rectangle rect = new Rectangle(px(h.getX()) - 6, py(h.getY()) - 6, 12, 12);
	    rect.setFill(Color.RED);
	    rect.setStroke(Color.BLACK);
	    rect.setStrokeWidth(1.5);
	    rect.getProperties().put("hub", h);

	    Tooltip tooltip = new Tooltip("Hub " + h.getId() + " en (" + h.getX() + "," + h.getY() + ")");
	    Tooltip.install(rect, tooltip);

	    // === Click para mostrar que el hub está seleccionado ===
	    rect.setOnMouseClicked(e -> {
	        System.out.println("Hub seleccionado: " + h.getId());
	        // Aquí podrías poner, por ejemplo, un Label en la UI
	        // o incluso habilitar controles específicos del Hub
	    });

	    root.getChildren().add(rect);

	    // Texto con el nombre
	    Text label = new Text(px(h.getX()) + 12, py(h.getY()), h.getNombre());
	    label.setFont(Font.font(12));
	    root.getChildren().add(label);
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

	/** Pérdida de transmisión (penetración) al atravesar una pared, en dB. */
	private double wallTransmissionLossDb(Wall w) {
		if (w.getMaterial() == null)
			return 0.0;
		// Si tu Material tiene API distinta, adapta aquí:
		return w.getMaterial().lossDb(env.getFreqMHz(), w.getThicknessCm());
	}

	/** Pérdida de reflexión al rebotar en una pared, en dB (modelo simple). */
	private double wallReflectionLossDb(Wall w) {
		// Modelo simple: una fracción de la pérdida de transmisión, con mínimo.
		double t = wallTransmissionLossDb(w);
		return Math.max(3.0, 0.5 * t); // p.ej. si ladrillo 7 dB → reflexión ≈ 3.5 dB
	}

	// Paleta "turbo" aproximada (suave y moderna)
	private Color lerpTurbo(double t) {
		// clamp
		t = Math.max(0.0, Math.min(1.0, t));
		// aproximación simple: puedes sustituir por una LUT si quieres más fidelidad
		// aquí uso una mezcla de stops para un efecto turbo-like
		return Color.hsb(260 * (1 - t), 0.95, 0.95); // simple: morado→azul→...→rojo
	}

	// Paleta "jet" clásica (azul→cian→verde→amarillo→rojo)
	private Color lerpJet(double t) {
		t = Math.max(0.0, Math.min(1.0, t));
		double r = Math.min(Math.max(1.5 - Math.abs(4 * t - 3), 0), 1);
		double g = Math.min(Math.max(1.5 - Math.abs(4 * t - 2), 0), 1);
		double b = Math.min(Math.max(1.5 - Math.abs(4 * t - 1), 0), 1);
		return new Color(r, g, b, 0.85);
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

	/* === Helpers para los ángulos y cuadrantes === */
	private static double deg0to360(double a) {
		a = a % 360.0;
		if (a < 0)
			a += 360.0;
		return a;
	}
}
