import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;

/** Applies an exact RGB lookup table while preserving source alpha and unmapped ARGB values. */
public final class StrictPngRecolor {
    private StrictPngRecolor() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: StrictPngRecolor <jar> <entry> <output> <requireOpaqueMapping> <lut>");
        }

        Path jarPath = Path.of(args[0]);
        String entryName = args[1];
        Path output = Path.of(args[2]);
        boolean requireOpaqueMapping = Boolean.parseBoolean(args[3]);
        Map<Integer, Integer> lut = parseLut(args[4]);

        BufferedImage source = readJarImage(jarPath, entryName);
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int before = source.getRGB(x, y);
                Integer replacement = lut.get(before & 0xFFFFFF);
                if (replacement == null) {
                    if (requireOpaqueMapping && (before >>> 24) != 0) {
                        throw new IllegalStateException(
                                "Unmapped opaque source color " + rgb(before) + " at (" + x + "," + y + ")");
                    }
                    result.setRGB(x, y, before);
                } else {
                    result.setRGB(x, y, (before & 0xFF000000) | replacement);
                }
            }
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(result, "png", output.toFile())) {
            throw new IOException("No PNG writer is available");
        }
        verify(source, ImageIO.read(output.toFile()), lut, requireOpaqueMapping, output);
    }

    private static BufferedImage readJarImage(Path jarPath, String entryName) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(entryName);
            if (entry == null) {
                throw new IOException("Missing " + entryName + " in " + jarPath);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    throw new IOException("Unsupported PNG " + entryName + " in " + jarPath);
                }
                return image;
            }
        }
    }

    private static Map<Integer, Integer> parseLut(String encoded) {
        Map<Integer, Integer> lut = new HashMap<>();
        if (encoded.isBlank()) {
            return lut;
        }
        for (String mapping : encoded.split(";")) {
            String[] sides = mapping.split("=", -1);
            if (sides.length != 2) {
                throw new IllegalArgumentException("Invalid LUT entry: " + mapping);
            }
            lut.put(parseRgb(sides[0]), parseRgb(sides[1]));
        }
        return lut;
    }

    private static int parseRgb(String encoded) {
        String[] channels = encoded.split(",", -1);
        if (channels.length != 3) {
            throw new IllegalArgumentException("Invalid RGB value: " + encoded);
        }
        int red = channel(channels[0]);
        int green = channel(channels[1]);
        int blue = channel(channels[2]);
        return (red << 16) | (green << 8) | blue;
    }

    private static int channel(String encoded) {
        int value = Integer.parseInt(encoded);
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("RGB channel out of range: " + encoded);
        }
        return value;
    }

    private static void verify(
            BufferedImage source,
            BufferedImage actual,
            Map<Integer, Integer> lut,
            boolean requireOpaqueMapping,
            Path output) {
        if (actual == null
                || actual.getWidth() != source.getWidth()
                || actual.getHeight() != source.getHeight()) {
            throw new IllegalStateException("Recolor changed dimensions for " + output);
        }
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int before = source.getRGB(x, y);
                Integer replacement = lut.get(before & 0xFFFFFF);
                if (replacement == null && requireOpaqueMapping && (before >>> 24) != 0) {
                    throw new IllegalStateException("Unmapped opaque source pixel during verification");
                }
                int expected = replacement == null
                        ? before
                        : (before & 0xFF000000) | replacement;
                if (actual.getRGB(x, y) != expected) {
                    throw new IllegalStateException(
                            "Recolor changed pixel (" + x + "," + y + ") for " + output);
                }
            }
        }
    }

    private static String rgb(int argb) {
        return ((argb >>> 16) & 0xFF) + "," + ((argb >>> 8) & 0xFF) + "," + (argb & 0xFF);
    }
}
