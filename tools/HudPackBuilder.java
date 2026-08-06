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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Reproducibly builds the layered bitmap-font HMD resource packs. */
public final class HudPackBuilder {
    private static final int CELL_WIDTH = 256;
    private static final int CELL_HEIGHT = 128;
    private static final int COLUMNS = 8;
    private static final int GLYPH_COUNT = 535;
    private static final int ROWS = (GLYPH_COUNT + COLUMNS - 1) / COLUMNS;

    private static final int BASE_INDEX = 0;
    private static final int WEAPON_INDEX = 1;
    private static final int THREAT_INDEX = 6;
    private static final int PITCH_INDEX = 30;
    private static final int HEADING_INDEX = 55;
    private static final int SPEED_INDEX = 127;
    private static final int ALTITUDE_INDEX = 208;
    private static final int HEIGHT_INDEX = 305;
    private static final int PROGRADE_INDEX = 370;
    private static final int LOCK_MARKER_INDEX = 419;
    private static final int LOCK_PROGRESS_INDEX = 517;

    private static final Color HUD = new Color(72, 255, 105, 238);
    private static final Color HUD_MEDIUM = new Color(50, 218, 88, 185);
    private static final Color HUD_DIM = new Color(35, 142, 70, 125);
    private static final Map<Character, String> PIXEL_FONT = Map.ofEntries(
            Map.entry('0', "111101101101111"), Map.entry('1', "010110010010111"),
            Map.entry('2', "111001111100111"), Map.entry('3', "111001111001111"),
            Map.entry('4', "101101111001001"), Map.entry('5', "111100111001111"),
            Map.entry('6', "111100111101111"), Map.entry('7', "111001010010010"),
            Map.entry('8', "111101111101111"), Map.entry('9', "111101111001111"),
            Map.entry('A', "010101111101101"), Map.entry('B', "110101110101110"),
            Map.entry('C', "111100100100111"), Map.entry('D', "110101101101110"),
            Map.entry('E', "111100110100111"), Map.entry('F', "111100110100100"),
            Map.entry('G', "111100101101111"), Map.entry('H', "101101111101101"),
            Map.entry('I', "111010010010111"), Map.entry('J', "001001001101111"),
            Map.entry('K', "101101110101101"), Map.entry('L', "100100100100111"),
            Map.entry('M', "101111111101101"), Map.entry('N', "101111111111101"),
            Map.entry('O', "111101101101111"), Map.entry('P', "111101111100100"),
            Map.entry('Q', "111101101111001"), Map.entry('R', "110101110101101"),
            Map.entry('S', "111100111001111"), Map.entry('T', "111010010010010"),
            Map.entry('U', "101101101101111"), Map.entry('V', "101101101101010"),
            Map.entry('W', "101101111111101"), Map.entry('X', "101101010101101"),
            Map.entry('Y', "101101010010010"), Map.entry('Z', "111001010100111"),
            Map.entry('+', "000010111010000"), Map.entry('-', "000000111000000"),
            Map.entry('/', "001001010100100"), Map.entry('.', "000000000000010")
    );

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
        byte[] iconPng = png(buildIcon(atlas));
        byte[] fontJson = fontJson(72, 96, -193).getBytes(StandardCharsets.UTF_8);
        byte[] titleFontJson = fontJson(9, 24, -49).getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> audio = loadAudio();

