package UI;

import core.Configuracion;
import core.Hub;
import core.Obstacle;
import core.RayMetrics;
import core.Sensor;
import core.WaveContribution;
import env.Environment;

import javafx.util.Duration;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// =============================================================================
// Sección: Clase Principal
// =============================================================================

/**
 * Aplicación JavaFX para simular un entorno de smart home con trazado de rayos
 * para propagación de señales. Visualiza sensores, hubs, obstáculos y simula
 * el rebote de señales.
 */
public class VisualGridApp extends Application {

    // =============================================================================
    // Sección: Constantes de Dibujo y Parámetros Globales
    // =============================================================================
    
    // Parámetros de la ventana y la cuadrícula
    public static final int WIDTH = 1000;
    public static final int HEIGHT = 700;
    public static final int MARGIN = 60;
    public static final int SCALE = 20;
    public static final int GRID_MAX_X = 40;
    public static final int GRID_MAX_Y = 24;

    // Parámetros de los rayos
    private static final int RAY_STEP_MS = 30;
    private static final int MAX_BOUNCES = 10;
    private static final double EPS = 1e-3;

    // Parámetros para múltiples rayos
 // ===== Estado =====
   
    private static final int RAYS_PER_SENSOR = 72;       // Ej.: 72 rayos (cada 5°)
    private static final double HUB_HIT_THRESHOLD = 0.30; // Radio de “acierto” en celdas
    private static final double RAY_STEP_METERS = 1.0;    // Avance por tick
    RayMetrics metrics = new RayMetrics();
    

    // =============================================================================
    // Sección: Variables de Estado
    // =============================================================================
 // ===== Estado =====
    private boolean anyRayHitThisRun = false;
    private final Configuracion config = new Configuracion();
    private final AtomicInteger seq = new AtomicInteger(1);
    private final Map<Sensor, NodeBundle> nodos = new HashMap<>();
    private final List<WaveContribution> waveField = new ArrayList<>();


    // Lista de métricas de rayos (una por rayo que llegue a Hub o termine)
    private final java.util.List<RayMetrics> rayMetricsList = new java.util.ArrayList<>();


    // Elementos del Hub
    private Group hubNode = null;
    private Rectangle hubBox;
    private Tooltip hubTooltip;
    
 // ===== Parámetros globales de las ondas =====
    private static final double WAVE_INITIAL_RADIUS = 5;       // radio inicial (px)
    private static final double WAVE_MAX_RADIUS = 300;         // radio máximo (px)
    private static final double WAVE_DURATION_SEC = 10.0;       // duración de expansión (s)
    private static final double WAVE_INITIAL_OPACITY = 0.6;    // opacidad inicial
    private static final double WAVE_FINAL_OPACITY = 0.0;      // opacidad final
    private static final double WAVE_INTERVAL_MS = 250;        // tiempo entre ondas nuevas (ms)

    

    // =============================================================================
    // Sección: Enumeraciones
    // =============================================================================
    
    private enum RayType { X_ONLY, Y_ONLY, DIAGONAL }

    // =============================================================================
    // Sección: Método Principal (main) y Lanzamiento de la Aplicación
    // =============================================================================
    
    public static void main(String[] args) { 
        launch(); 
    }

