package UI;

import core.Configuracion;
import core.Hub;
import core.Obstacle;            // LEGACY: usado aún por el raytracing viejo
import core.RayMetrics;
import core.Sensor;
import core.WaveContribution;
import core.Wall;
import core.Propagation;
import core.material.MaterialsDB;
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
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smart Home Visual Grid + Heatmap RSSI/SNR + Raytracing legacy.
 */
public class VisualGridApp extends Application {

    // ============================
    //  Constantes de dibujo / Grid
    // ============================
    public static final int WIDTH  = 1000;
    public static final int HEIGHT = 700;
    public static final int MARGIN = 60;
    public static final int SCALE  = 20;   // px por celda (1 celda = 1 m)
    public static final int GRID_MAX_X = 40;
    public static final int GRID_MAX_Y = 24;

    // ============================
    //  Parámetros raytracing legacy
    // ============================
    private static final int RAY_STEP_MS = 30;
    private static final int MAX_BOUNCES = 10;
    private static final double HUB_HIT_THRESHOLD = 0.30; // radio “acierto” (m)
    private static final double RAY_STEP_METERS = 1.0;
    private static final double EPS = 1e-3;

    // ============================
    //  Estado global
    // ============================
    private final Configuracion config = new Configuracion();   // tu contenedor legacy
    private final Map<Sensor, NodeBundle> nodos = new HashMap<>();
    private final List<WaveContribution> waveField = new ArrayList<>();
    private final List<RayMetrics> rayMetricsList = new ArrayList<>();

    // Nuevo motor simple
    private Environment env;            // parámetros físicos + paredes (materiales)
    private List<Wall> walls = new ArrayList<>();
    private Group wallsLayer;


    // UI base
    private Group rootGroup;            // superpone heatmap + canvas
    private Canvas gridCanvas;          // rejilla + paredes + objetos
    private WritableImage heatmapImg;   // 1 px por celda
    private ImageView heatmapView;      // escalado a pixeles

    // Flags heatmap
    private boolean showHeatmap = false;
    private boolean showSNR = false;
    private CheckBox chkShowSnr;


    // Dimensiones raster
    private int cellSizePx = SCALE;
    private int gridW = GRID_MAX_X;
    private int gridH = GRID_MAX_Y;

    // Hub UI
    private Group     hubNode = null;
    private Rectangle hubBox;
    private Tooltip   hubTooltip;

    // Olas visuales
    private static final double WAVE_INITIAL_RADIUS  = 5;
    private static final double WAVE_MAX_RADIUS      = 300;
    private static final double WAVE_DURATION_SEC    = 10.0;
    private static final double WAVE_INITIAL_OPACITY = 0.6;
    private static final double WAVE_FINAL_OPACITY   = 0.0;
    private static final double WAVE_INTERVAL_MS     = 250;

    // Aux record
    private record NodeBundle(Circle dot, Label label, Tooltip tip) {}

    // ============================
    //  Main
    // ============================
    public static void main(String[] args) { launch(); }

    // ============================
    //  JavaFX
    // ============================
    @Override
    public void start(Stage stage) {
        initModel();
        Parent root = buildUI();

     // 1) Sensor y Hub
        Sensor S1 = new Sensor("S1", "Temp salón", 5, 5, 22.5);
        S1.setTxDbm(20.0);
        config.addSensor(S1);

        Hub H1 = new Hub("H1", "Hub central", 15, 5);
        H1.setGrDb(0.0);
        config.setHub(H1);

        // 2) Entorno
        env.setFreqMHz(Environment.WIFI_24_GHZ_MHZ);  // 2400 MHz
        env.setBandwidthHz(20e6);
        env.setNoiseFigureDb(7.0);
        env.setAlphaDbPerMeter(0.0);    

        // Obstáculos legacy (para rayos viejos). En paralelo tenemos walls en env.
        // Puedes quitar esto si ya migraste todo a env.setWalls(...).
        config.setObstaculos(List.of()); // vacío para no duplicar paredes visuales

        
        // 1) Asegura que el panel lateral tenga ancho fijo y fondo opacos
        if (root instanceof BorderPane bp && bp.getRight() instanceof Region side) {
            side.setPrefWidth(250); // elige el ancho que prefieras para el panel
            side.setBackground(new Background(
                new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)
            ));
            side.setBorder(new Border(new BorderStroke(
                Color.web("#dddddd"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT
            )));
        }

        // 2) Scene SIN width/height fijos (se calcula a partir de los preferred sizes)
        Scene scene = new Scene(root);
        stage.setTitle("Smart Home — Visual Grid con Heatmap");
        stage.setScene(scene);

        // 3) Ajusta la ventana al tamaño preferido de root (canvas + panel)
        stage.sizeToScene();
        stage.show();

        // Dibujo inicial + utilidades
        repaintAll();
        enableWallEditing();  // menú contextual para cambiar material
        enableProbe();        // tooltip con métricas al mover ratón
    }


    // ============================
    //  Inicialización y UI
    // ============================
    private void initModel() {
        env = new Environment();
        // Carga una plantilla con materiales
       // env.setWalls(Environment.WALLS_TEMPLATE_HOUSE_MAT);
        walls = new ArrayList<>(env.getWalls());
    }

