package com.mamiyaotaru.voxelmap.persistent;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Rasterizes exact world coordinates at display resolution; it never aggregates or drops chunks. */
final class WorldMapRaster {
    record Image(int[] pixels, int width, int height, WorldMapGeometry.Bounds bounds) {
        long bytes() { return (long) pixels.length * 4; }
    }
    static final class Painter implements AutoCloseable {
        private final BufferedImage image;
        private final Graphics2D graphics;
        private final WorldMapGeometry.Bounds bounds;
        private final double scaleX, scaleZ;
        private final Line2D.Double line = new Line2D.Double();
        private final Rectangle2D.Double rect = new Rectangle2D.Double();
        Painter(WorldMapGeometry.Bounds bounds, int width, int height, int color, float thickness, boolean smooth) {
            this.bounds = bounds;
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            graphics = image.createGraphics();
            scaleX = width / (double) (bounds.maxX() - bounds.minX());
            scaleZ = height / (double) (bounds.maxZ() - bounds.minZ());
            graphics.setColor(new Color(color, true));
            graphics.setStroke(new BasicStroke((float) (thickness * scaleX), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, smooth ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        }
        void rect(float x0, float z0, float x1, float z1) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            rect.setRect((x0 - bounds.minX()) * scaleX, (z0 - bounds.minZ()) * scaleZ, (x1 - x0) * scaleX, (z1 - z0) * scaleZ);
            graphics.fill(rect);
        }
        void outline(float x0, float z0, float x1, float z1) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            rect.setRect((x0 - bounds.minX()) * scaleX, (z0 - bounds.minZ()) * scaleZ, (x1 - x0) * scaleX, (z1 - z0) * scaleZ);
            graphics.draw(rect);
        }
        void trails(ExploredLineMesher.Result mesh, float thickness, boolean nodes) {
            long total = (long) mesh.segmentCount() + mesh.nodeCount();
            WorldMapProgress.report("Drawing lines", 0, total);
            for (int i = 0; i < mesh.segmentCount(); i++) {
                if ((i & 4095) == 0) WorldMapProgress.report("Drawing lines", i, total);
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                int p = i * 4; float[] s = mesh.segments();
                line.setLine((s[p] - bounds.minX()) * scaleX, (s[p + 1] - bounds.minZ()) * scaleZ,
                        (s[p + 2] - bounds.minX()) * scaleX, (s[p + 3] - bounds.minZ()) * scaleZ);
                graphics.draw(line);
            }
            float half = thickness * .55F;
            for (int i = 0; i < mesh.nodeCount(); i++) {
                if ((i & 4095) == 0) WorldMapProgress.report("Drawing lines", (long) mesh.segmentCount() + i, total);
                if (!(nodes || !mesh.nodeLinked()[i])) continue;
                float x = mesh.nodeCoords()[i * 2], z = mesh.nodeCoords()[i * 2 + 1];
                rect(x - half, z - half, x + half, z + half);
            }
        }
        Image finish() {
            int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            WorldMapProgress.report("Preparing image", 0, pixels.length);
            for (int i = 0; i < pixels.length; i++) {
                if ((i & 65535) == 0) WorldMapProgress.report("Preparing image", i, pixels.length);
                int c = pixels[i]; pixels[i] = (c & 0xff00ff00) | ((c & 255) << 16) | ((c >>> 16) & 255);
            }
            return new Image(pixels, image.getWidth(), image.getHeight(), bounds);
        }
        @Override public void close() { graphics.dispose(); }
    }
}