    @Override
    public void start(Stage stage) {
        Pane root = new Pane();
        root.setPrefSize(WIDTH, HEIGHT);

        // Cuadrícula y ejes
        drawAxesAndGrid(root);

        // Sensores iniciales
        Sensor S1 = new Sensor("S1", "Temp salón", 3, 2, 22.5);
        config.addSensor(S1);
        config.getSensores().forEach(s -> pintarSensor(root, s));

        // Hub inicial
        Hub hub = new Hub("H1", "Hub central", 18, 20);
        config.setHub(hub);
        pintarHub(root, hub);

        // Paredes
        config.setObstaculos(Environment.WALLS_TEMPLATE_1);
        drawObstacles(root);

        // === Botón de simulación de rayos ===
        Button btnSimular = new Button("Iniciar Simulación");
        btnSimular.setLayoutX(20);
        btnSimular.setLayoutY(20);
        btnSimular.setOnAction(e -> {
            root.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));
            launchRaysForAllSensors(root);
        });
        root.getChildren().add(btnSimular);

        // === Botón de simulación de ondas ===
        Button btnWaves = new Button("Iniciar Ondas");
        btnWaves.setLayoutX(20);
        btnWaves.setLayoutY(60);
        btnWaves.setOnAction(e -> {
            root.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("wave")));
            for (Sensor s : config.getSensores()) {
                launchWavefronts(root, s);
            }
        });
        root.getChildren().add(btnWaves);

        // === Botón de parámetros ===
        Button btnParams = new Button("Show Parameters");
        btnParams.setLayoutX(20);
        btnParams.setLayoutY(100);
        btnParams.setOnAction(e -> showParametersWindow());
        root.getChildren().add(btnParams);

        // Ventana principal
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        stage.setTitle("Smart Home — Visual Grid con Simulación");
        stage.setScene(scene);
        stage.show();
    }


    // =============================================================================
    // Sección: Métodos de UI y Dibujo
    // =============================================================================
    
    /**
     * Muestra una ventana con parámetros de simulación, incluyendo cálculos
     * como distancia, path loss, SNR, etc.
     */
    private void showParametersWindow() {
        Stage paramStage = new Stage();
        paramStage.setTitle("Simulation Parameters");

        StringBuilder sb = new StringBuilder();
        sb.append("📡 Simulation Parameters\n\n");

        // ==== Información del Hub ====
        if (config.hasHub()) {
            Hub h = config.getHub();

            sb.append("Hub: ").append(h.getId())
              .append(" at (").append(h.getX()).append(",").append(h.getY()).append(")\n")
              .append("Rx Gain: ").append(h.getGrDb()).append(" dB\n\n");
        }

        // ==== Información de los sensores ====
        for (Sensor s : config.getSensores()) {
            sb.append("Sensor ").append(s.getId())
              .append(" — ").append(s.getNombre()).append("\n");
            sb.append("Pos: (").append(s.getX()).append(",").append(s.getY()).append(")\n");
            sb.append("Tx Power: ").append(s.getTxDbm()).append(" dBm\n");
            sb.append("Value: ").append(s.getValue()).append("\n");

            if (config.hasHub()) {
                Hub h = config.getHub();

                // Distancia simple (euclídea)
                double dx = h.getX() - s.getX();
                double dy = h.getY() - s.getY();
                double dMeters = Math.hypot(dx, dy);

                // FSPL
                double fMHz = env.Environment.WIFI_24_GHZ_MHZ;
                double lossDb = core.Propagation.fsplLossDb(dMeters, fMHz);

                // Rx Power
                double prxDbm = core.Propagation.receivedPowerDbm(
                    s.getTxDbm(), 0.0, h.getGrDb(), lossDb);

                // RSSI aproximado
                double rssi = Math.max(-100, Math.min(0, prxDbm));

                // SNR con ruido -90 dBm
                double noiseFloor = -90.0;
                double snr = prxDbm - noiseFloor;

                // Link Margin (Rx - Sensibilidad)
                double sensitivity = -82.0;
                double margin = prxDbm - sensitivity;

                // BER aproximado (BPSK)
                double ber = 0.5 * Math.exp(-snr / 10.0);

                // Channel Capacity (Shannon)
                double bandwidth = 20e6; // 20 MHz típico en WiFi
                double capacity = bandwidth * (Math.log(1 + Math.pow(10, snr / 10)) / Math.log(2));

                // Mostrar resultados clásicos
                sb.append("Distance: ").append(String.format("%.2f m", dMeters)).append("\n");
                sb.append("Path Loss (FSPL): ").append(String.format("%.2f dB", lossDb)).append("\n");
                sb.append("Received Power: ").append(String.format("%.2f dBm", prxDbm)).append("\n");
                sb.append("RSSI: ").append(String.format("%.1f dBm", rssi)).append("\n");
                sb.append("SNR: ").append(String.format("%.1f dB", snr)).append("\n");
                sb.append("Link Margin: ").append(String.format("%.1f dB", margin)).append("\n");
                sb.append("BER (BPSK approx): ").append(String.format("%.2e", ber)).append("\n");
                sb.append("Channel Capacity: ").append(String.format("%.2f Mbps", capacity / 1e6)).append("\n\n");
            }
        }

        // ==== Parámetros de métricas de los rayos ====
        sb.append("\n📊 Ray Metrics (última simulación)\n");
        if (rayMetricsList.isEmpty()) {
            sb.append("No ray metrics recorded yet.\n");
        } else {
            int idx = 1;
            for (RayMetrics m : rayMetricsList) {
                sb.append("Ray #").append(idx++).append("\n");
                sb.append(" - Distance traveled: ").append(String.format("%.2f m", m.getDistanceTraveled())).append("\n");
                sb.append(" - Bounces: ").append(m.getNumBounces()).append("\n");
                sb.append(" - Final Rx Power: ").append(String.format("%.2f dBm", m.getPrxDbm())).append("\n");
                sb.append(" - FSPL: ").append(String.format("%.2f dB", m.getFsplDb())).append("\n");
                sb.append(" - SNR: ").append(String.format("%.2f dB", m.getSnrDb())).append("\n");
                sb.append(" - RSSI: ").append(String.format("%.1f dBm", m.getRssi())).append("\n");
                sb.append(" - Link Margin: ").append(String.format("%.1f dB", m.getLinkMargin())).append("\n");
                sb.append(" - BER: ").append(String.format("%.2e", m.getBer())).append("\n");
                sb.append(" - Channel Capacity: ").append(String.format("%.2f Mbps", m.getChannelCapacityMbps())).append("\n\n");
            }
        }

        // ==== Parámetros globales ====
        sb.append("🌍 Global Parameters:\n");
        sb.append("Frequency: ").append(env.Environment.WIFI_24_GHZ_MHZ).append(" MHz\n");
        sb.append("Max Bounces: ").append(MAX_BOUNCES).append("\n");
        sb.append("Ray Step: ").append(RAY_STEP_MS).append(" ms\n");
        sb.append("Noise Floor: -90 dBm (default)\n");
        sb.append("Receiver Sensitivity: -82 dBm (WiFi typical)\n");

        // ==== Mostrar en ventana ====
        javafx.scene.control.TextArea area = new javafx.scene.control.TextArea(sb.toString());
        area.setEditable(false);
        area.setWrapText(true);

        Scene scene = new Scene(area, 500, 700);
        paramStage.setScene(scene);
        paramStage.show();
    }



    /**
     * Dibuja los ejes y la cuadrícula en el pane principal.
     */
    private void drawAxesAndGrid(Pane root) {
        Font tickFont = Font.font(11);
        Color gridColor = Color.web("#eeeeee");

        for (int x = 0; x <= GRID_MAX_X; x++) {
            double xx = px(x);
            Line v = new Line(xx, py(0), xx, py(GRID_MAX_Y));
            v.setStroke(gridColor);
            root.getChildren().add(v);
            Label lab = label(Integer.toString(x), xx, py(0) + 12, tickFont, Color.GRAY);
            root.getChildren().add(lab);
        }
        for (int y = 0; y <= GRID_MAX_Y; y++) {
            double yy = py(y);
            Line h = new Line(px(0), yy, px(GRID_MAX_X), yy);
            h.setStroke(gridColor);
            root.getChildren().add(h);
            Label lab = label(Integer.toString(y), px(0) - 20, yy - 5, tickFont, Color.GRAY);
            root.getChildren().add(lab);
        }

        root.getChildren().addAll(
                label("X", px(GRID_MAX_X) + 15, py(0) - 10, Font.font(14), Color.BLACK),
                label("Y", px(0) - 20, py(GRID_MAX_Y) + 15, Font.font(14), Color.BLACK)
        );
    }

    /**
     * Dibuja los obstáculos (paredes) en el pane.
     */
    private void drawObstacles(Pane root) {
        for (Obstacle o : config.getObstaculos()) {
            Line wall = new Line(px(o.getX1()), py(o.getY1()), px(o.getX2()), py(o.getY2()));
            wall.setStroke(Color.BLACK);
            wall.setStrokeWidth(3);
            root.getChildren().add(wall);
        }
    }

    /**
     * Dibuja el hub en el pane con su tooltip.
     */
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

    /**
     * Muestra el tooltip sobre el hub por un tiempo limitado.
     */
    private void showHubTooltipOverHub() {
        if (hubBox == null || hubTooltip == null) return;
        Bounds b = hubBox.localToScreen(hubBox.getBoundsInLocal());
        if (b == null) return;
        hubTooltip.show(hubBox, b.getMinX(), b.getMinY() - 24);
        PauseTransition hide = new PauseTransition(Duration.seconds(3));
        hide.setOnFinished(e -> hubTooltip.hide());
        hide.play();
    }

    /**
     * Dibuja un sensor en el pane con su tooltip.
     */
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

    private record NodeBundle(Circle dot, Label label, Tooltip tip) {}

    // =============================================================================
    // Sección: Lógica de Simulación de Rayos
    // =============================================================================
    
    /**
     * Lanza rayos para todos los sensores basados en el tipo de antena.
     */
    private void launchRaysForAllSensors(Pane root) {
        anyRayHitThisRun = false;
        root.getChildren().removeIf(n -> Boolean.TRUE.equals(n.getProperties().get("ray")));

        for (Sensor s : config.getSensores()) {
            if (s.getAntennaType() == Sensor.AntennaType.ISOTROPIC) {
                // 360° cobertura
                for (int deg = 0; deg < 360; deg += 10) {
                    double angleRad = Math.toRadians(deg);
                    startRay(root, s, angleRad, Color.ORANGE);
                }
            } else if (s.getAntennaType() == Sensor.AntennaType.DIRECTIVE) {
                // Solo dentro del cuadrante elegido
                double startDeg = 0, endDeg = 90;
                switch (s.getDirectiveQuadrant()) {
                    case Q1 -> { startDeg = 0;   endDeg = 90;  }   // X+, Y+
                    case Q2 -> { startDeg = 90;  endDeg = 180; }   // X-, Y+
                    case Q3 -> { startDeg = 180; endDeg = 270; }   // X-, Y-
                    case Q4 -> { startDeg = 270; endDeg = 360; }   // X+, Y-
                }

                for (int deg = (int) startDeg; deg < endDeg; deg += 10) {
                    double angleRad = Math.toRadians(deg);
                    startRay(root, s, angleRad, Color.ORANGE);
                }
            }
        }
    }

    /**
     * Inicia un rayo en coordenadas polares con potencia específica.
     */
    private void startRayPolar(Pane root, Sensor s, double angleRad, double txDbmForThisRay, Color color) {
        // Línea inicial
        final Line[] line = { new Line(px(s.getX()), py(s.getY()), px(s.getX()), py(s.getY())) };
        line[0].setStroke(color);
        line[0].setStrokeWidth(1.8);
        line[0].getProperties().put("ray", true);
        root.getChildren().add(line[0]);

        // Estado continuo
        final double[] currX = { s.getX() }, currY = { s.getY() };
        final double[] dirX  = { Math.cos(angleRad) }, dirY = { Math.sin(angleRad) }; // unitario
        final double[] txDbm = { txDbmForThisRay }; // potencia efectiva de este rayo (se penaliza en rebotes)
        final int[]    bounces = {0};
        final double   EPS = 1e-3;

        // Distancia acumulada a lo largo del trazado (para FSPL realista)
        final double[] distAcc = {0.0};

        Timeline tl = new Timeline();
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(RAY_STEP_MS), ev -> {
            // Propuesta de avance
            double nextX = currX[0] + dirX[0] * RAY_STEP_METERS;
            double nextY = currY[0] + dirY[0] * RAY_STEP_METERS;

            // 1) Buscar PRIMER impacto con paredes en el tramo (curr -> next)
            double[] bestHit = null;        // {hx, hy, t}
            Obstacle bestObs = null;
            double   bestT   = Double.POSITIVE_INFINITY;
            final double T_EPS = 1e-6;

            for (Obstacle o : config.getObstaculos()) {
                double[] h = segIntersectionD(currX[0], currY[0], nextX, nextY,
                                              o.getX1(), o.getY1(), o.getX2(), o.getY2());
                if (h == null) continue;
                double t = h[2]; // parámetro del segmento
                if (t <= T_EPS || t > 1.0) continue; // ignora choque en origen/ fuera del tramo
                if (t < bestT) { bestT = t; bestHit = h; bestObs = o; }
            }

            if (bestHit != null) {
                // Distancia recorrida hasta el impacto dentro de este paso
                distAcc[0] += bestT * RAY_STEP_METERS;

                // Cierra tramo en el impacto
                double hx = bestHit[0], hy = bestHit[1];
                line[0].setEndX(px(hx));
                line[0].setEndY(py(hy));

                // Nuevo tramo desde el impacto
                Line nl = new Line(px(hx), py(hy), px(hx), py(hy));
                nl.setStroke(line[0].getStroke());
                nl.setStrokeWidth(line[0].getStrokeWidth());
                nl.getProperties().put("ray", true);
                root.getChildren().add(nl);
                line[0] = nl;

                // Reflexión física: n = (-wy, wx)/|w|
                double wx = bestObs.getX2() - bestObs.getX1();
                double wy = bestObs.getY2() - bestObs.getY1();
                double wl = Math.hypot(wx, wy);
                if (wl < 1e-12) { tl.stop(); return; }
                double nx = -wy / wl, ny = wx / wl;

                double dot = dirX[0]*nx + dirY[0]*ny;
                double rx = dirX[0] - 2.0*dot*nx;
                double ry = dirY[0] - 2.0*dot*ny;
                double rl = Math.hypot(rx, ry);
                rx /= rl; ry /= rl;

                // Empujón para no re-chocar en el mismo punto
                currX[0] = hx + rx * EPS;
                currY[0] = hy + ry * EPS;
                dirX[0]  = rx;
                dirY[0]  = ry;

                // Penalización por rebote
                bounces[0]++;
                txDbm[0] -= 3.0;

                if (bounces[0] >= MAX_BOUNCES) { tl.stop(); return; }

                return; // fin del tick
            }

            // 2) Detección de HUB en el tramo (curr -> next)
            if (config.hasHub()) {
                Hub h = config.getHub();
                double segDx = nextX - currX[0], segDy = nextY - currY[0];
                double denom = segDx*segDx + segDy*segDy;
                double tHub  = (denom <= 1e-12) ? 0.0
                               : ((h.getX() - currX[0]) * segDx + (h.getY() - currY[0]) * segDy) / denom;
                tHub = Math.max(0.0, Math.min(1.0, tHub));
                double closestX = currX[0] + tHub*segDx;
                double closestY = currY[0] + tHub*segDy;
                double distPerp = Math.hypot(h.getX() - closestX, h.getY() - closestY);

                if (distPerp <= HUB_HIT_THRESHOLD) {
                    // Distancia total exacta hasta el hub
                    double segLen = Math.hypot(segDx, segDy);
                    double dMeters = distAcc[0] + tHub * segLen;

                    // Fija punta en el hub
                    line[0].setEndX(px(h.getX()));
                    line[0].setEndY(py(h.getY()));

                    // FSPL / Rx
                    double fMHz   = env.Environment.WIFI_24_GHZ_MHZ;
                    double lossDb = core.Propagation.fsplLossDb(dMeters, fMHz);
                    double prxDbm = core.Propagation.receivedPowerDbm(txDbm[0], 0.0, h.getGrDb(), lossDb);

                    if (hubTooltip != null) {
                        hubTooltip.setText(String.format(
                            "Hub %s (%d,%d)\nSensor: %s\nDist: %.2f m\nFSPL: %.1f dB\nRx: %.1f dBm",
                            h.getId(), h.getX(), h.getY(),
                            s.getNombre(), dMeters, lossDb, prxDbm
                        ));
                        showHubTooltipOverHub();
                    }

                    anyRayHitThisRun = true;
                    tl.stop();
                    return;
                }
            }

            // 3) Avance normal: sumar distancia, actualizar estado y dibujar
            distAcc[0] += RAY_STEP_METERS;
            currX[0] = nextX; currY[0] = nextY;
            line[0].setEndX(px(currX[0]));
            line[0].setEndY(py(currY[0]));
        }));
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }

    /**
     * Inicia un rayo con un ángulo dado y maneja rebotes y detección de hub.
     */
	RayMetrics m = new RayMetrics();
    
	// === startRay con ondas (fase 1: amplitud + fase) + métricas al impactar el HUB ===
	private void startRay(Pane root, Sensor s, double angleRad, Color color) {
	    final double MAX_DISTANCE = 50.0;   // metros/celdas
	    final double[] traveled = {0.0};    // distancia acumulada a lo largo del rayo

	    // Línea inicial
	    final Line[] line = { new Line(px(s.getX()), py(s.getY()), px(s.getX()), py(s.getY())) };
	    line[0].setStroke(color);
	    line[0].setStrokeWidth(2.0);
	    line[0].getProperties().put("ray", true);
	    root.getChildren().add(line[0]);

	    // Estado
	    final double[] currX = { s.getX() };
	    final double[] currY = { s.getY() };

	    final double[] dirX = { Math.cos(angleRad) };
	    final double[] dirY = { Math.sin(angleRad) };

	    final int[] bounces = { 0 };
	    final double STEP = 0.5;    // metros por tick (dirX/dirY son unitarios)
	    final double EPS  = 1e-3;

	    // === Parámetros de onda (para registrar contribuciones, si lo usas) ===
	    final double fMHz   = env.Environment.WIFI_24_GHZ_MHZ;   // MHz
	    final double freqHz = fMHz * 1e6;                        // Hz
	    final double c      = 3e8;                               // m/s
	    final double lambda = c / freqHz;                        // m

	    final double txDbm = s.getTxDbm();
	    final double txMw  = Math.pow(10, txDbm / 10.0);         // mW
	    final double baseAmplitude = Math.sqrt(txMw);            // ~ √P

	    Timeline tl = new Timeline();
	    KeyFrame frame = new KeyFrame(Duration.millis(RAY_STEP_MS), ev -> {
	        // Próxima posición tentativa
	        double nextX = currX[0] + dirX[0] * STEP;
	        double nextY = currY[0] + dirY[0] * STEP;

	        // === (Opcional) registrar contribución de onda en el punto actual ===
	        traveled[0] += STEP; // sumamos el STEP **al inicio del tick**
	        double phase = 2 * Math.PI * (traveled[0] / lambda);
	        double amplitude = baseAmplitude / (1.0 + traveled[0]); // atenuación simple
	        WaveContribution wc = new WaveContribution(currX[0], currY[0], amplitude, phase);
	        waveField.add(wc);

	        // === 1) Colisión con obstáculos: elegir el primer impacto dentro del tramo ===
	        double[] bestHit = null;
	        Obstacle bestObs = null;
	        double bestT = Double.POSITIVE_INFINITY;

	        for (Obstacle o : config.getObstaculos()) {
	            double[] h = segIntersectionD(
	                currX[0], currY[0], nextX, nextY,
	                o.getX1(), o.getY1(), o.getX2(), o.getY2()
	            );
	            if (h == null) continue;

	            double t = h[2];
	            if (t < 1e-6 || t > 1.0) continue; // ignorar origen y fuera del segmento

	            if (t < bestT) { bestT = t; bestHit = h; bestObs = o; }
	        }

	        if (bestHit != null) {
	            // Cierra tramo en el impacto
	            double hx = bestHit[0], hy = bestHit[1];
	            line[0].setEndX(px(hx));
	            line[0].setEndY(py(hy));

	            // Nuevo tramo desde el impacto
	            Line newLine = new Line(px(hx), py(hy), px(hx), py(hy));
	            newLine.setStroke(line[0].getStroke());
	            newLine.setStrokeWidth(line[0].getStrokeWidth());
	            newLine.getProperties().put("ray", true);
	            root.getChildren().add(newLine);
	            line[0] = newLine;

	            // Reflexión (ley del espejo con normal unitaria de la pared)
	            double wx = bestObs.getX2() - bestObs.getX1();
	            double wy = bestObs.getY2() - bestObs.getY1();
	            double wl = Math.hypot(wx, wy);
	            double nx = -wy / wl, ny =  wx / wl;

	            double dot = dirX[0]*nx + dirY[0]*ny;
	            double rx = dirX[0] - 2.0 * dot * nx;
	            double ry = dirY[0] - 2.0 * dot * ny;
	            double rl = Math.hypot(rx, ry);
	            dirX[0] = rx / rl;
	            dirY[0] = ry / rl;

	            // Salir mínimamente de la pared para no “re-chocar”
	            currX[0] = hx + dirX[0] * EPS;
	            currY[0] = hy + dirY[0] * EPS;

	            // Penalización por rebote
	            bounces[0]++;
	            if (bounces[0] >= MAX_BOUNCES) { tl.stop(); return; }
	            return; // fin de tick tras rebote
	        }

	        // === 2) Detección de HUB en el tramo (curr -> next) con tHub y métricas ===
	        if (config.hasHub()) {
	            Hub h = config.getHub();

	            // Vector del tramo actual
	            double segDx = nextX - currX[0];
	            double segDy = nextY - currY[0];
	            double segLen = Math.hypot(segDx, segDy); // ~ STEP

	            // Proyección del HUB sobre el segmento para obtener tHub en [0,1]
	            double denom = segDx*segDx + segDy*segDy;
	            double tHub  = (denom <= 1e-12) ? 0.0
	                           : ((h.getX() - currX[0]) * segDx + (h.getY() - currY[0]) * segDy) / denom;

	            // Clamp numérico
	            if (tHub < 0.0) tHub = 0.0;
	            if (tHub > 1.0) tHub = 1.0;

	            // Distancia perpendicular del HUB al segmento
	            double closestX = currX[0] + tHub * segDx;
	            double closestY = currY[0] + tHub * segDy;
	            double distPerp = Math.hypot(h.getX() - closestX, h.getY() - closestY);

	            final double HUB_HIT_THRESHOLD = 0.30; // en celdas/metros
	            if (distPerp <= HUB_HIT_THRESHOLD) {
	                // Distancia acumulada real hasta el HUB dentro de este tick:
	                // traveled[] ya sumó STEP al principio del tick.
	                // Distancia total = (acumulado previo) + (tHub * segLen)
	                // Acumulado previo = traveled - segLen
	                double dMeters = (traveled[0] - segLen) + tHub * segLen;

	                // Fijar visualmente en el HUB
	                line[0].setEndX(px(h.getX()));
	                line[0].setEndY(py(h.getY()));

	                // Potencia Tx efectiva con penalización por rebotes
	                double txEffDbm = s.getTxDbm() - 3.0 * bounces[0];

	                // FSPL / Rx
	                double lossDb = core.Propagation.fsplLossDb(dMeters, fMHz);
	                double prxDbm = core.Propagation.receivedPowerDbm(txEffDbm, 0.0, h.getGrDb(), lossDb);

	                // ---- Construir y guardar métricas del rayo ----
	                RayMetrics m = new RayMetrics();
	                m.setDistanceTraveled(dMeters);
	                m.setNumBounces(bounces[0]);
	                m.setTxDbm(txEffDbm);
	                m.setFsplDb(lossDb);
	                m.setPrxDbm(prxDbm);

	                double noiseFloor = -90.0;
	                double snr = prxDbm - noiseFloor;
	                m.setSnrDb(snr);

	                double sensitivity = -82.0;
	                m.setLinkMargin(prxDbm - sensitivity);

	                double rssi = Math.max(-100, Math.min(0, prxDbm));
	                m.setRssi(rssi);

	                double ber = 0.5 * Math.exp(-snr / 10.0); // aproximación BPSK
	                m.setBer(ber);

	                double bandwidth = 20e6; // 20 MHz
	                double capacity = bandwidth * (Math.log(1 + Math.pow(10, snr / 10.0)) / Math.log(2));
	                m.setChannelCapacityMbps(capacity / 1e6);

	                rayMetricsList.add(m);

	                // Tooltip del HUB
	                if (hubTooltip != null) {
	                    hubTooltip.setText(String.format(
	                        "Hub %s (%d,%d)\nSensor: %s\nDist: %.2f m\nFSPL: %.1f dB\nRx: %.1f dBm",
	                        h.getId(), h.getX(), h.getY(),
	                        s.getNombre(), dMeters, lossDb, prxDbm
	                    ));
	                    showHubTooltipOverHub();
	                }

	                anyRayHitThisRun = true;
	                tl.stop();
	                return;
	            }
	        }

	        // === 3) Avance normal (sin choque) ===
	        currX[0] = nextX;
	        currY[0] = nextY;

	        line[0].setEndX(px(currX[0]));
	        line[0].setEndY(py(currY[0]));

	        if (traveled[0] >= MAX_DISTANCE) {
	            tl.stop();
	        }
	    });

	    tl.getKeyFrames().add(frame);
	    tl.setCycleCount(Animation.INDEFINITE);
	    tl.play();
	}

    
    // ========= Esta segunda parte del codigo se encarga de las ondas circulares ==================
	/**
	 * Lanza frentes de onda circulares desde un sensor.
	 * Controlado por parámetros configurables.
	 */
	private void launchWavefronts(Pane root, Sensor s) {
	    Timeline timeline = new Timeline(new KeyFrame(Duration.millis(WAVE_INTERVAL_MS), ev -> {
	        Circle wave = new Circle(px(s.getX()), py(s.getY()), WAVE_INITIAL_RADIUS);
	        wave.setStroke(Color.DODGERBLUE);
	        wave.setStrokeWidth(1.5);
	        wave.setFill(Color.TRANSPARENT);

	        // Marca como "onda"
	        wave.getProperties().put("wave", true);
	        root.getChildren().add(wave);

	        // Animación de expansión y desvanecimiento
	        Timeline anim = new Timeline(
	            new KeyFrame(Duration.seconds(0),
	                new javafx.animation.KeyValue(wave.radiusProperty(), WAVE_INITIAL_RADIUS),
	                new javafx.animation.KeyValue(wave.opacityProperty(), WAVE_INITIAL_OPACITY)
	            ),
	            new KeyFrame(Duration.seconds(WAVE_DURATION_SEC),
	                new javafx.animation.KeyValue(wave.radiusProperty(), WAVE_MAX_RADIUS),
	                new javafx.animation.KeyValue(wave.opacityProperty(), WAVE_FINAL_OPACITY)
	            )
	        );
	        anim.setOnFinished(e -> root.getChildren().remove(wave));
	        anim.play();
	    }));
	    timeline.setCycleCount(Animation.INDEFINITE);
	    timeline.play();
	}


    
    

    // =============================================================================
    // Sección: Métodos de Utilidad
    // =============================================================================
    
    /**
     * Convierte coordenada grid X a píxeles.
     */
    private static double px(double gridX) { 
        return MARGIN + gridX * SCALE; 
    }

    /**
     * Convierte coordenada grid Y a píxeles (invertida).
     */
    private static double py(double gridY) { 
        return HEIGHT - MARGIN - gridY * SCALE; 
    }

    /**
     * Crea una etiqueta con texto, posición y estilo.
     */
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

    /**
     * Convierte píxeles X a grid entero.
     */
    private static int toGridX(double pixelX) {
        return (int)Math.round((pixelX - MARGIN) / SCALE);
    }

    /**
     * Convierte píxeles Y a grid entero.
     */
    private static int toGridY(double pixelY) {
        return (int)Math.round((HEIGHT - MARGIN - pixelY) / SCALE);
    }

    /**
     * Calcula punto de intersección entre dos líneas.
     */
    private static double[] intersectionPoint(double x1, double y1, double x2, double y2,
                                              double x3, double y3, double x4, double y4) {
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (denom == 0) return null;
        double px = ((x1*y2 - y1*x2) * (x3 - x4) - (x1 - x2) * (x3*y4 - y3*x4)) / denom;
        double py = ((x1*y2 - y1*x2) * (y3 - y4) - (y1 - y2) * (x3*y4 - y3*x4)) / denom;
        return new double[]{px, py};
    }

    /**
     * Convierte píxeles X a grid double (sin redondeo).
     */
    private static double toGridXd(double pixelX) { 
        return (pixelX - MARGIN) / (double)SCALE; 
    }

    /**
     * Convierte píxeles Y a grid double (sin redondeo).
     */
    private static double toGridYd(double pixelY) { 
        return (HEIGHT - MARGIN - pixelY) / (double)SCALE; 
    }

    /**
     * Calcula intersección de segmentos con parámetro t.
     * Devuelve {px, py, t} o null si no intersectan.
     */
    private static double[] segIntersectionD(double x1, double y1, double x2, double y2,
                                          double x3, double y3, double x4, double y4) {
        double den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(den) < 1e-12) return null;  // paralelos o coincidentes

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den;
        double u = ((x1 - x3) * (y1 - y2) - (y1 - y3) * (x1 - x2)) / den; // ← SIN *-1.0

        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null;

        double px = x1 + t * (x2 - x1);
        double py = y1 + t * (y2 - y1);
        return new double[]{ px, py, t };
    }

    /**
     * Calcula la distancia perpendicular de un punto a un segmento.
     */
    private static double distPointToSegment(double px, double py,
                                             double x1, double y1, double x2, double y2) {
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
    
    private void saveRayMetrics(Sensor s, double endX, double endY, double distance, int bounces, double txDbm) {
        if (!config.hasHub()) return;

        Hub h = config.getHub();
        double fMHz   = env.Environment.WIFI_24_GHZ_MHZ;
        double lossDb = core.Propagation.fsplLossDb(distance, fMHz);
        double prxDbm = core.Propagation.receivedPowerDbm(txDbm, 0.0, h.getGrDb(), lossDb);

        RayMetrics m = new RayMetrics();
        m.setDistanceTraveled(distance);
        m.setNumBounces(bounces);
        m.setTxDbm(txDbm);
        m.setFsplDb(lossDb);
        m.setPrxDbm(prxDbm);

        double noiseFloor = -90.0;
        m.setSnrDb(prxDbm - noiseFloor);

        double sensitivity = -82.0;
        m.setLinkMargin(prxDbm - sensitivity);

        double rssi = Math.max(-100, Math.min(0, prxDbm));
        m.setRssi(rssi);

        double ber = 0.5 * Math.exp(-m.getSnrDb() / 10.0);
        m.setBer(ber);

        double bandwidth = 20e6; // 20 MHz
        double capacity = bandwidth * (Math.log(1 + Math.pow(10, m.getSnrDb() / 10)) / Math.log(2));
        m.setChannelCapacityMbps(capacity / 1e6);

        rayMetricsList.add(m);
    }

    
    
}