    private Parent buildUI() {
        // ===== Centro: heatmap (debajo) + wallsLayer (medio) + canvas (encima) =====
        gridCanvas = new Canvas(gridW * cellSizePx + 2 * MARGIN,
                                gridH * cellSizePx + 2 * MARGIN);

        heatmapImg = new WritableImage(gridW, gridH); // 1 px por celda
        heatmapView = new ImageView(heatmapImg);
        heatmapView.setFitWidth(gridW * cellSizePx);
        heatmapView.setFitHeight(gridH * cellSizePx);
        heatmapView.setOpacity(0.72);
        heatmapView.setVisible(false); // oculto por defecto

        StackPane heatmapHolder = new StackPane(heatmapView);
        heatmapHolder.setPadding(new Insets(MARGIN, MARGIN, MARGIN, MARGIN));

        wallsLayer = new Group();                // <<< capa exclusiva para paredes
        Pane canvasHolder = new Pane(gridCanvas);

        rootGroup = new Group(heatmapHolder, wallsLayer, canvasHolder);

        // Lo envolvemos en un Pane para reutilizar métodos que esperan Pane
        Pane centerPane = new Pane(rootGroup);

        // ===== Lateral derecho =====
        VBox side = new VBox(10);
        side.setPadding(new Insets(10));
        side.setPrefWidth(250);
        side.setBackground(new Background(
            new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)
        ));
        side.setBorder(new Border(new BorderStroke(
            Color.web("#dddddd"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT
        )));

        // Frecuencia
        Label lblFreq = new Label("Frecuencia");
        ComboBox<String> cbFreq = new ComboBox<>();
        cbFreq.getItems().addAll("2.4 GHz", "5 GHz");
        cbFreq.getSelectionModel().select("2.4 GHz");
        cbFreq.setOnAction(e -> {
            String sel = cbFreq.getSelectionModel().getSelectedItem();
            env.setFreqMHz("2.4 GHz".equals(sel) ? Environment.WIFI_24_GHZ_MHZ
                                                 : Environment.WIFI_5_GHZ_MHZ);
            if (heatmapView.isVisible()) drawHeatmap();
            drawWallsInLayer(); // por si el tooltip de pérdidas cambia con la frecuencia
        });

        // Botón Heatmap (toggle)
        Button btnHeatmap = new Button("Heatmap");
        btnHeatmap.setOnAction(e -> {
            boolean newVis = !heatmapView.isVisible();
            heatmapView.setVisible(newVis);
            if (newVis) drawHeatmap();
        });

        // CheckBox Mostrar SNR
        chkShowSnr = new CheckBox("Mostrar SNR");
        chkShowSnr.setSelected(false);
        chkShowSnr.setOnAction(e -> { if (heatmapView.isVisible()) drawHeatmap(); });

        // BW (Hz)
        Label lblBW = new Label("BW (Hz)");
        TextField tfBW = new TextField(String.format("%.3g", env.getBandwidthHz()));
        Button btnBW = new Button("BW→");
        btnBW.setOnAction(e -> {
            try {
                double bw = Double.parseDouble(tfBW.getText());
                env.setBandwidthHz(bw);
                if (heatmapView.isVisible()) drawHeatmap();
            } catch (NumberFormatException ex) { /* opc: mostrar alerta */ }
        });

        // NF (dB)
        Label lblNF = new Label("NF (dB)");
        TextField tfNF = new TextField(String.format("%.1f", env.getNoiseFigureDb()));
        Button btnNF = new Button("NF→");
        btnNF.setOnAction(e -> {
            try {
                double nf = Double.parseDouble(tfNF.getText());
                env.setNoiseFigureDb(nf);
                if (heatmapView.isVisible()) drawHeatmap();
            } catch (NumberFormatException ex) { /* opc: alerta */ }
        });

        // Plantillas de paredes (FUNCIONALES con wallsLayer)
        Label lblTpl = new Label("Plantillas de paredes");
        HBox tplRow = new HBox(8);
        Button tplRect  = new Button("Tpl Rectángulo");
        Button tplRoom  = new Button("Tpl Habitación");
        Button tplHouse = new Button("Tpl House L");

        tplRect.setOnAction(e -> {
            env.setWalls(Environment.WALLS_TEMPLATE_1_MAT);
            drawWallsInLayer();
            if (heatmapView.isVisible()) drawHeatmap();
        });
        tplRoom.setOnAction(e -> {
            env.setWalls(Environment.WALLS_TEMPLATE_2_MAT);
            drawWallsInLayer();
            if (heatmapView.isVisible()) drawHeatmap();
        });
        tplHouse.setOnAction(e -> {
            env.setWalls(Environment.WALLS_TEMPLATE_HOUSE_MAT);
            drawWallsInLayer();
            if (heatmapView.isVisible()) drawHeatmap();
        });
        tplRow.getChildren().addAll(tplRect, tplRoom, tplHouse);

        // Acciones
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

        // Montaje del lateral
        side.getChildren().addAll(
            lblFreq, cbFreq,
            btnHeatmap,
            chkShowSnr,
            lblBW, new HBox(6, tfBW, btnBW),
            lblNF, new HBox(6, tfNF, btnNF),
            lblTpl, tplRow,
            lblActions, actions1
        );

        // ===== BorderPane principal =====
        BorderPane bp = new BorderPane();
        bp.setCenter(centerPane);
        bp.setRight(side);

        // Dibujo inicial (rejilla + paredes)
        drawAxesAndGrid(centerPane);
        drawWallsInLayer(); // usa la capa

        return bp;
    }



