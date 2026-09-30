package io.github.phlekies.smarthome.app;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import io.github.phlekies.smarthome.model.Environment;
import io.github.phlekies.smarthome.model.Hub;
import io.github.phlekies.smarthome.model.Sensor;
import io.github.phlekies.smarthome.simulation.CellResult;
import io.github.phlekies.smarthome.simulation.CoverageStats;
import io.github.phlekies.smarthome.simulation.SimulationSettings;
import io.github.phlekies.smarthome.util.Format;

/** Self-contained HTML report of the current project (styles and plan image embedded). */
public final class CoverageReport {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US);

    private CoverageReport() {
    }

    /**
     * @param coverage coverage of the last heatmap, or {@code null} if none was computed
     * @param planPng  PNG image of the plan, or {@code null} to omit it
     */
    public static String html(SimulatorModel model, CoverageStats coverage, byte[] planPng, LocalDateTime generatedAt) {
        Environment env = model.environment();
        SimulationSettings settings = model.settings();
        StringBuilder html = new StringBuilder(16_384);

        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Coverage report</title>
                <style>
                  body { font-family: "Segoe UI", system-ui, sans-serif; margin: 0; background: #0f172a; color: #e2e8f0; }
                  main { max-width: 1100px; margin: 0 auto; padding: 32px 24px 48px; }
                  h1 { margin: 0 0 4px; font-size: 28px; }
                  h2 { margin: 36px 0 12px; font-size: 18px; color: #93c5fd; }
                  .muted { color: #94a3b8; }
                  .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; }
                  .kpi { background: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 14px 16px; }
                  .kpi b { display: block; font-size: 24px; margin-top: 4px; color: #f8fafc; }
                  img { width: 100%; border-radius: 12px; border: 1px solid #334155; }
                  table { width: 100%; border-collapse: collapse; background: #1e293b; border-radius: 12px; overflow: hidden; }
                  th, td { text-align: left; padding: 9px 12px; border-bottom: 1px solid #334155; }
                  th { color: #94a3b8; font-weight: 600; font-size: 13px; }
                  .ok { color: #4ade80; font-weight: 600; } .bad { color: #f87171; font-weight: 600; }
                  footer { margin-top: 40px; font-size: 13px; }
                </style>
                </head>
                <body><main>
                """);

        html.append("<h1>Wi-Fi coverage report</h1>\n");
        html.append("<p class=\"muted\">").append(escape(model.scenarioName())).append(" &middot; ")
                .append(escape(model.scenarioDescription())).append("<br>Generated on ")
                .append(DATE.format(generatedAt)).append("</p>\n");

        html.append("<h2>Key indicators</h2>\n<div class=\"kpis\">\n");
        if (coverage != null && !coverage.isEmpty()) {
            kpi(html, "Area with usable signal", String.format(Locale.US, "%.0f %%", coverage.coveredFraction() * 100));
            kpi(html, "Median signal", Format.dbm(coverage.medianSignalDbm()));
            kpi(html, "Signal in 90 % of the area", "≥ " + Format.dbm(coverage.signalP10Dbm()));
            kpi(html, "Median SINR", Format.db(coverage.medianSinrDb()));
        }
        kpi(html, "Sensors", Integer.toString(model.sensors().size()));
        kpi(html, "Walls", Integer.toString(model.walls().size()));
        kpi(html, "Band", Format.frequencyMHz(env.getFreqMHz()));
        kpi(html, "Noise floor", Format.dbm(env.noiseFloorDbm()));
        html.append("</div>\n");

        if (planPng != null) {
            html.append("<h2>Floor plan</h2>\n<img alt=\"Floor plan with coverage heatmap\" src=\"data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(planPng)).append("\">\n");
        }

        html.append("<h2>Sensor links to the hub</h2>\n");
        Optional<Hub> hub = model.hub();
        if (hub.isEmpty()) {
            html.append("<p class=\"muted\">No hub has been placed.</p>\n");
        } else {
            html.append("<p class=\"muted\">Hub at (").append(hub.get().getX()).append(", ").append(hub.get().getY())
                    .append(") m. Receiver sensitivity ").append(Format.dbm(settings.getReceiverSensitivityDbm()))
                    .append(".</p>\n");
            html.append("<table>\n<tr><th>Sensor</th><th>Position</th><th>Antenna</th><th>Distance</th>"
                    + "<th>Received power</th><th>SNR</th><th>Link margin</th><th>Status</th></tr>\n");
            for (Sensor sensor : model.sensors()) {
                CellResult link = model.sensorLink(sensor).orElseThrow();
                boolean reached = link.hasEnergy();
                boolean ok = reached && link.linkMarginDb() >= 0;
                html.append("<tr><td>").append(escape(sensor.getName()))
                        .append("</td><td>(").append(sensor.getX()).append(", ").append(sensor.getY())
                        .append(")</td><td>").append(sensor.getAntennaType())
                        .append("</td><td>").append(Format.meters(sensor.distanceTo(hub.get())))
                        .append("</td><td>").append(reached ? Format.dbm(link.totalPowerDbm()) : "no signal")
                        .append("</td><td>").append(reached ? Format.db(link.snrDb()) : "-")
                        .append("</td><td>").append(reached ? Format.db(link.linkMarginDb()) : "-")
                        .append("</td><td class=\"").append(ok ? "ok\">Connected" : "bad\">Out of range")
                        .append("</td></tr>\n");
            }
            html.append("</table>\n");
        }

        html.append("<h2>Simulation settings</h2>\n<table>\n");
        row(html, "Propagation", settings.getPropagationMode().toString());
        row(html, "Fading", settings.getFadingModel().toString());
        row(html, "Path-loss exponent", Format.number(settings.getLogDistanceExponent(), 2));
        row(html, "Diffraction / scattering", (settings.isDiffractionEnabled() ? "on" : "off") + " / "
                + (settings.isScatteringEnabled() ? "on" : "off"));
        row(html, "Bandwidth", Format.hertz(env.getBandwidthHz()));
        row(html, "Noise figure", Format.db(env.getNoiseFigureDb()));
        html.append("</table>\n");

        html.append("<footer class=\"muted\">Generated by Smart Home Simulator.</footer>\n</main></body></html>\n");
        return html.toString();
    }

    private static void kpi(StringBuilder html, String label, String value) {
        html.append("<div class=\"kpi\"><span class=\"muted\">").append(escape(label)).append("</span><b>")
                .append(escape(value)).append("</b></div>\n");
    }

    private static void row(StringBuilder html, String label, String value) {
        html.append("<tr><th>").append(escape(label)).append("</th><td>").append(escape(value)).append("</td></tr>\n");
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
