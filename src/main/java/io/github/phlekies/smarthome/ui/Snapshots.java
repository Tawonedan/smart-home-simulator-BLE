package io.github.phlekies.smarthome.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

/** Renders nodes to PNG bytes. */
final class Snapshots {

    private static final double OVERLAY_MARGIN = 24;

    private Snapshots() {
    }

    static byte[] png(Node node) throws IOException {
        return encode(node.snapshot(new SnapshotParameters(), null));
    }

    /**
     * The plan at its natural size, with an optional overlay (the legend) drawn in its top-right
     * corner, as it appears on screen.
     */
    static byte[] planWithOverlay(Node plan, Node overlay) throws IOException {
        WritableImage base = plan.snapshot(new SnapshotParameters(), null);
        if (overlay == null || !overlay.isVisible()) {
            return encode(base);
        }
        SnapshotParameters transparent = new SnapshotParameters();
        transparent.setFill(javafx.scene.paint.Color.TRANSPARENT);
        Image layer = overlay.snapshot(transparent, null);

        Canvas canvas = new Canvas(base.getWidth(), base.getHeight());
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.drawImage(base, 0, 0);
        g.drawImage(layer, base.getWidth() - layer.getWidth() - OVERLAY_MARGIN, OVERLAY_MARGIN);
        return encode(canvas.snapshot(new SnapshotParameters(), null));
    }

    private static byte[] encode(Image image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(buffered, "png", bytes);
        return bytes.toByteArray();
    }
}