    private VBox buildSidePanel() {
        // Heatmap toggles
        ToggleButton btnHeatmap = new ToggleButton("Heatmap");
        btnHeatmap.setOnAction(e -> { showHeatmap = btnHeatmap.isSelected(); repaintAll(); });

        CheckBox cbSNR = new CheckBox("Mostrar SNR");
        cbSNR.selectedProperty().addListener((obs, a, b) -> { showSNR = b; repaintAll(); });

        // Frecuencia
        ComboBox<String> cbFreq = new ComboBox<>();
        cbFreq.getItems().addAll("2.4 GHz", "5 GHz");
        cbFreq.getSelectionModel().select(0);
        cbFreq.valueProperty().addListener((o,old,v) -> {
            env.setFreqMHz(v.contains("2.4") ? Environment.WIFI_24_GHZ_MHZ
                                             : Environment.WIFI_5_GHZ_MHZ);
            repaintAll();
        });

        // BW / NF
        TextField tfBW = new TextField(Double.toString(env.getBandwidthHz())); tfBW.setPrefColumnCount(10);
        Button bwApply = new Button("BW→");
        bwApply.setOnAction(e -> { try { env.setBandwidthHz(Double.parseDouble(tfBW.getText().trim())); repaintAll(); } catch(Exception ex){} });

        TextField tfNF = new TextField(Double.toString(env.getNoiseFigureDb())); tfNF.setPrefColumnCount(5);
        Button nfApply = new Button("NF→");
        nfApply.setOnAction(e -> { try { env.setNoiseFigureDb(Double.parseDouble(tfNF.getText().trim())); repaintAll(); } catch(Exception ex){} });

        // Templates paredes (materiales)
        Button btnTpl1 = new Button("Tpl Rectángulo");
        btnTpl1.setOnAction(e -> { env.setWalls(Environment.WALLS_TEMPLATE_1_MAT); walls = new ArrayList<>(env.getWalls()); repaintAll(); });

        Button btnTpl2 = new Button("Tpl Habitación");
        btnTpl2.setOnAction(e -> { env.setWalls(Environment.WALLS_TEMPLATE_2_MAT); walls = new ArrayList<>(env.getWalls()); repaintAll(); });

        Button btnTplH = new Button("Tpl House L");
        btnTplH.setOnAction(e -> { env.setWalls(Environment.WALLS_TEMPLATE_HOUSE_MAT); walls = new ArrayList<>(env.getWalls()); repaintAll(); });

        // Botones legacy (rayos/ondas/params)
        Button btnSimular = new Button("Rayos (legacy)");
        btnSimular.setOnAction(e -> {
            Pane center = (Pane)((BorderPane)rootGroup.getParent().getParent()).getCenter();
            center.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));
            launchRaysForAllSensors(center);
        });

        Button btnWaves = new Button("Ondas");
        btnWaves.setOnAction(e -> {
            Pane center = (Pane)((BorderPane)rootGroup.getParent().getParent()).getCenter();
            center.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("wave")));
            for (Sensor s : config.getSensores()) launchWavefronts(center, s);
        });

        Button btnParams = new Button("Parámetros");
        btnParams.setOnAction(e -> showParametersWindow());

        VBox box = new VBox(10,
            new Label("Frecuencia"), cbFreq,
            btnHeatmap, cbSNR,
            new Separator(),
            new HBox(6, new Label("BW (Hz)"), tfBW, bwApply),
            new HBox(6, new Label("NF (dB)"), tfNF, nfApply),
            new Separator(),
            new Label("Plantillas de paredes"),
            new HBox(6, btnTpl1, btnTpl2, btnTplH),
            new Separator(),
            new Label("Acciones"),
            new HBox(6, btnSimular, btnWaves, btnParams)
        );
        box.setPadding(new Insets(10));
        return box;
    }

    // ============================
    //  Dibujo (rejilla / paredes / objetos)
    // ============================
    private void repaintAll() {
        // Heatmap (debajo)
        if (showHeatmap) {
            renderHeatmap();
            heatmapView.setVisible(true);
        } else {
            heatmapView.setVisible(false);
        }
        // Rejilla + paredes + objetos
        Pane center = (Pane)((BorderPane)rootGroup.getParent().getParent()).getCenter();
        drawAxesAndGrid(center);
        drawWalls(center);
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
        g.getChildren().addAll(
            label("X", px(GRID_MAX_X) + 15, py(0) - 10, Font.font(14), Color.BLACK),
            label("Y", px(0) - 20,         py(GRID_MAX_Y) + 15, Font.font(14), Color.BLACK)
        );
        root.getChildren().add(g);
    }

    /** Dibuja las paredes actuales de env, limpiando las anteriores. */
    private void drawWalls(Pane root) {
        // 1) Borrar paredes antiguas (las marcamos con layer="wall")
        root.getChildren().removeIf(n ->
            "wall".equals(n.getProperties().get("layer"))
        );

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
            Tooltip t = new Tooltip(
                String.format("%s (%.1f cm)\nLoss @%.0f MHz: %.1f dB",
                    w.getMaterial()!=null ? w.getMaterial().getName() : "WALL",
                    w.getThicknessCm(), env.getFreqMHz(), lossDb)
            );
            Tooltip.install(l, t);

            root.getChildren().add(l);
        }
    }

    /** Color por material (ajusta a tu MaterialsDB). */
    private Color colorForMaterial(core.material.Material m) {
        if (m == null) return Color.DARKGRAY;
        String name = m.getName().toLowerCase();
        if (name.contains("brick"))     return Color.SADDLEBROWN;
        if (name.contains("concrete"))  return Color.DIMGRAY;
        if (name.contains("drywall"))   return Color.LIGHTSLATEGRAY;
        if (name.contains("glass"))     return Color.DEEPSKYBLUE;
        if (name.contains("metal"))     return Color.DARKRED;
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
        Group g = new Group(); g.setId("hubSensors");

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
            RadioMenuItem miDir  = new RadioMenuItem("Direccional (1 cuadrante)");
            ToggleGroup tg = new ToggleGroup(); miOmni.setToggleGroup(tg); miDir.setToggleGroup(tg);
            miOmni.setSelected(s.getAntennaType() == Sensor.AntennaType.OMNI);
            miDir.setSelected(s.getAntennaType() == Sensor.AntennaType.DIRECTIONAL);
            miOmni.setOnAction(e -> { s.setAntennaType(Sensor.AntennaType.OMNI); repaintAll(); });
            miDir.setOnAction(e -> { s.setAntennaType(Sensor.AntennaType.DIRECTIONAL); repaintAll(); });

            Menu mQuad = new Menu("Cuadrante");
            for (Sensor.Quadrant q : Sensor.Quadrant.values()) {
                RadioMenuItem item = new RadioMenuItem(q.name());
                item.setSelected(s.getDirectiveQuadrant() == q);
                item.setOnAction(e -> { s.setDirectiveQuadrant(q); repaintAll(); });
                item.setToggleGroup(new ToggleGroup());
                mQuad.getItems().add(item);
            }

            mType.getItems().addAll(miOmni, miDir);
            cm.getItems().addAll(mType, mQuad);

            dot.setOnMousePressed(e -> {
                if (e.isSecondaryButtonDown()) {
                    cm.show(dot, e.getScreenX(), e.getScreenY());
                } else {
                    cm.hide();
                }
            });

            

            Label lab = label(s.getNombre(), cx + 10, cy - 10, Font.font(12), Color.DARKBLUE);

            String tipText = String.format("%s\nPos: (%d,%d)\nTx: %.1f dBm\nValor: %.1f",
                    s.getNombre(), s.getX(), s.getY(), s.getTxDbm(), s.getValue());
            Tooltip tip = new Tooltip(tipText);
            Tooltip.install(dot, tip);

            g.getChildren().addAll(dot, lab);
            nodos.put(s, new NodeBundle(dot, lab, tip));
        }
        Pane center = (Pane)((BorderPane)rootGroup.getParent().getParent()).getCenter();
        center.getChildren().add(g);
    }

    // ============================
    //  Heatmap RSSI / SNR
    // ============================
    private void renderHeatmap() {
        PixelWriter pw = heatmapImg.getPixelWriter();

        // Para cada celda, suma incoherente de potencias de todos los sensores
        for (int y=0; y<gridH; y++) {
            for (int x=0; x<gridW; x++) {
                double total_mW = 0.0;
                double bestPrx = -999.0;
                for (Sensor s : config.getSensores()) {
                    RayMetrics m = computeDirectLink(x + 0.5, y + 0.5, s);
                    double mw = Math.pow(10.0, m.getPrxDbm()/10.0);
                    total_mW += mw;
                    if (m.getPrxDbm() > bestPrx) bestPrx = m.getPrxDbm();
                }
                double prxSumDbm = 10.0*Math.log10(Math.max(total_mW, 1e-15));
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

        double prx = s.getTxDbm() + gt + gr
                   - fspl
                   - env.getAlphaDbPerMeter()*d
                   - wallLoss;

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
            double k = t / 0.5;              // 0 → 1
            return new Color(0, k, 1 - k, 1);
        } else {
            double k = (t - 0.5) / 0.5;      // 0 → 1
            return new Color(k, 1 - k, 0, 1);
        }
    }

    // ============================
    //  Edición de paredes (opcional)
    // ============================
    private void enableWallEditing() {
        gridCanvas.setOnMouseClicked(e -> {
            if (!e.isSecondaryButtonDown()) return;
            double gx = toGridXd(e.getX());
            double gy = toGridYd(e.getY());
            Wall w = findWallNear(px(gx), py(gy));
            if (w == null) return;

            ContextMenu cm = new ContextMenu();
            Menu mMat = new Menu("Material");
            MenuItem miCon = new MenuItem("Hormigón");
            miCon.setOnAction(a -> { w.setMaterial(MaterialsDB.CONCRETE); repaintAll(); });
            MenuItem miBri = new MenuItem("Ladrillo");
            miBri.setOnAction(a -> { w.setMaterial(MaterialsDB.BRICK); repaintAll(); });
            MenuItem miDry = new MenuItem("Tabique");
            miDry.setOnAction(a -> { w.setMaterial(MaterialsDB.DRYWALL); repaintAll(); });
            MenuItem miGla = new MenuItem("Cristal");
            miGla.setOnAction(a -> { w.setMaterial(MaterialsDB.GLASS); repaintAll(); });
            MenuItem miMet = new MenuItem("Puerta metálica");
            miMet.setOnAction(a -> { w.setMaterial(MaterialsDB.METAL_DOOR); repaintAll(); });
            mMat.getItems().addAll(miCon, miBri, miDry, miGla, miMet);

            cm.getItems().addAll(mMat);
            cm.show(gridCanvas, e.getScreenX(), e.getScreenY());
        });
    }

    private Wall findWallNear(double px, double py) {
        final double tol = 6.0; // píxeles
        for (Wall w : walls) {
            double d = distPointToSegment(px, py, px(w.getX1()), py(w.getY1()), px(w.getX2()), py(w.getY2()));
            if (d <= tol) return w;
        }
        return null;
    }

    // ============================
    //  Tooltip de sonda (opcional)
    // ============================
    private final Tooltip probeTip = new Tooltip();
    private void enableProbe() {
        gridCanvas.setOnMouseMoved(e -> {
            int gx = (int)((e.getX() - MARGIN)/cellSizePx);
            int gy = (int)((HEIGHT - MARGIN - e.getY())/cellSizePx);
            if (gx<0 || gy<0 || gx>=gridW || gy>=gridH) return;

            double prxBest = -999;
            double prxSumMw = 0;
            for (Sensor s : config.getSensores()) {
                RayMetrics m = computeDirectLink(gx+0.5, gy+0.5, s);
                prxBest = Math.max(prxBest, m.getPrxDbm());
                prxSumMw += Math.pow(10, m.getPrxDbm()/10.0);
            }
            double prxSum = 10*Math.log10(Math.max(prxSumMw,1e-15));
            double noise = env.noiseFloorDbm();
            probeTip.setText(String.format(
                "Cell(%d,%d)\nBest RSSI: %.1f dBm\nSum RSSI: %.1f dBm\nSNR(sum): %.1f dB",
                gx, gy, prxBest, prxSum, prxSum - noise));
            Tooltip.install(gridCanvas, probeTip);
        });
    }

    // ============================
    //  Ventana de parámetros (ajustada a env)
    // ============================
    private void showParametersWindow() {
        Stage paramStage = new Stage();
        paramStage.setTitle("Simulation Parameters");

        StringBuilder sb = new StringBuilder();
        sb.append("📡 Simulation Parameters\n\n");

        if (config.hasHub()) {
            Hub h = config.getHub();
            sb.append("Hub: ").append(h.getId())
              .append(" at (").append(h.getX()).append(",").append(h.getY()).append(")\n")
              .append("Rx Gain: ").append(h.getGrDb()).append(" dB\n\n");
        }

        for (Sensor s : config.getSensores()) {
            sb.append("Sensor ").append(s.getId()).append(" — ").append(s.getNombre()).append("\n");
            sb.append("Pos: (").append(s.getX()).append(",").append(s.getY()).append(")\n");
            sb.append("Tx Power: ").append(s.getTxDbm()).append(" dBm\n");

            if (config.hasHub()) {
                Hub h = config.getHub();
                double dMeters = Math.hypot(h.getX()-s.getX(), h.getY()-s.getY());
                double fspl = Propagation.fsplLossDb(dMeters, env.getFreqMHz());
                double wallLoss = Propagation.wallLossAlongLine(env.getWalls(), env.getFreqMHz(),
                        s.getX(), s.getY(), h.getX(), h.getY());
                double prxDbm = s.getTxDbm() + h.getGrDb() - fspl - env.getAlphaDbPerMeter()*dMeters - wallLoss;
                double noise = env.noiseFloorDbm();
                double snr = prxDbm - noise;
                double bw = env.getBandwidthHz();
                double capacity = bw * (Math.log(1 + Math.pow(10, snr/10.0)) / Math.log(2));

                sb.append("Distance: ").append(String.format("%.2f m", dMeters)).append("\n");
                sb.append("FSPL: ").append(String.format("%.2f dB", fspl)).append("\n");
                sb.append("Walls loss: ").append(String.format("%.2f dB", wallLoss)).append("\n");
                sb.append("Rx: ").append(String.format("%.2f dBm", prxDbm)).append("\n");
                sb.append("Noise: ").append(String.format("%.2f dBm", noise)).append("\n");
                sb.append("SNR: ").append(String.format("%.2f dB", snr)).append("\n");
                sb.append("Capacity (Shannon): ").append(String.format("%.2f Mbps", capacity/1e6)).append("\n\n");
            }
        }

        sb.append("🌍 Global Parameters:\n");
        sb.append("Freq: ").append(env.getFreqMHz()).append(" MHz\n");
        sb.append("BW: ").append(env.getBandwidthHz()).append(" Hz\n");
        sb.append("NF: ").append(env.getNoiseFigureDb()).append(" dB\n");
        sb.append("Noise floor: ").append(String.format("%.2f dBm", env.noiseFloorDbm())).append("\n");

        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
        area.setWrapText(true);
        Scene scene = new Scene(area, 520, 680);
        paramStage.setScene(scene);
        paramStage.show();
    }

    // ============================
    //  Raytracing legacy (sin cambios de materiales; usa config.getObstaculos())
    // ============================
    private void launchRaysForAllSensors(Pane root) {
        root.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));

        final double stepDeg = 10.0; // resolución angular

        for (Sensor s : config.getSensores()) {
            // Si tienes Sensor.emissionAnglesDeg(stepDeg):
            for (double deg : s.emissionAnglesDeg(stepDeg)) {
                double rad = Math.toRadians(deg);
                startRayFrom(root, s, s.getX(), s.getY(),
                             Math.cos(rad), Math.sin(rad),
                             s.getTxDbm(), 0, Color.ORANGE);
            }

            /* --- Alternativa si no tienes emissionAnglesDeg(...) ---
            if (s.isOmni()) {
                for (double deg = 0; deg < 360.0; deg += stepDeg) {
                    double rad = Math.toRadians(deg);
                    startRayFrom(root, s, s.getX(), s.getY(),
                                 Math.cos(rad), Math.sin(rad),
                                 s.getTxDbm(), 0, Color.ORANGE);
                }
            } else {
                double[] r = quadrantToDegRange(s.getDirectiveQuadrant()); // {start,end}
                for (double deg = r[0]; deg < r[1]; deg += stepDeg) {
                    double rad = Math.toRadians(deg);
                    startRayFrom(root, s, s.getX(), s.getY(),
                                 Math.cos(rad), Math.sin(rad),
                                 s.getTxDbm(), 0, Color.ORANGE);
                }
            }
            ---------------------------------------------------------- */
        }
    }



    /** Raytracing simple con rebotes contra paredes (env.getWalls()). */
    /** Raytracing con división de energía en impactos: transmisión + reflexión. */
    private void startRayFrom(Pane root, Sensor s,
                              double startX, double startY,
                              double dirX0, double dirY0,
                              double txEffDbm, int bounces,
                              Color color) {

        if (bounces >= MAX_BOUNCES || txEffDbm <= -120) return;

        final double[] currX = { startX };
        final double[] currY = { startY };
        final double[] dirX  = { dirX0 };
        final double[] dirY  = { dirY0 };
        final double[] traveled = {0.0};

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
                double[] h = segIntersectionD(currX[0], currY[0], nextX, nextY,
                                              w.getX1(), w.getY1(), w.getX2(), w.getY2());
                if (h == null) continue;
                double t = h[2];
                if (t <= 1e-6 || t > 1.0) continue;
                if (t < bestT) { bestT = t; bestHit = h; bestWall = w; }
            }

            if (bestHit != null) {
                // Distancia parcial hasta el impacto
                traveled[0] += bestT * RAY_STEP_METERS;
                double hx = bestHit[0], hy = bestHit[1];

                // Cierra el segmento en el punto de impacto
                seg[0].setEndX(px(hx));
                seg[0].setEndY(py(hy));

                // ===== 1.a) RAMA REFLEJADA =====
                // Calcula dirección reflejada r = d - 2(d·n)n
                double wx = bestWall.getX2() - bestWall.getX1();
                double wy = bestWall.getY2() - bestWall.getY1();
                double wl = Math.hypot(wx, wy);
                if (wl < 1e-12) { tl.stop(); return; }
                double nx = -wy / wl, ny = wx / wl;
                double dot = dirX[0]*nx + dirY[0]*ny;
                double rx = dirX[0] - 2.0 * dot * nx;
                double ry = dirY[0] - 2.0 * dot * ny;
                double rl = Math.hypot(rx, ry);
                rx /= rl; ry /= rl;

                // Potencia tras la reflexión
                double reflLoss = wallReflectionLossDb(bestWall);
                double txReflectedDbm = txEffDbm - reflLoss;

                // Lanza rama reflejada desde el impacto, ligeramente desplazada
                startRayFrom(root, s, hx + rx * EPS, hy + ry * EPS, rx, ry,
                             txReflectedDbm, bounces + 1, color);

                // ===== 1.b) RAMA TRANSMITIDA (ATRAVIESA LA PARED) =====
                // Mismo vector de dirección actual (continúa recto)
                double txTransmittedDbm = txEffDbm - wallTransmissionLossDb(bestWall);
                startRayFrom(root, s, hx + dirX[0] * EPS, hy + dirY[0] * EPS,
                             dirX[0], dirY[0],
                             txTransmittedDbm, bounces + 1, color);

                // Este timeline termina aquí (hemos “spliteado” el rayo)
                tl.stop();
                return;
            }

            // ===== 2) Detección de HUB en el tramo (curr -> next) =====
            if (config.hasHub()) {
                Hub h = config.getHub();
                double segDx = nextX - currX[0], segDy = nextY - currY[0];
                double segLen = Math.hypot(segDx, segDy);
                double denom = segDx*segDx + segDy*segDy;
                double tHub  = (denom <= 1e-12) ? 0.0
                        : ((h.getX() - currX[0]) * segDx + (h.getY() - currY[0]) * segDy) / denom;
                tHub = Math.max(0.0, Math.min(1.0, tHub));
                double closestX = currX[0] + tHub * segDx;
                double closestY = currY[0] + tHub * segDy;
                double distPerp = Math.hypot(h.getX() - closestX, h.getY() - closestY);

                if (distPerp <= HUB_HIT_THRESHOLD) {
                    double dMeters = traveled[0] + tHub * segLen;
                    // Termina visual en el hub
                    seg[0].setEndX(px(h.getX()));
                    seg[0].setEndY(py(h.getY()));

                    // Rx estimado de esta rama (solo FSPL aquí; si quieres,
                    // puedes sumar alpha*d y paredes previas que ya hemos restado en txEffDbm)
                    double lossDb = Propagation.fsplLossDb(dMeters, env.getFreqMHz());
                    double prxDbm = txEffDbm + h.getGrDb() - lossDb;

                    if (hubTooltip != null) {
                        hubTooltip.setText(String.format(
                            "Hub %s (%d,%d)\nSensor: %s\nDist: %.2f m\nFSPL: %.1f dB\nRx(rama): %.1f dBm",
                            h.getId(), h.getX(), h.getY(), s.getNombre(), dMeters, lossDb, prxDbm
                        ));
                        showHubTooltipOverHub();
                    }
                    tl.stop();
                    return;
                }
            }

            // ===== 3) Avance sin impacto =====
            traveled[0] += RAY_STEP_METERS;
            currX[0] = nextX; currY[0] = nextY;
            seg[0].setEndX(px(currX[0])); seg[0].setEndY(py(currY[0]));
            if (traveled[0] >= 50.0) tl.stop();
        });

        tl.getKeyFrames().add(frame);
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }


    /** Ondas circulares que detectan llegada al HUB y muestran métricas como los rayos. */
    private void launchWavefronts(Pane root, Sensor s) {
        // Geometría de referencia
        if (!config.hasHub()) return;
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
                double wallLoss = Propagation.wallLossAlongLine(
                        env.getWalls(), env.getFreqMHz(),
                        s.getX(), s.getY(), h.getX(), h.getY());
                double prxDbm = s.getTxDbm()
                        + h.getGrDb()              // ganancia RX
                        - fspl
                        - env.getAlphaDbPerMeter() * dMeters
                        - wallLoss;

                double prxMw = Math.pow(10.0, prxDbm / 10.0);
                double noise = env.noiseFloorDbm();
                double snr = prxDbm - noise;
                double capacityMbps = env.getBandwidthHz() *
                        (Math.log(1 + Math.pow(10, snr / 10.0)) / Math.log(2)) / 1e6;

                // Tooltip del HUB
                if (hubTooltip != null) {
                    hubTooltip.setText(String.format(
                        "Hub %s (%d,%d)\nSensor: %s\nDist: %.2f m\nFSPL: %.1f dB\nWalls: %.1f dB\n" +
                        "Rx: %.1f dBm (%.3f mW)\nNoise: %.1f dBm\nSNR: %.1f dB\nC(Shannon): %.2f Mbps",
                        h.getId(), h.getX(), h.getY(),
                        s.getNombre(), dMeters, fspl, wallLoss,
                        prxDbm, prxMw, noise, snr, capacityMbps
                    ));
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
        tl.setCycleCount((int)Math.ceil(frames));
        tl.setOnFinished(e -> root.getChildren().remove(wave));
        tl.play();
    }

    
    
    /** Dibuja un heatmap de Rx (o SNR si está activado) celda a celda. */
    private void drawHeatmap() {
        GraphicsContext g = gridCanvas.getGraphicsContext2D();

        // Limpia el heatmap previo
        g.clearRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());

        // Rango de colores (ajústalo si quieres)
        final double MIN_DBM = -100.0; // azul
        final double MAX_DBM =  -30.0; // rojo

        // Si estás mostrando SNR en la UI
        final boolean showSnr = chkShowSnr != null && chkShowSnr.isSelected();
        final double noiseDbm = env.noiseFloorDbm();

        // Recorremos celdas del grid (en unidades "metro" de tu mundo)
        for (int y = 0; y < GRID_MAX_Y; y++) {
            for (int x = 0; x < GRID_MAX_X; x++) {
                // Centro de la celda (en coordenadas de tu grid)
                double cx = x + 0.5;
                double cy = y + 0.5;

                // Suma de contribuciones en dominio lineal (mW)
                double sumMw = 0.0;

                for (Sensor s : config.getSensores()) {
                    double dx = cx - s.getX();
                    double dy = cy - s.getY();
                    double d  = Math.hypot(dx, dy);      // distancia en "m"

                    if (d < 1e-6) d = 1e-3;             // evita log(0)

                    // Pérdida libre
                    double fspl = Propagation.fsplLossDb(d, env.getFreqMHz());

                    // Pérdida por paredes a LO-S (línea recta sensor→celda)
                    double wloss = Propagation.wallLossAlongLine(
                            env.getWalls(), env.getFreqMHz(),
                            s.getX(), s.getY(), cx, cy);

                    // Atenuación lineal del medio
                    double alphaLoss = env.getAlphaDbPerMeter() * d;

                    // Rx con 0 dB de ganancia en el “receptor ficticio”
                    double prxDbm = s.getTxDbm() - fspl - alphaLoss - wloss;

                    // Suma en mW
                    sumMw += Math.pow(10.0, prxDbm / 10.0);
                }

                // Si no hay sensores, pinta transparente
                if (sumMw <= 0) continue;

                double prxTotDbm = 10.0 * Math.log10(sumMw);
                double valueForColor = showSnr ? (prxTotDbm - noiseDbm) : prxTotDbm;

                // Normaliza a [0,1]
                double t;
                if (showSnr) {
                    // Rango típico SNR para colores (ajustable)
                    double minS = 0.0, maxS = 60.0;
                    t = (valueForColor - minS) / (maxS - minS);
                } else {
                    t = (valueForColor - MIN_DBM) / (MAX_DBM - MIN_DBM);
                }
                t = Math.max(0.0, Math.min(1.0, t));

                // Mapea a color (azul→cian→verde→amarillo→rojo)
                Color c = lerpTurbo(t); // o usa lerpJet(t) de abajo si prefieres

                g.setFill(c);

                // --- Pintado respetando márgenes y eje Y invertido ---
                double left   = px(x);        // x → pixels
                double right  = px(x + 1);
                double top    = py(y + 1);    // ¡OJO!: py invierte el eje
                double bottom = py(y);
                double w = right - left;
                double h = bottom - top;

                g.fillRect(left, top, w, h);
            }
        }
    }

    
    

    // ============================
    //  Utilidades geométricas/UI
    // ============================
    private static double px(double gridX) { return MARGIN + gridX * SCALE; }
    private static double py(double gridY) { return HEIGHT - MARGIN - gridY * SCALE; }

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

    private static double toGridXd(double pixelX) { return (pixelX - MARGIN) / (double)SCALE; }
    private static double toGridYd(double pixelY) { return (HEIGHT - MARGIN - pixelY) / (double)SCALE; }

    private static double[] segIntersectionD(double x1, double y1, double x2, double y2,
                                             double x3, double y3, double x4, double y4) {
        double den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(den) < 1e-12) return null;
        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den;
        double u = ((x1 - x3) * (y1 - y2) - (y1 - y3) * (x1 - x2)) / den;
        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null;
        double px = x1 + t * (x2 - x1);
        double py = y1 + t * (y2 - y1);
        return new double[]{ px, py, t };
    }

    private static double distPointToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double vx = x2 - x1, vy = y2 - y1;
        double wx = px - x1, wy = py - y1;
        double c1 = vx*wx + vy*wy;
        if (c1 <= 0) return Math.hypot(px - x1, py - y1);
        double c2 = vx*vx + vy*vy;
        if (c2 <= c1) return Math.hypot(px - x2, py - y2);
        double t = c1 / c2;
        double projx = x1 + t*vx, projy = y1 + t*vy;
        return Math.hypot(px - projx, py - projy);
    }

    private void showHubTooltipOverHub() {
        if (hubBox == null || hubTooltip == null) return;
        Bounds b = hubBox.localToScreen(hubBox.getBoundsInLocal());
        if (b == null) return;
        hubTooltip.show(hubBox, b.getMinX(), b.getMinY() - 24);
        PauseTransition hide = new PauseTransition(Duration.seconds(3));
        hide.setOnFinished(e -> hubTooltip.hide());
        hide.play();
    }

    private Color colorFor(Wall w) {
        String n = (w.getMaterial()!=null ? w.getMaterial().getName() : "");
        if (n.contains("Hormig")) return Color.GRAY;
        if (n.contains("Ladr"))   return Color.SIENNA;
        if (n.contains("Crist"))  return Color.LIGHTBLUE;
        if (n.contains("Tabique"))return Color.BURLYWOOD;
        if (n.contains("Metal"))  return Color.DARKSLATEGRAY;
        if (n.contains("Made"))   return Color.SADDLEBROWN;
        return Color.BLACK;
    }
    
    /** Dibuja un sensor en el pane con su tooltip. */
    private void pintarSensor(Pane root, Sensor s) {
        double cx = px(s.getX());
        double cy = py(s.getY());

        Circle dot = new Circle(cx, cy, 6, Color.DODGERBLUE);
        dot.setStroke(Color.DARKBLUE);

        Label lab = label(s.getNombre(), cx + 10, cy - 10, Font.font(12), Color.DARKBLUE);

        String tipText = String.format("%s\nPos: (%d,%d)\nTx: %.1f dBm\nValor: %.1f",
                s.getNombre(), s.getX(), s.getY(), s.getTxDbm(), s.getValue());
        Tooltip tip = new Tooltip(tipText);
        Tooltip.install(dot, tip);

        root.getChildren().addAll(dot, lab);
        nodos.put(s, new NodeBundle(dot, lab, tip));
    }
    
    
    /** Dibuja el hub en el pane con su tooltip. */
    private void pintarHub(Pane root, Hub h) {
        if (hubNode != null) root.getChildren().remove(hubNode);

        double cx = px(h.getX());
        double cy = py(h.getY());

        hubBox = new Rectangle(cx - 7, cy - 7, 14, 14);
        hubBox.setFill(Color.CRIMSON);
        hubBox.setStroke(Color.DARKRED);
        hubBox.setStrokeWidth(2);

        Label tag = label("HUB", cx + 10, cy - 10, Font.font(12), Color.CRIMSON);

        hubTooltip = new Tooltip(String.format("Hub %s (%d,%d)\nRx: —",
                h.getId(), h.getX(), h.getY()));
        Tooltip.install(hubBox, hubTooltip);

        hubNode = new Group(hubBox, tag);
        root.getChildren().add(hubNode);
    }

    /** Devuelve el rango angular [startDeg, endDeg) del cuadrante en grados. */
    private static double[] quadrantToDegRange(Sensor.Quadrant q) {
        // Usamos convención trigonométrica: 0° hacia +X, 90° hacia +Y, ccw
        return switch (q) {
            case Q1 -> new double[]{ 0.0,  90.0};   // +X,+Y
            case Q2 -> new double[]{90.0, 180.0};   // -X,+Y
            case Q3 -> new double[]{180.0,270.0};   // -X,-Y
            case Q4 -> new double[]{270.0,360.0};   // +X,-Y
        };
    }

    /** Pequeño flash en el HUB cuando una onda/rayo “impacta”. */
    private void flashAtHub(Pane root, Hub h) {
        Circle flash = new Circle(px(h.getX()), py(h.getY()), 6);
        flash.setFill(Color.CRIMSON);
        flash.setOpacity(0.9);
        root.getChildren().add(flash);

        Timeline anim = new Timeline(
            new KeyFrame(Duration.millis(0),
                new javafx.animation.KeyValue(flash.radiusProperty(), 6),
                new javafx.animation.KeyValue(flash.opacityProperty(), 0.9)
            ),
            new KeyFrame(Duration.millis(350),
                new javafx.animation.KeyValue(flash.radiusProperty(), 22),
                new javafx.animation.KeyValue(flash.opacityProperty(), 0.0)
            )
        );
        anim.setOnFinished(e -> root.getChildren().remove(flash));
        anim.play();
    }

    
    /** Pérdida de transmisión (penetración) al atravesar una pared, en dB. */
    private double wallTransmissionLossDb(Wall w) {
        if (w.getMaterial() == null) return 0.0;
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
        return Color.hsb(260*(1-t), 0.95, 0.95); // simple: morado→azul→...→rojo
    }

    // Paleta "jet" clásica (azul→cian→verde→amarillo→rojo)
    private Color lerpJet(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        double r = Math.min(Math.max(1.5 - Math.abs(4*t - 3), 0), 1);
        double g = Math.min(Math.max(1.5 - Math.abs(4*t - 2), 0), 1);
        double b = Math.min(Math.max(1.5 - Math.abs(4*t - 1), 0), 1);
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
            Tooltip.install(l, new Tooltip(
                String.format("%s (%.1f cm) — Loss@%.0fMHz: %.1f dB",
                    w.getMaterial()!=null ? w.getMaterial().getName() : "WALL",
                    w.getThicknessCm(), env.getFreqMHz(), lossDb)));

            wallsLayer.getChildren().add(l);
        }
    }

    

    
    

}
