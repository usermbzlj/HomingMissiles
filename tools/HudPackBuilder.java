import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Reproducibly builds the bitmap-font HMD resource packs without external image tools. */
public final class HudPackBuilder {
    private static final int CELL_WIDTH = 256;
    private static final int CELL_HEIGHT = 128;
    private static final int COLUMNS = 4;
    private static final int ROWS = 8;

    private HudPackBuilder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: java tools/HudPackBuilder.java <output-directory>");
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output);

        BufferedImage atlas = buildAtlas();
        byte[] atlasPng = png(atlas);
        byte[] iconPng = png(buildIcon());
        byte[] fontJson = fontJson().getBytes(StandardCharsets.UTF_8);

        buildPack(output, "HomingMissiles-HUD-1.21.4.zip", packMetadataLegacy(), atlasPng, iconPng, fontJson);
        buildPack(output, "HomingMissiles-HUD-1.21.11.zip", packMetadataModern(), atlasPng, iconPng, fontJson);
        ImageIO.write(buildPreview(atlas), "PNG", output.resolve("HomingMissiles-HUD-preview.png").toFile());
    }

    private static BufferedImage buildAtlas() {
        BufferedImage image = new BufferedImage(
                CELL_WIDTH * COLUMNS, CELL_HEIGHT * ROWS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        for (int active = 1; active <= 4; active++) {
            Graphics2D cell = cell(graphics, active - 1);
            drawShooter(cell, active);
            cell.dispose();
        }
        for (int urgency = 0; urgency < 3; urgency++) {
            for (int direction = 0; direction < 8; direction++) {
                int glyphIndex = 4 + urgency * 8 + direction;
                Graphics2D cell = cell(graphics, glyphIndex);
                drawThreat(cell, direction, urgency);
                cell.dispose();
            }
        }
        graphics.dispose();
        return image;
    }

    private static Graphics2D cell(Graphics2D atlas, int index) {
        int column = index % COLUMNS;
        int row = index / COLUMNS;
        Graphics2D result = (Graphics2D) atlas.create(
                column * CELL_WIDTH, row * CELL_HEIGHT, CELL_WIDTH, CELL_HEIGHT);
        configure(result);
        return result;
    }

    private static void configure(Graphics2D graphics) {
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void drawShooter(Graphics2D graphics, int active) {
        Color bright = new Color(70, 255, 220, 235);
        Color medium = new Color(28, 190, 170, 185);
        Color dim = new Color(18, 102, 98, 135);
        drawFrame(graphics, bright, medium);
        drawReticle(graphics, bright, dim);

        graphics.setColor(dim);
        for (int y = 40; y <= 88; y += 8) {
            int width = y % 16 == 0 ? 9 : 5;
            graphics.fillRect(39, y, width, 2);
            graphics.fillRect(CELL_WIDTH - 39 - width, y, width, 2);
        }

        int startX = 101;
        for (int index = 0; index < 4; index++) {
            int x = startX + index * 14;
            graphics.setColor(index < active ? bright : dim);
            if (index < active) {
                graphics.fillRect(x, 105, 9, 7);
                graphics.setColor(new Color(225, 255, 248, 225));
                graphics.fillRect(x + 2, 106, 5, 2);
            } else {
                graphics.drawRect(x, 105, 8, 6);
            }
        }

        graphics.setColor(medium);
        graphics.fillRect(56, 105, 25, 2);
        graphics.fillRect(175, 105, 25, 2);
        graphics.fillRect(61, 110, 15, 1);
        graphics.fillRect(180, 110, 15, 1);
    }

    private static void drawThreat(Graphics2D graphics, int direction, int urgency) {
        Color bright = switch (urgency) {
            case 0 -> new Color(255, 184, 48, 205);
            case 1 -> new Color(255, 92, 34, 225);
            default -> new Color(255, 34, 38, 245);
        };
        Color medium = new Color(bright.getRed(), bright.getGreen(), bright.getBlue(), 165);
        Color dim = new Color(bright.getRed(), bright.getGreen(), bright.getBlue(), 95);
        drawFrame(graphics, bright, dim);

        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(dim);
        int radius = 20 + urgency * 7;
        graphics.drawRect(128 - radius, 64 - radius, radius * 2, radius * 2);
        if (urgency >= 1) {
            graphics.drawRect(128 - radius - 5, 64 - radius - 5, (radius + 5) * 2, (radius + 5) * 2);
        }

        graphics.setColor(bright);
        Polygon diamond = new Polygon(
                new int[]{128, 138, 128, 118},
                new int[]{51, 64, 77, 64}, 4);
        graphics.drawPolygon(diamond);
        graphics.fillRect(126, 57, 4, 10);
        graphics.fillRect(126, 71, 4, 4);

        double angle = Math.toRadians(-90.0 - direction * 45.0);
        int outerX = 128 + (int) Math.round(Math.cos(angle) * 91.0);
        int outerY = 64 + (int) Math.round(Math.sin(angle) * 44.0);
        int innerX = 128 + (int) Math.round(Math.cos(angle) * 68.0);
        int innerY = 64 + (int) Math.round(Math.sin(angle) * 33.0);
        drawInwardChevron(graphics, outerX, outerY, innerX, innerY, bright, 3 + urgency);

        graphics.setColor(medium);
        for (int offset = -1; offset <= 1; offset += 2) {
            int x = 128 + offset * (43 + urgency * 4);
            graphics.fillRect(x, 61, 2, 7);
        }
    }

    private static void drawFrame(Graphics2D graphics, Color bright, Color secondary) {
        graphics.setColor(secondary);
        graphics.fillRect(21, 18, 42, 2);
        graphics.fillRect(193, 18, 42, 2);
        graphics.fillRect(21, 108, 42, 2);
        graphics.fillRect(193, 108, 42, 2);
        graphics.fillRect(18, 21, 2, 29);
        graphics.fillRect(18, 78, 2, 29);
        graphics.fillRect(236, 21, 2, 29);
        graphics.fillRect(236, 78, 2, 29);

        graphics.setColor(bright);
        corner(graphics, 27, 27, 1, 1);
        corner(graphics, 229, 27, -1, 1);
        corner(graphics, 27, 101, 1, -1);
        corner(graphics, 229, 101, -1, -1);
    }

    private static void corner(Graphics2D graphics, int x, int y, int horizontal, int vertical) {
        int horizontalX = horizontal > 0 ? x : x - 17;
        int verticalY = vertical > 0 ? y : y - 17;
        graphics.fillRect(horizontalX, y, 18, 3);
        graphics.fillRect(x, verticalY, 3, 18);
    }

    private static void drawReticle(Graphics2D graphics, Color bright, Color dim) {
        graphics.setColor(dim);
        graphics.drawRect(115, 51, 26, 26);
        graphics.setColor(bright);
        graphics.fillRect(126, 43, 4, 11);
        graphics.fillRect(126, 74, 4, 11);
        graphics.fillRect(106, 62, 12, 4);
        graphics.fillRect(138, 62, 12, 4);
        graphics.drawRect(122, 58, 12, 12);
        graphics.fillRect(127, 63, 2, 2);
        graphics.fillRect(62, 63, 38, 2);
        graphics.fillRect(156, 63, 38, 2);
        graphics.fillRect(76, 59, 2, 10);
        graphics.fillRect(178, 59, 2, 10);
    }

    private static void drawInwardChevron(Graphics2D graphics,
                                           int outerX, int outerY,
                                           int innerX, int innerY,
                                           Color color, int thickness) {
        double dx = innerX - outerX;
        double dy = innerY - outerY;
        double length = Math.max(1.0, Math.hypot(dx, dy));
        double perpendicularX = -dy / length;
        double perpendicularY = dx / length;
        int wing = 9 + thickness;
        int wingX1 = outerX + (int) Math.round(perpendicularX * wing);
        int wingY1 = outerY + (int) Math.round(perpendicularY * wing);
        int wingX2 = outerX - (int) Math.round(perpendicularX * wing);
        int wingY2 = outerY - (int) Math.round(perpendicularY * wing);
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(thickness, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.drawLine(wingX1, wingY1, innerX, innerY);
        graphics.drawLine(wingX2, wingY2, innerX, innerY);
        graphics.drawLine(outerX, outerY, innerX, innerY);
    }

    private static BufferedImage buildIcon() {
        BufferedImage icon = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        configure(graphics);
        graphics.setColor(new Color(5, 15, 22, 255));
        graphics.fillRect(0, 0, 128, 128);
        graphics.scale(0.5, 0.5);
        drawShooter(graphics, 4);
        graphics.dispose();
        return icon;
    }

    private static BufferedImage buildPreview(BufferedImage atlas) {
        BufferedImage preview = new BufferedImage(CELL_WIDTH * 2, CELL_HEIGHT * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();
        configure(graphics);
        graphics.setColor(new Color(7, 12, 18));
        graphics.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        copyCell(graphics, atlas, 3, 0, 0);
        copyCell(graphics, atlas, 4, CELL_WIDTH, 0);
        copyCell(graphics, atlas, 12, 0, CELL_HEIGHT);
        copyCell(graphics, atlas, 20, CELL_WIDTH, CELL_HEIGHT);
        graphics.dispose();
        return preview;
    }

    private static void copyCell(Graphics2D graphics, BufferedImage atlas, int index, int x, int y) {
        int sourceX = (index % COLUMNS) * CELL_WIDTH;
        int sourceY = (index / COLUMNS) * CELL_HEIGHT;
        graphics.drawImage(atlas,
                x, y, x + CELL_WIDTH, y + CELL_HEIGHT,
                sourceX, sourceY, sourceX + CELL_WIDTH, sourceY + CELL_HEIGHT,
                null);
    }

    private static String fontJson() {
        StringBuilder rows = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            if (row > 0) {
                rows.append(',');
            }
            rows.append('"');
            for (int column = 0; column < COLUMNS; column++) {
                rows.append(String.format("\\u%04x", 0xE100 + row * COLUMNS + column));
            }
            rows.append('"');
        }
        return "{\"providers\":[{\"type\":\"bitmap\","
                + "\"file\":\"homingmissiles:font/homingmissiles_hmd.png\","
                + "\"ascent\":72,\"height\":96,\"chars\":[" + rows + "]}]}\n";
    }

    private static String packMetadataLegacy() {
        return "{\"pack\":{\"pack_format\":46,\"supported_formats\":[46,46],"
                + "\"description\":\"HomingMissiles 3.0 Pixel HMD - Minecraft 1.21.4\"}}\n";
    }

    private static String packMetadataModern() {
        return "{\"pack\":{\"min_format\":75,\"max_format\":75,"
                + "\"description\":\"HomingMissiles 3.0 Pixel HMD - Minecraft 1.21.11\"}}\n";
    }

    private static void buildPack(Path output, String name, String metadata,
                                  byte[] atlas, byte[] icon, byte[] font) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("assets/homingmissiles/font/hud.json", font);
        entries.put("assets/homingmissiles/textures/font/homingmissiles_hmd.png", atlas);
        entries.put("pack.mcmeta", metadata.getBytes(StandardCharsets.UTF_8));
        entries.put("pack.png", icon);

        Path zipPath = output.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        String sha1 = digest("SHA-1", Files.readAllBytes(zipPath));
        Files.writeString(output.resolve(name + ".sha1"), sha1 + "  " + name + "\n", StandardCharsets.US_ASCII);
        System.out.println(name + " SHA-1=" + sha1);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", output)) {
            throw new IOException("PNG writer unavailable");
        }
        return output.toByteArray();
    }

    private static String digest(String algorithm, byte[] data) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
    }
}