        buildPack(output, "HomingMissiles-HUD-1.21.4.zip", packMetadataLegacy(),
                atlasPng, iconPng, fontJson, titleFontJson, audio);
        buildPack(output, "HomingMissiles-HUD-1.21.11.zip", packMetadataModern(),
                atlasPng, iconPng, fontJson, titleFontJson, audio);
        ImageIO.write(buildPreview(atlas), "PNG", output.resolve("HomingMissiles-HUD-preview.png").toFile());
    }

    private static BufferedImage buildAtlas() {
        BufferedImage image = new BufferedImage(
                CELL_WIDTH * COLUMNS, CELL_HEIGHT * ROWS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);

        paint(graphics, BASE_INDEX, HudPackBuilder::drawBase);
        for (int active = 0; active <= 4; active++) {
            int value = active;
            paint(graphics, WEAPON_INDEX + active, cell -> drawWeapon(cell, value));
        }
        for (int urgency = 0; urgency < 3; urgency++) {
            for (int direction = 0; direction < 8; direction++) {
                int urgencyValue = urgency;
                int directionValue = direction;
                paint(graphics, THREAT_INDEX + urgency * 8 + direction,
                        cell -> drawThreat(cell, directionValue, urgencyValue));
            }
        }
        for (int bucket = 0; bucket < 25; bucket++) {
            int pitch = -60 + bucket * 5;
            paint(graphics, PITCH_INDEX + bucket, cell -> drawPitch(cell, pitch));
        }
        for (int bucket = 0; bucket < 72; bucket++) {
            int heading = bucket * 5;
            paint(graphics, HEADING_INDEX + bucket, cell -> drawHeading(cell, heading));
        }
        for (int bucket = 0; bucket < 81; bucket++) {
            int speed = bucket * 2;
            paint(graphics, SPEED_INDEX + bucket, cell -> drawSpeed(cell, speed));
        }
        for (int bucket = 0; bucket < 97; bucket++) {
            int altitude = -64 + bucket * 4;
            paint(graphics, ALTITUDE_INDEX + bucket, cell -> drawAltitude(cell, altitude));
        }
        for (int bucket = 0; bucket < 65; bucket++) {
            int height = bucket * 4;
            paint(graphics, HEIGHT_INDEX + bucket, cell -> drawHeight(cell, height));
        }
        for (int bucket = 0; bucket < 49; bucket++) {
            int value = bucket;
            paint(graphics, PROGRADE_INDEX + bucket, cell -> drawPrograde(cell, value));
        }
        for (int locked = 0; locked < 2; locked++) {
            for (int position = 0; position < 49; position++) {
                int positionValue = position;
                boolean lockedValue = locked == 1;
                paint(graphics, LOCK_MARKER_INDEX + locked * 49 + position,
                        cell -> drawLockMarker(cell, positionValue, lockedValue));
            }
        }
        for (int progress = 0; progress <= 17; progress++) {
            int value = progress;
            paint(graphics, LOCK_PROGRESS_INDEX + progress,
                    cell -> drawLockProgress(cell, value));
        }

        graphics.dispose();
        return image;
    }

    private static void paint(Graphics2D atlas, int index, Painter painter) {
        Graphics2D cell = cell(atlas, index);
        painter.paint(cell);
        cell.setColor(new Color(0, 0, 0, 1));
        cell.fillRect(CELL_WIDTH - 1, CELL_HEIGHT / 2, 1, 1);
        cell.dispose();
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

    private static void drawBase(Graphics2D graphics) {
        graphics.setColor(HUD_DIM);
        graphics.fillRect(26, 16, 45, 2);
        graphics.fillRect(185, 16, 45, 2);
        graphics.fillRect(19, 31, 2, 67);
        graphics.fillRect(235, 31, 2, 67);
        graphics.fillRect(24, 105, 42, 2);
        graphics.fillRect(190, 105, 42, 2);

        graphics.setColor(HUD_MEDIUM);
        corner(graphics, 27, 25, 1, 1);
        corner(graphics, 229, 25, -1, 1);
        corner(graphics, 27, 101, 1, -1);
        corner(graphics, 229, 101, -1, -1);

        graphics.setColor(HUD);
        graphics.fillRect(122, 17, 12, 2);
        graphics.fillRect(127, 14, 2, 8);
        graphics.fillRect(106, 63, 13, 2);
        graphics.fillRect(137, 63, 13, 2);
        graphics.fillRect(127, 55, 2, 7);
        graphics.fillRect(127, 66, 2, 7);
        graphics.drawRect(122, 59, 12, 10);

        drawText(graphics, "HDG", 108, 5, 1, HUD_MEDIUM, Align.LEFT);
        drawText(graphics, "SPD", 3, 31, 1, HUD_MEDIUM, Align.LEFT);
        drawText(graphics, "ALT", 241, 31, 1, HUD_MEDIUM, Align.RIGHT);
        drawText(graphics, "HMD", 128, 115, 1, HUD_DIM, Align.CENTER);
    }

    private static void drawWeapon(Graphics2D graphics, int active) {
        drawText(graphics, "MSL " + active + "/4", 30, 112, 1, HUD, Align.LEFT);
        for (int index = 0; index < 4; index++) {
            int x = 68 + index * 11;
            graphics.setColor(index < active ? HUD : HUD_DIM);
            if (index < active) {
                graphics.fillRect(x, 111, 7, 5);
                graphics.setColor(new Color(220, 255, 226, 230));
                graphics.fillRect(x + 2, 112, 3, 1);
            } else {
                graphics.drawRect(x, 111, 6, 4);
            }
        }
    }

    private static void drawThreat(Graphics2D graphics, int direction, int urgency) {
        Color bright = switch (urgency) {
            case 0 -> new Color(255, 193, 52, 225);
            case 1 -> new Color(255, 102, 35, 238);
            default -> new Color(255, 39, 42, 252);
        };
        Color dim = new Color(bright.getRed(), bright.getGreen(), bright.getBlue(), 125);
        drawText(graphics, urgency == 2 ? "BREAK" : "MISSILE", 128, 29, 2, bright, Align.CENTER);

        graphics.setStroke(new BasicStroke(2.0f + urgency));
        graphics.setColor(dim);
        int radius = 19 + urgency * 5;
        graphics.drawRect(128 - radius, 64 - radius, radius * 2, radius * 2);
        graphics.setColor(bright);
        graphics.drawOval(119, 55, 18, 18);
        graphics.fillRect(127, 51, 2, 7);
        graphics.fillRect(127, 71, 2, 7);
        graphics.fillRect(115, 63, 7, 2);
        graphics.fillRect(134, 63, 7, 2);

        double angle = Math.toRadians(-90.0 - direction * 45.0);
        int outerX = 128 + (int) Math.round(Math.cos(angle) * 91.0);
        int outerY = 64 + (int) Math.round(Math.sin(angle) * 44.0);
        int innerX = 128 + (int) Math.round(Math.cos(angle) * 67.0);
        int innerY = 64 + (int) Math.round(Math.sin(angle) * 32.0);
        drawInwardChevron(graphics, outerX, outerY, innerX, innerY, bright, 3 + urgency);
    }

    private static void drawPitch(Graphics2D graphics, int minecraftPitch) {
        int aircraftPitch = -minecraftPitch;
        for (int mark = -60; mark <= 60; mark += 10) {
            int y = 64 + (int) Math.round((aircraftPitch - mark) * 0.65);
            if (y < 34 || y > 96) {
                continue;
            }
            boolean horizon = mark == 0;
            int halfWidth = horizon ? 43 : 16 + Math.abs(mark) / 5;
            graphics.setColor(horizon ? HUD : HUD_MEDIUM);
            if (mark < 0 && !horizon) {
                for (int x = 128 - halfWidth; x < 128 + halfWidth; x += 7) {
                    graphics.fillRect(x, y, 4, 1);
                }
            } else {
                graphics.fillRect(128 - halfWidth, y, halfWidth - 7, horizon ? 2 : 1);
                graphics.fillRect(135, y, halfWidth - 7, horizon ? 2 : 1);
            }
            if (!horizon) {
                String label = Integer.toString(Math.abs(mark));
                drawText(graphics, label, 125 - halfWidth, y - 2, 1, HUD_MEDIUM, Align.RIGHT);
                drawText(graphics, label, 131 + halfWidth, y - 2, 1, HUD_MEDIUM, Align.LEFT);
            }
        }
        drawText(graphics, "P" + signed(aircraftPitch), 128, 101, 1, HUD_MEDIUM, Align.CENTER);
    }

    private static void drawHeading(Graphics2D graphics, int heading) {
        for (int offset = -4; offset <= 4; offset++) {
            int value = Math.floorMod(heading + offset * 15, 360);
            int x = 128 + offset * 20;
            graphics.setColor(offset == 0 ? HUD : HUD_MEDIUM);
            graphics.fillRect(x, 20, offset % 2 == 0 ? 2 : 1, offset % 2 == 0 ? 6 : 4);
            if (offset % 2 == 0 && offset != 0) {
                drawText(graphics, headingLabel(value), x, 10, 1, HUD_MEDIUM, Align.CENTER);
            }
        }
        graphics.setColor(HUD);
        graphics.drawRect(114, 4, 28, 10);
        drawText(graphics, threeDigits(heading), 128, 7, 1, HUD, Align.CENTER);
    }

    private static void drawSpeed(Graphics2D graphics, int speed) {
        graphics.setColor(HUD);
        graphics.drawRect(18, 56, 36, 15);
        drawText(graphics, threeDigits(speed), 36, 61, 2, HUD, Align.CENTER);
        for (int offset = -2; offset <= 2; offset++) {
            if (offset == 0) {
                continue;
            }
            int value = Math.max(0, speed - offset * 10);
            int y = 63 + offset * 16;
            graphics.setColor(HUD_MEDIUM);
            graphics.fillRect(19, y, 8, 1);
            drawText(graphics, threeDigits(value), 31, y - 2, 1, HUD_MEDIUM, Align.LEFT);
        }
    }

    private static void drawAltitude(Graphics2D graphics, int altitude) {
        graphics.setColor(HUD);
        graphics.drawRect(202, 56, 36, 15);
        drawText(graphics, formatAltitude(altitude), 220, 61, 2, HUD, Align.CENTER);
        for (int offset = -2; offset <= 2; offset++) {
            if (offset == 0) {
                continue;
            }
            int value = altitude - offset * 16;
            int y = 63 + offset * 16;
            graphics.setColor(HUD_MEDIUM);
            graphics.fillRect(229, y, 8, 1);
            drawText(graphics, formatAltitude(value), 225, y - 2, 1, HUD_MEDIUM, Align.RIGHT);
        }
    }

    private static void drawHeight(Graphics2D graphics, int height) {
        graphics.setColor(height <= 16 ? new Color(255, 173, 47, 235) : HUD_MEDIUM);
        drawText(graphics, "AGL " + threeDigits(height), 226, 112, 1, graphics.getColor(), Align.RIGHT);
    }

    private static void drawPrograde(Graphics2D graphics, int bucket) {
        int column = bucket % 7;
        int row = bucket / 7;
        int x = 128 + (column - 3) * 12;
        int y = 64 + (row - 3) * 9;
        graphics.setColor(HUD);
        graphics.drawOval(x - 5, y - 5, 10, 10);
        graphics.fillRect(x - 10, y, 5, 1);
        graphics.fillRect(x + 6, y, 5, 1);
        graphics.fillRect(x, y - 9, 1, 4);
    }

    private static void drawLockMarker(Graphics2D graphics, int position, boolean locked) {
        int column = position % 7;
        int row = position / 7;
        int x = 128 + (column - 3) * 18;
        int y = 64 + (row - 3) * 12;
        Color color = locked ? HUD : new Color(255, 191, 48, 238);
        int radius = locked ? 12 : 9;
        int arm = locked ? 7 : 5;
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(locked ? 2.0f : 1.0f));
        graphics.drawLine(x - radius, y - radius, x - radius + arm, y - radius);
        graphics.drawLine(x - radius, y - radius, x - radius, y - radius + arm);
        graphics.drawLine(x + radius, y - radius, x + radius - arm, y - radius);
        graphics.drawLine(x + radius, y - radius, x + radius, y - radius + arm);
        graphics.drawLine(x - radius, y + radius, x - radius + arm, y + radius);
        graphics.drawLine(x - radius, y + radius, x - radius, y + radius - arm);
        graphics.drawLine(x + radius, y + radius, x + radius - arm, y + radius);
        graphics.drawLine(x + radius, y + radius, x + radius, y + radius - arm);
        if (locked) {
            graphics.drawOval(x - 3, y - 3, 6, 6);
        } else {
            graphics.fillRect(x, y, 2, 2);
        }
    }

    private static void drawLockProgress(Graphics2D graphics, int progress) {
        boolean locked = progress == 17;
        int segments = locked ? 16 : progress;
        Color color = locked ? HUD : new Color(255, 191, 48, 238);
        drawText(graphics, locked ? "LOCK" : progress == 0 ? "SCAN" : "ACQ",
                128, 84, 1, color, Align.CENTER);
        for (int segment = 0; segment < 16; segment++) {
            int x = 88 + segment * 5;
            graphics.setColor(segment < segments ? color : new Color(
                    color.getRed(), color.getGreen(), color.getBlue(), 68));
            if (segment < segments) {
                graphics.fillRect(x, 94, 4, 3);
            } else {
                graphics.drawRect(x, 94, 3, 2);
            }
        }
    }

    private static void corner(Graphics2D graphics, int x, int y, int horizontal, int vertical) {
        int horizontalX = horizontal > 0 ? x : x - 17;
        int verticalY = vertical > 0 ? y : y - 17;
        graphics.fillRect(horizontalX, y, 18, 2);
        graphics.fillRect(x, verticalY, 2, 18);
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

    private static void drawText(Graphics2D graphics, String text, int x, int y,
                                 int scale, Color color, Align align) {
        String upper = text.toUpperCase(Locale.ROOT);
        int width = Math.max(0, upper.length() * 4 - 1) * scale;
        int cursor = switch (align) {
            case LEFT -> x;
            case CENTER -> x - width / 2;
            case RIGHT -> x - width;
        };
        graphics.setColor(color);
        for (int index = 0; index < upper.length(); index++) {
            char character = upper.charAt(index);
            String pixels = PIXEL_FONT.get(character);
            if (pixels != null) {
                for (int row = 0; row < 5; row++) {
                    for (int column = 0; column < 3; column++) {
                        if (pixels.charAt(row * 3 + column) == '1') {
                            graphics.fillRect(cursor + column * scale, y + row * scale, scale, scale);
                        }
                    }
                }
            }
            cursor += 4 * scale;
        }
    }

    private static String headingLabel(int heading) {
        return switch (heading) {
            case 0 -> "N";
            case 90 -> "E";
            case 180 -> "S";
            case 270 -> "W";
            default -> Integer.toString(heading / 10);
        };
    }

    private static String threeDigits(int value) {
        return String.format(Locale.ROOT, "%03d", Math.max(0, Math.min(999, value)));
    }

    private static String formatAltitude(int value) {
        if (value < 0) {
            return "-" + String.format(Locale.ROOT, "%02d", Math.min(99, Math.abs(value)));
        }
        return threeDigits(value);
    }

    private static String signed(int value) {
        return (value >= 0 ? "+" : "-") + Math.abs(value);
    }

    private static BufferedImage buildIcon(BufferedImage atlas) {
        BufferedImage icon = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        configure(graphics);
        graphics.setColor(new Color(5, 15, 22, 255));
        graphics.fillRect(0, 0, 128, 128);
        graphics.drawImage(atlas,
                0, 32, 128, 96,
                0, 0, CELL_WIDTH, CELL_HEIGHT,
                null);
        graphics.dispose();
        return icon;
    }

    private static BufferedImage buildPreview(BufferedImage atlas) {
        BufferedImage preview = new BufferedImage(CELL_WIDTH * 2, CELL_HEIGHT * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();
        configure(graphics);
        graphics.setColor(new Color(7, 12, 18));
        graphics.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        drawCompositePreview(graphics, atlas, 0, 0, 3, 16, 30, 32, 24, 0, -1, 0, false);
        drawCompositePreview(graphics, atlas, CELL_WIDTH, 0, 1, 20, 45, 44, 14, 0, 17, 7, false);
        drawCompositePreview(graphics, atlas, 0, CELL_HEIGHT, 4, 28, 62, 58, 42, 0, 24, 17, true);
        drawCompositePreview(graphics, atlas, CELL_WIDTH, CELL_HEIGHT, 2, 36, 18, 20, 56, 16, -1, 0, false);
        graphics.dispose();
        return preview;
    }

    private static void drawCompositePreview(Graphics2D graphics, BufferedImage atlas, int x, int y,
                                             int weapon, int heading, int speed, int altitude,
                                             int height, int threat, int lockPosition,
                                             int lockProgress, boolean locked) {
        copyCell(graphics, atlas, BASE_INDEX, x, y);
        copyCell(graphics, atlas, HEADING_INDEX + heading, x, y);
        copyCell(graphics, atlas, PITCH_INDEX + 12, x, y);
        copyCell(graphics, atlas, PROGRADE_INDEX + 24, x, y);
        copyCell(graphics, atlas, SPEED_INDEX + speed, x, y);
        copyCell(graphics, atlas, ALTITUDE_INDEX + altitude, x, y);
        copyCell(graphics, atlas, HEIGHT_INDEX + height, x, y);
        copyCell(graphics, atlas, WEAPON_INDEX + weapon, x, y);
        if (lockPosition >= 0) {
            copyCell(graphics, atlas, LOCK_MARKER_INDEX + (locked ? 49 : 0) + lockPosition, x, y);
            copyCell(graphics, atlas, LOCK_PROGRESS_INDEX + lockProgress, x, y);
        }
        if (threat > 0) {
            copyCell(graphics, atlas, THREAT_INDEX + threat, x, y);
        }
    }

    private static void copyCell(Graphics2D graphics, BufferedImage atlas, int index, int x, int y) {
        int sourceX = (index % COLUMNS) * CELL_WIDTH;
        int sourceY = (index / COLUMNS) * CELL_HEIGHT;
        graphics.drawImage(atlas,
                x, y, x + CELL_WIDTH, y + CELL_HEIGHT,
                sourceX, sourceY, sourceX + CELL_WIDTH, sourceY + CELL_HEIGHT,
                null);
    }

    private static String fontJson(int ascent, int height, int layerAdvance) {
        StringBuilder rows = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            if (row > 0) {
                rows.append(',');
            }
            rows.append('"');
            for (int column = 0; column < COLUMNS; column++) {
                rows.append(String.format(Locale.ROOT, "\\u%04x", 0xE100 + row * COLUMNS + column));
            }
            rows.append('"');
        }
        return "{\"providers\":["
                + "{\"type\":\"space\",\"advances\":{\"\\ue0ff\":" + layerAdvance + "}},"
                + "{\"type\":\"bitmap\",\"file\":\"homingmissiles:font/homingmissiles_hmd.png\","
                + "\"ascent\":" + ascent + ",\"height\":" + height + ",\"chars\":[" + rows + "]}]}\n";
    }

    private static Map<String, byte[]> loadAudio() throws IOException {
        Path audioRoot = Path.of("src", "main", "hud", "audio").toAbsolutePath().normalize();
        Map<String, byte[]> audio = new TreeMap<>();
        for (String name : new String[]{"launch", "lock_confirm", "missile_critical", "missile_warning"}) {
            Path source = audioRoot.resolve(name + ".ogg");
            if (!Files.isRegularFile(source)) {
                throw new IOException("missing processed HUD audio asset: " + source);
            }
            audio.put("assets/homingmissiles/sounds/hud/" + name + ".ogg", Files.readAllBytes(source));
        }
        return audio;
    }

    private static String soundsJson() {
        return "{\n"
                + "  \"hud.launch\": {\"sounds\":[{\"name\":\"homingmissiles:hud/launch\",\"volume\":0.85}]},\n"
                + "  \"hud.lock_confirm\": {\"sounds\":[{\"name\":\"homingmissiles:hud/lock_confirm\"}]},\n"
                + "  \"hud.missile_warning\": {\"sounds\":[{\"name\":\"homingmissiles:hud/missile_warning\"}]},\n"
                + "  \"hud.missile_critical\": {\"sounds\":[{\"name\":\"homingmissiles:hud/missile_critical\"}]}\n"
                + "}\n";
    }

    private static String audioNotice() {
        return "HUD launch and lock-confirmation audio is synthesized for this project.\n"
                + "launch: deterministic string/ignition oscillators, filtered noise exhaust and "
                + "mechanical transient generated by tools/build-hud-audio.ps1\n"
                + "lock_confirm: deterministic four-note ascending harmonic cue generated by "
                + "tools/build-hud-audio.ps1\n"
                + "missile_warning: Warning! from 7 Space Sounds by Joth - "
                + "https://opengameart.org/content/7-space-sounds\n"
                + "missile_critical: Short alarm by yd - "
                + "https://opengameart.org/content/short-alarm\n"
                + "Warning-source license: CC0 1.0 - "
                + "https://creativecommons.org/publicdomain/zero/1.0/\n"
                + "No audio was extracted from Minecraft, Ace Combat 7 or another commercial game.\n"
                + "Build recipe and source hashes: tools/build-hud-audio.ps1 and "
                + "src/main/hud/third-party/README.md\n";
    }

    private static String packMetadataLegacy() {
        return "{\"pack\":{\"pack_format\":46,\"supported_formats\":[46,46],"
                + "\"description\":\"HomingMissiles 3.0 Layered Flight HMD - Minecraft 1.21.4\"}}\n";
    }

    private static String packMetadataModern() {
        return "{\"pack\":{\"min_format\":75,\"max_format\":75,"
                + "\"description\":\"HomingMissiles 3.0 Layered Flight HMD - Minecraft 1.21.11\"}}\n";
    }

    private static void buildPack(Path output, String name, String metadata,
                                  byte[] atlas, byte[] icon, byte[] font, byte[] titleFont,
                                  Map<String, byte[]> audio) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("assets/homingmissiles/font/hud.json", font);
        entries.put("assets/homingmissiles/font/hud_title.json", titleFont);
        entries.put("assets/homingmissiles/textures/font/homingmissiles_hmd.png", atlas);
        entries.put("assets/homingmissiles/sounds.json", soundsJson().getBytes(StandardCharsets.UTF_8));
        entries.put("assets/homingmissiles/sounds/NOTICE.txt", audioNotice().getBytes(StandardCharsets.UTF_8));
        entries.putAll(audio);
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

    @FunctionalInterface
    private interface Painter {
        void paint(Graphics2D graphics);
    }

    private enum Align { LEFT, CENTER, RIGHT }
}
